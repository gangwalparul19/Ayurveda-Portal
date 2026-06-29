package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.ManualOrderRequest;
import com.ayurveda.platform.dto.request.WhatsAppOrderRequest;
import com.ayurveda.platform.dto.response.DuplicateCheckResult;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.exception.InsufficientStockException;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import com.ayurveda.platform.util.WhatsAppTextParser;import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for order lifecycle management within a tenant's database.
 * Handles order creation, status transitions, and history tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SalespersonRepository salespersonRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final ProductManagementService productManagementService;
    private final PaymentRecordRepository paymentRecordRepository;
    private final WhatsAppTextParser whatsAppParser;
    private final CustomerService customerService;
    private final com.ayurveda.platform.master.service.ConfigurationService configurationService;
    private final com.ayurveda.platform.master.service.AuditLogService auditLogService;
    private final CouponUsageRepository couponUsageRepository;

    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    public Page<Order> getOrdersByStatus(Order.OrderStatus status, Pageable pageable) {
        return orderRepository.findAllByStatus(status, pageable);
    }

    public Page<Order> getOrdersByDateRange(LocalDate start, LocalDate end, Pageable pageable) {
        return orderRepository.findAllByOrderDateBetween(start, end, pageable);
    }

    public Page<Order> getOrdersBySalesperson(Long salespersonId, Pageable pageable) {
        return orderRepository.findAllBySalespersonId(salespersonId, pageable);
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }

    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
    }

    /**
     * Fetch a single order and map it to an {@link OrderResponse} DTO.
     * Runs inside a read-only transaction so lazy associations (customer, items,
     * status history) are initialized before mapping, avoiding LazyInitialization
     * issues and preventing raw Hibernate proxies from leaking to the serializer.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderResponseById(Long id) {
        return mapOrderToResponse(getOrderById(id));
    }

    /**
     * Fetch a page of orders (with the same optional filtering as {@link #getAllOrders})
     * and map each entity to an {@link OrderResponse} DTO within a read-only transaction.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersAsResponse(Order.OrderStatus status,
                                                   LocalDate from,
                                                   LocalDate to,
                                                   Long salespersonId,
                                                   Pageable pageable) {
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findAllByStatus(status, pageable);
        } else if (from != null && to != null) {
            orders = orderRepository.findAllByOrderDateBetween(from, to, pageable);
        } else if (salespersonId != null) {
            orders = orderRepository.findAllBySalespersonId(salespersonId, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return orders.map(this::mapOrderToResponse);
    }

    /**
     * Resolve the order's customer and salesperson and reuse the shared
     * {@link #mapToOrderResponse} mapper used by the create/update endpoints.
     */
    private OrderResponse mapOrderToResponse(Order order) {
        Salesperson salesperson = order.getSalespersonId() != null
                ? salespersonRepository.findById(order.getSalespersonId()).orElse(null)
                : null;
        return mapToOrderResponse(order, order.getCustomer(), salesperson);
    }

    /**
     * Create a new order with line items.
     * Generates an order number, snapshots product data, and calculates totals.
     */
    @Transactional
    public Order createOrder(Order order, List<OrderItem> items) {
        // Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();
        order.setOrderNumber(orderNumber);

        if (order.getOrderDate() == null) {
            order.setOrderDate(LocalDate.now());
        }

        // Snapshot product data and calculate line totals
        for (OrderItem item : items) {
            if (item.getProduct() != null) {
                Product product = productRepository.findById(item.getProduct().getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Product", "id", item.getProduct().getId()));
                item.setProductNameSnapshot(product.getName());
                item.setSkuSnapshot(product.getSku());
                if (item.getUnitPrice() == null) {
                    item.setUnitPrice(product.getSalePrice());
                }
                if (item.getMrpSnapshot() == null) {
                    item.setMrpSnapshot(product.getMrp());
                }
            }
            item.calculateLineTotal();
            order.addItem(item);
        }

        // Calculate order totals
        order.recalculateTotals();

        Order saved = orderRepository.save(order);

        // Record initial status in history
        addStatusHistory(saved, null, Order.OrderStatus.NEW.name(),
                order.getSalespersonId(), "Order created");

        // Audit: order created (Req 32.4)
        auditOrderCreated(saved, order.getSalespersonId());

        log.info("Created order: {} with {} items, total: {}",
                saved.getOrderNumber(), items.size(), saved.getTotalAmount());

        return saved;
    }

    /**
     * Transition an order to a new status with validation, audit trail, and stock management.
     * Implements Requirements 5.9, 5.10, 5.11, 6.1, 6.2, 6.3, 9.2, 9.3
     * 
     * This method:
     * - Validates status transition using state machine rules (Req 5.9)
     * - Creates OrderStatusHistory record for audit trail (Req 6.1, 6.2, 6.3)
     * - Updates order status (Req 5.9)
     * - Sets dispatchedAt/deliveredAt timestamps (Req 5.10, 5.11)
     * - Handles stock updates for PACKED/CANCELLED/RETURNED statuses (Req 9.2, 9.3)
     * 
     * Stock Management:
     * - PACKED: Reduces stock quantity for all order items (Req 9.2)
     * - CANCELLED: Restores stock if transitioning from PACKED or later (Req 9.3)
     * - RETURNED: Restores stock quantities (Req 9.3)
     * 
     * @param orderId ID of the order to update
     * @param newStatus Target status to transition to
     * @param changedBy User ID performing the status change
     * @param notes Optional notes about the status change
     * @return Updated Order entity
     * @throws ResourceNotFoundException if order not found
     * @throws InvalidStatusTransitionException if transition is not allowed
     * @throws InsufficientStockException if stock validation fails for PACKED status
     */
    @Transactional
    public Order updateOrderStatus(Long orderId, Order.OrderStatus newStatus,
                                    Long changedBy, String notes) {
        Order order = getOrderById(orderId);
        Order.OrderStatus oldStatus = order.getStatus();

        // Validate transition using Order entity's comprehensive validation
        // This validates state machine rules and business rules (Req 5.1-5.9)
        order.isValidStatusTransition(newStatus);

        // Handle stock updates based on status transition (Req 9.2, 9.3)
        handleStockUpdates(order, oldStatus, newStatus, changedBy, notes);

        // Update order status
        order.setStatus(newStatus);

        // Set timestamps for specific transitions (Req 5.10, 5.11)
        if (newStatus == Order.OrderStatus.DISPATCHED) {
            order.setDispatchedAt(LocalDateTime.now());
        } else if (newStatus == Order.OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
        }

        // Save order changes
        order = orderRepository.save(order);

        // Record status change in history (Req 6.1, 6.2)
        addStatusHistory(order, oldStatus.name(), newStatus.name(), changedBy, notes);

        // Audit: order status change, incl. cancellation/return (Req 32.4)
        auditOrderStatusChange(order, oldStatus, newStatus, changedBy, notes);

        log.info("Order {} status changed: {} -> {}", order.getOrderNumber(), oldStatus, newStatus);

        return order;
    }

    /**
     * Handle stock updates for status transitions.
     * Implements Requirements 9.2, 9.3
     * 
     * - Requirement 9.2: WHEN order status transitions to PACKED THEN reduce stock for all items
     * - Requirement 9.3: WHEN order status transitions to CANCELLED or RETURNED from PACKED or later
     *                    THEN restore stock quantities
     * 
     * @param order The order being transitioned
     * @param oldStatus Previous status
     * @param newStatus Target status
     * @param userId User performing the operation
     * @param notes Notes about the status change
     */
    private void handleStockUpdates(Order order, Order.OrderStatus oldStatus, 
                                    Order.OrderStatus newStatus, Long userId, String notes) {
        
        // Requirement 9.2: Reduce stock when transitioning to PACKED
        if (newStatus == Order.OrderStatus.PACKED) {
            reduceStockForOrder(order, userId, notes);
        }
        
        // Requirement 9.3: Restore stock when cancelling or returning from PACKED or later
        if (newStatus == Order.OrderStatus.CANCELLED || newStatus == Order.OrderStatus.RETURNED) {
            // Only restore stock if order was previously packed (stock was already reduced)
            if (isStockAlreadyReduced(oldStatus)) {
                restoreStockForOrder(order, userId, notes, newStatus);
            }
        }
    }

    /**
     * Check if stock was already reduced for the given status.
     * Stock is reduced when order reaches PACKED status.
     * 
     * @param status Order status to check
     * @return true if stock was already reduced, false otherwise
     */
    private boolean isStockAlreadyReduced(Order.OrderStatus status) {
        return status == Order.OrderStatus.PACKED || 
               status == Order.OrderStatus.DISPATCHED || 
               status == Order.OrderStatus.DELIVERED;
    }

    /**
     * Reduce stock quantities for all order items.
     * Implements Requirement 9.2
     * 
     * @param order Order whose items need stock reduction
     * @param userId User performing the operation
     * @param notes Notes about the operation
     * @throws InsufficientStockException if any product has insufficient stock
     */
    private void reduceStockForOrder(Order order, Long userId, String notes) {
        log.debug("Reducing stock for order: {}", order.getOrderNumber());
        
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Long productId = item.getProduct().getId();
                Integer quantity = item.getQuantity();
                
                // Reduce stock (negative quantity for STOCK_OUT operation)
                productManagementService.updateStock(
                    productId, 
                    -quantity,  // Negative for reduction
                    StockHistory.StockOperation.STOCK_OUT,
                    "ORDER",
                    order.getId(),
                    String.format("Order %s packed - %s", order.getOrderNumber(), 
                                  notes != null ? notes : "Stock reduced for packing"),
                    userId
                );
                
                log.debug("Reduced stock for product ID {} by {} units for order {}", 
                         productId, quantity, order.getOrderNumber());
            }
        }
        
        log.info("Stock reduced for order {} - {} items processed", 
                order.getOrderNumber(), order.getItems().size());
    }

    /**
     * Restore stock quantities for all order items.
     * Implements Requirement 9.3
     * 
     * @param order Order whose items need stock restoration
     * @param userId User performing the operation
     * @param notes Notes about the operation
     * @param newStatus The status being transitioned to (CANCELLED or RETURNED)
     */
    private void restoreStockForOrder(Order order, Long userId, String notes, 
                                     Order.OrderStatus newStatus) {
        log.debug("Restoring stock for order: {} (status: {})", order.getOrderNumber(), newStatus);
        
        StockHistory.StockOperation operation = newStatus == Order.OrderStatus.RETURNED 
                ? StockHistory.StockOperation.RETURN 
                : StockHistory.StockOperation.RETURN;  // Use RETURN for both CANCELLED and RETURNED
        
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                Long productId = item.getProduct().getId();
                Integer quantity = item.getQuantity();
                
                // Restore stock (positive quantity for RETURN operation)
                productManagementService.updateStock(
                    productId, 
                    quantity,  // Positive for restoration
                    operation,
                    "ORDER",
                    order.getId(),
                    String.format("Order %s %s - %s", order.getOrderNumber(), 
                                  newStatus.name().toLowerCase(),
                                  notes != null ? notes : "Stock restored"),
                    userId
                );
                
                log.debug("Restored stock for product ID {} by {} units for order {}", 
                         productId, quantity, order.getOrderNumber());
            }
        }
        
        log.info("Stock restored for order {} - {} items processed", 
                order.getOrderNumber(), order.getItems().size());
    }

    /**
     * Validate that a status transition is allowed.
     * @deprecated Use Order.isValidStatusTransition() instead for comprehensive validation
     */
    @Deprecated
    private void validateStatusTransition(Order.OrderStatus from, Order.OrderStatus to) {
        // Cannot transition from terminal states
        if (from == Order.OrderStatus.DELIVERED || from == Order.OrderStatus.CANCELLED) {
            if (to != Order.OrderStatus.RETURNED) {
                throw new IllegalArgumentException(
                        "Cannot transition from " + from + " to " + to);
            }
        }
        // Cannot go backwards in the pipeline (except cancellation/return)
        if (to != Order.OrderStatus.CANCELLED && to != Order.OrderStatus.RETURNED) {
            if (to.ordinal() <= from.ordinal()) {
                throw new IllegalArgumentException(
                        "Cannot transition backwards from " + from + " to " + to);
            }
        }
    }

    private void addStatusHistory(Order order, String fromStatus, String toStatus,
                                   Long changedBy, String notes) {
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .changedBy(changedBy)
                .notes(notes)
                .build();
        order.getStatusHistory().add(history);
    }

    // --- Audit logging (Requirements 32.3, 32.4) ---

    /**
     * Record an ORDER_CREATED audit entry. Best-effort: never breaks the
     * order creation transaction if audit persistence fails.
     */
    private void auditOrderCreated(Order order, Long userId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderId", order.getId());
        details.put("orderNumber", order.getOrderNumber());
        details.put("orderSource", order.getOrderSource() != null ? order.getOrderSource().name() : null);
        details.put("totalAmount", order.getTotalAmount());
        details.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);
        safeAudit(userId, com.ayurveda.platform.master.service.AuditLogService.ORDER_CREATED, details);
    }

    /**
     * Record an audit entry for an order status change. Uses a specific action
     * for cancellations and returns, otherwise a generic status-change action.
     */
    private void auditOrderStatusChange(Order order, Order.OrderStatus oldStatus,
                                        Order.OrderStatus newStatus, Long userId, String notes) {
        String action;
        if (newStatus == Order.OrderStatus.CANCELLED) {
            action = com.ayurveda.platform.master.service.AuditLogService.ORDER_CANCELLED;
        } else if (newStatus == Order.OrderStatus.RETURNED) {
            action = com.ayurveda.platform.master.service.AuditLogService.ORDER_RETURNED;
        } else {
            action = com.ayurveda.platform.master.service.AuditLogService.ORDER_STATUS_CHANGED;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderId", order.getId());
        details.put("orderNumber", order.getOrderNumber());
        details.put("fromStatus", oldStatus != null ? oldStatus.name() : null);
        details.put("toStatus", newStatus != null ? newStatus.name() : null);
        details.put("notes", notes);
        safeAudit(userId, action, details);
    }

    /**
     * Record a PAYMENT_RECORDED audit entry.
     */
    private void auditPaymentRecorded(Order order, BigDecimal amount, Order.PaymentMode paymentMode,
                                      String transactionReference, Order.PaymentStatus newPaymentStatus,
                                      Long userId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderId", order.getId());
        details.put("orderNumber", order.getOrderNumber());
        details.put("amount", amount);
        details.put("paymentMode", paymentMode != null ? paymentMode.name() : null);
        details.put("transactionReference", transactionReference);
        details.put("paymentStatus", newPaymentStatus != null ? newPaymentStatus.name() : null);
        safeAudit(userId, com.ayurveda.platform.master.service.AuditLogService.PAYMENT_RECORDED, details);
    }

    /**
     * Persist an audit entry without allowing audit failures to propagate into
     * the business operation. The audit write itself runs in its own
     * transaction (see {@code AuditLogService}).
     */
    private void safeAudit(Long userId, String action, Map<String, Object> details) {
        try {
            auditLogService.record(userId, action, details);
        } catch (Exception e) {
            log.warn("Failed to write audit log (action={}, userId={}): {}", action, userId, e.getMessage());
        }
    }

    // --- Reporting helpers ---

    public long countOrdersByDate(LocalDate date) {
        return orderRepository.countByDate(date);
    }

    public BigDecimal sumOrderTotalByDate(LocalDate date) {
        return orderRepository.sumTotalByDate(date);
    }

    public BigDecimal sumOrderTotalByDateRange(LocalDate start, LocalDate end) {
        return orderRepository.sumTotalByDateRange(start, end);
    }

    /**
     * Create an order from storefront (public customer order).
     * No salesperson, source = STOREFRONT.
     */
    @Transactional
    public Order createStorefrontOrder(Customer customer, 
            com.ayurveda.platform.dto.request.StorefrontOrderRequest request) {
        
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderSource(Order.OrderSource.STOREFRONT);
        order.setStatus(Order.OrderStatus.NEW);
        order.setOrderDate(LocalDate.now());
        
        // Store delivery address in notes since Order doesn't have address fields
        String deliveryInfo = String.format("Delivery Address: %s, %s, %s, %s",
                request.getDeliveryAddress(), request.getCity(), 
                request.getState(), request.getPincode());
        order.setNotes(request.getNotes() != null ? 
                deliveryInfo + "\n\nCustomer Notes: " + request.getNotes() : deliveryInfo);
        
        // Set payment mode
        Order.PaymentMode paymentMode = Order.PaymentMode.COD; // Default
        if (request.getPaymentMethod() != null) {
            try {
                paymentMode = Order.PaymentMode.valueOf(request.getPaymentMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid payment method: {}, defaulting to COD", request.getPaymentMethod());
            }
        }
        order.setPaymentMode(paymentMode);

        // Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();
        order.setOrderNumber(orderNumber);

        // Create order items
        List<OrderItem> items = request.getItems().stream()
                .map(itemReq -> {
                    OrderItem item = new OrderItem();
                    
                    // Find product
                    Product product = productRepository.findById(itemReq.getProductId())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Product", "id", itemReq.getProductId()));
                    
                    item.setProduct(product);
                    item.setProductNameSnapshot(product.getName());
                    item.setSkuSnapshot(product.getSku());
                    item.setQuantity(itemReq.getQuantity());
                    item.setUnitPrice(product.getSalePrice());
                    item.setMrpSnapshot(product.getMrp());
                    item.calculateLineTotal();
                    
                    order.addItem(item);
                    
                    return item;
                })
                .toList();

        // Calculate totals
        order.recalculateTotals();

        // Apply coupon discount if provided
        if (request.getCouponDiscount() != null && request.getCouponDiscount().compareTo(BigDecimal.ZERO) > 0) {
            order.setCouponDiscount(request.getCouponDiscount());
            order.setTotalAmount(order.getTotalAmount().subtract(request.getCouponDiscount()));
        }

        // Save order
        Order saved = orderRepository.save(order);

        // Record status history
        addStatusHistory(saved, null, Order.OrderStatus.NEW.name(),
                null, "Order placed via storefront");

        // Audit: order created via storefront (Req 32.4)
        auditOrderCreated(saved, null);

        // Save coupon usage record if a coupon was applied
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()
                && request.getCouponDiscount() != null
                && request.getCouponDiscount().compareTo(BigDecimal.ZERO) > 0) {
            try {
                CouponUsage usage = CouponUsage.builder()
                        .couponId(saved.getCouponId())
                        .orderId(saved.getId())
                        .customerPhone(customer.getPhone())
                        .build();
                couponUsageRepository.save(usage);
            } catch (Exception e) {
                log.warn("Failed to save coupon usage record for order {}: {}", saved.getOrderNumber(), e.getMessage());
            }
        }

        log.info("Storefront order created: {} for customer: {}, total: {}",
                saved.getOrderNumber(), customer.getName(), saved.getTotalAmount());

        return saved;
    }

    /**
     * Record a payment against an order with validation and automatic status updates.
     * Implements Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7
     * 
     * This method:
     * - Validates payment amount doesn't exceed remaining balance (Req 7.2)
     * - Creates PaymentRecord entity with transaction details (Req 7.1)
     * - Calculates total paid amount (Req 7.3)
     * - Updates order paymentStatus (PENDING/PARTIAL/PAID) (Req 7.4, 7.5, 7.6)
     * - Rejects payments that would exceed order total (Req 7.7)
     * 
     * Payment Status Logic:
     * - PENDING: total paid = 0 (Req 7.6)
     * - PARTIAL: 0 < total paid < order total (Req 7.5)
     * - PAID: total paid = order total (Req 7.4)
     * 
     * @param orderId ID of the order to record payment against
     * @param amount Payment amount (must be positive)
     * @param paymentMode Payment method used (COD, UPI, BANK_TRANSFER, ONLINE, CREDIT)
     * @param transactionReference Transaction ID or reference (optional)
     * @param notes Optional notes about the payment
     * @param recordedBy User ID recording the payment
     * @return Updated Order entity with new payment record and updated payment status
     * @throws ResourceNotFoundException if order not found
     * @throws IllegalArgumentException if payment would exceed order total or amount is invalid
     */
    @Transactional
    public Order recordPayment(Long orderId, BigDecimal amount, Order.PaymentMode paymentMode,
                               String transactionReference, String notes, Long recordedBy) {
        // Validate payment amount is positive
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }

        // Fetch order
        Order order = getOrderById(orderId);

        // Calculate current total paid amount (Requirement 7.3)
        BigDecimal currentTotalPaid = paymentRecordRepository.calculateTotalPaidForOrder(orderId);

        // Calculate remaining balance
        BigDecimal remainingBalance = order.getTotalAmount().subtract(currentTotalPaid);

        // Validate payment doesn't exceed remaining balance (Requirement 7.2, 7.7)
        if (amount.compareTo(remainingBalance) > 0) {
            throw new IllegalArgumentException(
                    String.format("Payment amount %.2f exceeds remaining balance %.2f. " +
                                  "Order total: %.2f, Already paid: %.2f",
                            amount, remainingBalance, order.getTotalAmount(), currentTotalPaid)
            );
        }

        // Create PaymentRecord entity (Requirement 7.1)
        PaymentRecord paymentRecord = PaymentRecord.builder()
                .order(order)
                .amount(amount)
                .paymentMode(paymentMode)
                .transactionReference(transactionReference)
                .paymentDate(LocalDateTime.now())
                .notes(notes)
                .recordedBy(recordedBy)
                .build();

        // Save payment record
        paymentRecord = paymentRecordRepository.save(paymentRecord);
        order.getPaymentRecords().add(paymentRecord);

        // Calculate new total paid amount (Requirement 7.3)
        BigDecimal newTotalPaid = currentTotalPaid.add(amount);

        // Update order payment status based on total paid (Requirements 7.4, 7.5, 7.6)
        Order.PaymentStatus newPaymentStatus;
        if (newTotalPaid.compareTo(order.getTotalAmount()) == 0) {
            // Full payment received (Requirement 7.4)
            newPaymentStatus = Order.PaymentStatus.PAID;
        } else if (newTotalPaid.compareTo(BigDecimal.ZERO) > 0) {
            // Partial payment received (Requirement 7.5)
            newPaymentStatus = Order.PaymentStatus.PARTIAL;
        } else {
            // No payment received (Requirement 7.6)
            newPaymentStatus = Order.PaymentStatus.PENDING;
        }

        order.setPaymentStatus(newPaymentStatus);

        // Save order with updated payment status
        order = orderRepository.save(order);

        // Audit: payment recorded (Req 32.3)
        auditPaymentRecorded(order, amount, paymentMode, transactionReference, newPaymentStatus, recordedBy);

        log.info("Payment recorded for order {}: amount={}, mode={}, totalPaid={}/{}, status={}",
                order.getOrderNumber(), amount, paymentMode, newTotalPaid, 
                order.getTotalAmount(), newPaymentStatus);

        return order;
    }

    /**
     * Create a manual order with full validation.
     * Implements Requirements 1.1, 1.2, 1.3, 4.1
     * 
     * Validates:
     * - Customer exists (Req 1.2)
     * - Product stock availability
     * - At least one order item present (Req 1.1)
     * 
     * Creates:
     * - Order with NEW status
     * - OrderItems with product snapshots (Req 1.3)
     * - Order totals calculation (Req 4.1)
     * - Initial OrderStatusHistory record
     * 
     * @param request Manual order request with customer, items, and payment info
     * @param userId User ID creating the order
     * @return OrderResponse DTO with complete order information
     * @throws ResourceNotFoundException if customer or product not found
     * @throws InsufficientStockException if product stock insufficient
     */
    @Transactional
    public OrderResponse createManualOrder(ManualOrderRequest request, Long userId) {
        // Validate customer exists (Requirement 1.2)
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer", "id", request.getCustomerId()));

        // Validate salesperson if provided
        Salesperson salesperson = null;
        if (request.getSalespersonId() != null) {
            salesperson = salespersonRepository.findById(request.getSalespersonId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Salesperson", "id", request.getSalespersonId()));
        }

        // Validate at least one item (Requirement 1.1)
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one order item is required");
        }

        // Create order entity
        Order order = Order.builder()
                .customer(customer)
                .salespersonId(salesperson != null ? salesperson.getId() : null)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .orderDate(request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now())
                .paymentMode(request.getPaymentMode())
                .paymentStatus(request.getPaymentStatus() != null ? 
                        request.getPaymentStatus() : Order.PaymentStatus.PENDING)
                .discountAmount(request.getDiscountAmount() != null ? 
                        request.getDiscountAmount() : BigDecimal.ZERO)
                .taxAmount(request.getTaxAmount() != null ? 
                        request.getTaxAmount() : BigDecimal.ZERO)
                .shippingCharge(request.getShippingCharge() != null ? 
                        request.getShippingCharge() : BigDecimal.ZERO)
                .notes(request.getNotes())
                .items(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        // Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();
        order.setOrderNumber(orderNumber);

        // Create order items with product snapshots and validate stock
        List<OrderItem> orderItems = new ArrayList<>();
        for (ManualOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            // Fetch product
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product", "id", itemReq.getProductId()));

            // Validate stock availability
            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new InsufficientStockException(
                        product.getName(),
                        product.getStockQuantity(),
                        itemReq.getQuantity());
            }

            // Create order item with product snapshot (Requirement 1.3)
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .skuSnapshot(product.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice() != null ? 
                            itemReq.getUnitPrice() : product.getSalePrice())
                    .mrpSnapshot(product.getMrp())
                    .discount(itemReq.getDiscount() != null ? 
                            itemReq.getDiscount() : BigDecimal.ZERO)
                    .taxAmount(itemReq.getTaxAmount() != null ? 
                            itemReq.getTaxAmount() : BigDecimal.ZERO)
                    .build();

            // Calculate line total (Requirement 4.1)
            orderItem.calculateLineTotal();
            
            // Add item to order
            order.addItem(orderItem);
            orderItems.add(orderItem);
        }

        // Calculate order totals (Requirement 4.1)
        order.calculateOrderTotals();

        // Save order (cascades to items)
        Order savedOrder = orderRepository.save(order);

        // Create initial OrderStatusHistory record
        addStatusHistory(savedOrder, null, Order.OrderStatus.NEW.name(),
                userId, "Manual order created");

        // Audit: manual order created (Req 32.4)
        auditOrderCreated(savedOrder, userId);

        log.info("Manual order created: {} by user: {}, customer: {}, items: {}, total: {}",
                savedOrder.getOrderNumber(), userId, customer.getName(), 
                orderItems.size(), savedOrder.getTotalAmount());

        // Return OrderResponseDTO
        return mapToOrderResponse(savedOrder, customer, salesperson);
    }

    /**
     * Map Order entity to OrderResponse DTO
     */
    private OrderResponse mapToOrderResponse(Order order, Customer customer, Salesperson salesperson) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customer(customer != null ? OrderResponse.CustomerSummary.builder()
                        .id(customer.getId())
                        .name(customer.getName())
                        .phone(customer.getPhone())
                        .email(customer.getEmail())
                        .city(customer.getCity())
                        .state(customer.getState())
                        .build() : null)
                .salesperson(salesperson != null ? OrderResponse.SalespersonSummary.builder()
                        .id(salesperson.getId())
                        .name(salesperson.getName())
                        .employeeCode(salesperson.getEmployeeCode())
                        .build() : null)
                .orderSource(order.getOrderSource())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMode(order.getPaymentMode())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount())
                .shippingCharge(order.getShippingCharge())
                .totalAmount(order.getTotalAmount())
                .items(order.getItems().stream()
                        .map(item -> OrderResponse.OrderItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                                .productName(item.getProductNameSnapshot())
                                .sku(item.getSkuSnapshot())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .mrp(item.getMrpSnapshot())
                                .discount(item.getDiscount())
                                .taxAmount(item.getTaxAmount())
                                .lineTotal(item.getLineTotal())
                                .build())
                        .collect(Collectors.toList()))
                .statusHistory(order.getStatusHistory().stream()
                        .map(history -> OrderResponse.OrderStatusHistoryResponse.builder()
                                .id(history.getId())
                                .fromStatus(history.getFromStatus())
                                .toStatus(history.getToStatus())
                                .changedBy(history.getChangedBy())
                                .notes(history.getNotes())
                                .changedAt(history.getChangedAt())
                                .build())
                        .collect(Collectors.toList()))
                .orderDate(order.getOrderDate())
                .dispatchedAt(order.getDispatchedAt())
                .deliveredAt(order.getDeliveredAt())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * Create a WhatsApp order with automatic text parsing.
     * Implements Requirements 1.2, 1.3, 3.6
     * 
     * This method:
     * - Parses WhatsApp text using WhatsAppTextParser (Req 3.6)
     * - Applies manual overrides if provided
     * - Finds or creates customer by phone (Req 1.2, 10.4)
     * - Creates order with WHATSAPP source (Req 1.3)
     * - Stores raw WhatsApp text in order
     * - Returns order with confidence score and warnings
     * 
     * @param request WhatsApp order request with text and optional overrides
     * @param userId User ID creating the order
     * @return OrderResponse DTO with parsed data, confidence score, and warnings
     * @throws ResourceNotFoundException if salesperson or product not found
     * @throws InsufficientStockException if product stock insufficient
     */
    @Transactional
    public OrderResponse createWhatsAppOrder(WhatsAppOrderRequest request, Long userId) {
        log.info("Creating WhatsApp order from message text (length: {})", request.getWhatsappText().length());
        
        // Step 1: Parse WhatsApp message using parser service
        WhatsAppTextParser.ParsedWhatsAppOrder parsed = whatsAppParser.parseWhatsAppMessage(request.getWhatsappText());
        
        log.info("WhatsApp parsing complete: confidence={}, warnings={}, items={}", 
                parsed.getConfidenceScore(), parsed.getWarnings().size(), parsed.getItems().size());
        
        // Step 2: Apply manual overrides if provided
        WhatsAppTextParser.ParsedCustomer customerInfo = parsed.getCustomer();
        List<WhatsAppTextParser.ParsedItem> parsedItems = parsed.getItems();
        WhatsAppTextParser.ParsedPayment paymentInfo = parsed.getPayment();
        
        // Override customer info if provided
        if (request.getCustomerOverride() != null) {
            WhatsAppOrderRequest.CustomerOverride override = request.getCustomerOverride();
            // Apply all overrides to customer info (ParsedCustomer doesn't have email field)
            if (override.getName() != null) customerInfo.setName(override.getName());
            if (override.getPhone() != null) customerInfo.setPhone(override.getPhone());
            if (override.getAddress() != null) customerInfo.setAddress(override.getAddress());
            if (override.getPincode() != null) customerInfo.setPincode(override.getPincode());
            
            log.info("Applied customer override: name={}, phone={}", override.getName(), override.getPhone());
        }
        
        // Override items if provided (replace parsed items completely)
        if (request.getItemsOverride() != null && !request.getItemsOverride().isEmpty()) {
            parsedItems = request.getItemsOverride().stream()
                    .map(override -> WhatsAppTextParser.ParsedItem.builder()
                            .matchedProductId(override.getProductId())
                            .quantity(override.getQuantity())
                            .confidence(1.0) // Manual override has perfect confidence
                            .build())
                    .collect(Collectors.toList());
            
            log.info("Applied items override: {} items", parsedItems.size());
        }
        
        // Override payment info if provided
        if (request.getPaymentOverride() != null) {
            WhatsAppOrderRequest.PaymentOverride override = request.getPaymentOverride();
            if (override.getPaymentMode() != null) {
                paymentInfo.setPaymentMode(override.getPaymentMode());
            }
            if (override.getAmount() != null) {
                paymentInfo.setAmount(override.getAmount().toString());
            }
            
            log.info("Applied payment override: mode={}", override.getPaymentMode());
        }
        
        // Step 3: Validate salesperson if provided
        Salesperson salesperson = null;
        if (request.getSalespersonId() != null) {
            salesperson = salespersonRepository.findById(request.getSalespersonId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Salesperson", "id", request.getSalespersonId()));
        }
        
        // Step 4: Find or create customer
        Customer customer = customerService.findOrCreateCustomer(
                customerInfo.getName() != null && !customerInfo.getName().isBlank() ? customerInfo.getName() : "Unknown",
                customerInfo.getPhone(),
                null, // email - not in parsed customer
                customerInfo.getAddress(),
                null, // city - would need to be parsed from address
                null, // state - would need to be parsed from address
                customerInfo.getPincode()
        );
        
        log.info("Customer resolved: id={}, name={}, phone={}", 
                customer.getId(), customer.getName(), customer.getPhone());
        
        // Step 5: Validate at least one item
        if (parsedItems.isEmpty()) {
            throw new IllegalArgumentException("At least one order item is required. No products could be extracted from WhatsApp message.");
        }
        
        // Step 6: Determine payment mode
        Order.PaymentMode paymentMode = Order.PaymentMode.COD; // Default
        if (paymentInfo != null && paymentInfo.getPaymentMode() != null) {
            try {
                paymentMode = Order.PaymentMode.valueOf(paymentInfo.getPaymentMode().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid payment mode: {}, defaulting to COD", paymentInfo.getPaymentMode());
            }
        }
        
        // Step 7: Create order entity
        Order order = Order.builder()
                .customer(customer)
                .salespersonId(salesperson != null ? salesperson.getId() : null)
                .orderSource(Order.OrderSource.WHATSAPP)
                .status(Order.OrderStatus.NEW)
                .orderDate(LocalDate.now())
                .paymentMode(paymentMode)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .rawWhatsappText(request.getWhatsappText()) // Store raw WhatsApp text
                .notes(String.format("WhatsApp Order - Confidence: %.2f\nWarnings: %s", 
                        parsed.getConfidenceScore(), 
                        String.join(", ", parsed.getWarnings())))
                .items(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();
        
        // Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();
        order.setOrderNumber(orderNumber);
        
        // Step 8: Create order items with product snapshots and validate stock
        List<OrderItem> orderItems = new ArrayList<>();
        for (WhatsAppTextParser.ParsedItem parsedItem : parsedItems) {
            if (parsedItem.getMatchedProductId() == null) {
                log.warn("Skipping item with no matched product: {}", parsedItem.getRawText());
                continue; // Skip items without matched products
            }
            
            // Fetch product
            Product product = productRepository.findById(parsedItem.getMatchedProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product", "id", parsedItem.getMatchedProductId()));
            
            // Validate stock availability
            if (product.getStockQuantity() < parsedItem.getQuantity()) {
                throw new InsufficientStockException(
                        product.getName(),
                        product.getStockQuantity(),
                        parsedItem.getQuantity());
            }
            
            // Create order item with product snapshot
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .skuSnapshot(product.getSku())
                    .quantity(parsedItem.getQuantity())
                    .unitPrice(product.getSalePrice())
                    .mrpSnapshot(product.getMrp())
                    .discount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .build();
            
            // Calculate line total
            orderItem.calculateLineTotal();
            
            // Add item to order
            order.addItem(orderItem);
            orderItems.add(orderItem);
            
            log.debug("Added order item: product={}, quantity={}, price={}", 
                    product.getName(), parsedItem.getQuantity(), product.getSalePrice());
        }
        
        // Validate we have at least one item after filtering
        if (orderItems.isEmpty()) {
            throw new IllegalArgumentException("No valid products found in WhatsApp message. All parsed items failed validation.");
        }
        
        // Step 9: Calculate order totals
        order.calculateOrderTotals();
        
        // Step 10: Save order (cascades to items)
        Order savedOrder = orderRepository.save(order);
        
        // Step 11: Create initial OrderStatusHistory record
        addStatusHistory(savedOrder, null, Order.OrderStatus.NEW.name(),
                userId, String.format("WhatsApp order created (confidence: %.2f)", parsed.getConfidenceScore()));

        // Audit: WhatsApp order created (Req 32.4)
        auditOrderCreated(savedOrder, userId);

        log.info("WhatsApp order created: {} by user: {}, customer: {}, items: {}, total: {}, confidence: {}",
                savedOrder.getOrderNumber(), userId, customer.getName(), 
                orderItems.size(), savedOrder.getTotalAmount(), parsed.getConfidenceScore());
        
        // Step 12: Build response with confidence score and warnings
        OrderResponse response = mapToOrderResponse(savedOrder, customer, salesperson);
        
        // Add parsing metadata to response notes or as custom fields
        // Note: OrderResponse might need to be extended to include confidenceScore and warnings
        // For now, this information is stored in order.notes
        
        return response;
    }

    /**
     * Check for duplicate orders based on customer phone and product similarity.
     * Implements Requirements 11.1, 11.2, 11.3, 11.4, 11.5
     * 
     * This method:
     * - Finds customer by phone (Req 11.1)
     * - Queries orders within configured day window (default 7 days) (Req 11.1)
     * - Calculates Jaccard similarity for product sets (Req 11.2)
     * - Flags orders with 80% or higher similarity (Req 11.3)
     * - Returns list of potential duplicates with similarity scores (Req 11.4)
     * - Returns no duplicates when customer doesn't exist (Req 11.5)
     * 
     * Jaccard Similarity = |A ∩ B| / |A ∪ B|
     * Where A and B are sets of product IDs in two orders
     * 
     * @param customerPhone Phone number to search for customer
     * @param productIds List of product IDs in the order being checked
     * @param orderDate Date of the order being checked (for time window calculation)
     * @return DuplicateCheckResult with potential duplicates and similarity scores
     */
    public DuplicateCheckResult checkDuplicate(String customerPhone, List<Long> productIds, LocalDate orderDate) {
        log.info("Checking for duplicate orders: phone={}, products={}, date={}", 
                customerPhone, productIds.size(), orderDate);
        
        // Step 1: Find customer by phone (Req 11.5 - return no duplicates if customer doesn't exist)
        Customer customer = customerRepository.findByPhone(customerPhone).orElse(null);
        if (customer == null) {
            log.info("No customer found with phone: {}, no duplicates possible", customerPhone);
            return DuplicateCheckResult.builder()
                    .hasDuplicates(false)
                    .potentialDuplicates(new ArrayList<>())
                    .customerPhone(customerPhone)
                    .build();
        }
        
        // Step 2: Get configured duplicate check window (default 7 days) (Req 11.1)
        int duplicateCheckDays = configurationService.getDuplicateCheckDays();
        LocalDate startDate = orderDate.minusDays(duplicateCheckDays);
        LocalDate endDate = orderDate.plusDays(duplicateCheckDays);
        
        log.debug("Searching for orders within {} days: {} to {}", 
                duplicateCheckDays, startDate, endDate);
        
        // Step 3: Query recent orders within time window (Req 11.1)
        List<Order> recentOrders = orderRepository.findByCustomerIdAndOrderDateBetween(
                customer.getId(), startDate, endDate);
        
        log.info("Found {} orders for customer {} within time window", 
                recentOrders.size(), customer.getId());
        
        // Step 4: Convert input product IDs to a Set for Jaccard similarity calculation
        Set<Long> inputProductSet = new HashSet<>(productIds);
        
        // Step 5: Calculate Jaccard similarity for each order and filter by threshold (Req 11.2, 11.3)
        List<DuplicateCheckResult.DuplicateOrderInfo> potentialDuplicates = new ArrayList<>();
        final double SIMILARITY_THRESHOLD = 0.80; // 80% threshold (Req 11.3)
        
        for (Order order : recentOrders) {
            // Get product IDs from order items
            Set<Long> orderProductSet = order.getItems().stream()
                    .map(item -> item.getProduct().getId())
                    .collect(Collectors.toSet());
            
            // Calculate Jaccard similarity (Req 11.2)
            double similarity = calculateJaccardSimilarity(inputProductSet, orderProductSet);
            
            log.debug("Order {}: similarity={}, products={}", 
                    order.getOrderNumber(), similarity, orderProductSet.size());
            
            // Flag orders with 80% or higher similarity (Req 11.3)
            if (similarity >= SIMILARITY_THRESHOLD) {
                // Find common products (intersection)
                Set<Long> commonProducts = new HashSet<>(inputProductSet);
                commonProducts.retainAll(orderProductSet);
                
                // Calculate days difference
                long daysDifference = Math.abs(ChronoUnit.DAYS.between(orderDate, order.getOrderDate()));
                
                // Build duplicate order info (Req 11.4)
                DuplicateCheckResult.DuplicateOrderInfo duplicateInfo = DuplicateCheckResult.DuplicateOrderInfo.builder()
                        .orderId(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .orderDate(order.getOrderDate())
                        .similarityScore(similarity)
                        .daysDifference(daysDifference)
                        .commonProductIds(new ArrayList<>(commonProducts))
                        .orderStatus(order.getStatus().name())
                        .build();
                
                potentialDuplicates.add(duplicateInfo);
                
                log.info("Potential duplicate found: order={}, similarity={}, days={}", 
                        order.getOrderNumber(), similarity, daysDifference);
            }
        }
        
        // Step 6: Build and return result (Req 11.4)
        DuplicateCheckResult result = DuplicateCheckResult.builder()
                .hasDuplicates(!potentialDuplicates.isEmpty())
                .potentialDuplicates(potentialDuplicates)
                .customerPhone(customerPhone)
                .build();
        
        log.info("Duplicate check complete: hasDuplicates={}, count={}", 
                result.isHasDuplicates(), potentialDuplicates.size());
        
        return result;
    }

    /**
     * Calculate Jaccard similarity between two sets of product IDs.
     * 
     * Jaccard Similarity = |A ∩ B| / |A ∪ B|
     * 
     * @param set1 First set of product IDs
     * @param set2 Second set of product IDs
     * @return Similarity score between 0.0 and 1.0
     */
    private double calculateJaccardSimilarity(Set<Long> set1, Set<Long> set2) {
        // Handle edge cases
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0; // Both empty sets are considered identical
        }
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0; // One empty set means no similarity
        }
        
        // Calculate intersection (common elements)
        Set<Long> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        int intersectionSize = intersection.size();
        
        // Calculate union (all unique elements)
        Set<Long> union = new HashSet<>(set1);
        union.addAll(set2);
        int unionSize = union.size();
        
        // Jaccard similarity = |intersection| / |union|
        return (double) intersectionSize / unionSize;
    }

    /**
     * Create storefront order with StorefrontOrderRequest DTO.
     * Wrapper method that finds or creates customer and delegates to createStorefrontOrder.
     * Requirements: 1.3
     */
    @Transactional
    public OrderResponse createStorefrontOrder(com.ayurveda.platform.dto.request.StorefrontOrderRequest request) {
        // Find or create customer
        Customer customer = customerService.findOrCreateCustomer(
                request.getCustomerName(),
                request.getCustomerPhone(),
                request.getCustomerEmail(),
                request.getDeliveryAddress(),
                request.getCity(),
                request.getState(),
                request.getPincode()
        );

        // Create order using existing method
        Order order = createStorefrontOrder(customer, request);

        // Map to response DTO
        return mapToOrderResponse(order, customer, null);
    }

    /**
     * Update existing order details.
     * Requirements: 1.1, 1.2, 4.1
     */
    @Transactional
    public OrderResponse updateOrder(Long orderId, com.ayurveda.platform.dto.request.UpdateOrderRequest request, Long userId) {
        // Fetch existing order
        Order order = getOrderById(orderId);

        // Update customer if changed
        if (request.getCustomerId() != null && !request.getCustomerId().equals(order.getCustomer().getId())) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getCustomerId()));
            order.setCustomer(customer);
        }

        // Update salesperson if changed
        if (request.getSalespersonId() != null) {
            Salesperson salesperson = salespersonRepository.findById(request.getSalespersonId())
                    .orElseThrow(() -> new ResourceNotFoundException("Salesperson", "id", request.getSalespersonId()));
            order.setSalespersonId(salesperson.getId());
        }

        // Update order fields
        if (request.getDiscountAmount() != null) {
            order.setDiscountAmount(request.getDiscountAmount());
        }
        if (request.getShippingCharge() != null) {
            order.setShippingCharge(request.getShippingCharge());
        }
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }
        if (request.getOrderDate() != null) {
            order.setOrderDate(request.getOrderDate());
        }

        // Update items if provided
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            // Clear existing items
            order.getItems().clear();
            orderItemRepository.flush();

            // Add new items
            for (com.ayurveda.platform.dto.request.UpdateOrderRequest.OrderItemUpdateDTO itemReq : request.getItems()) {
                Product product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemReq.getProductId()));

                OrderItem orderItem = OrderItem.builder()
                        .product(product)
                        .productNameSnapshot(product.getName())
                        .skuSnapshot(product.getSku())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(product.getSalePrice())
                        .mrpSnapshot(product.getMrp())
                        .discount(itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO)
                        .build();

                orderItem.calculateLineTotal();
                order.addItem(orderItem);
            }
        }

        // Recalculate totals
        order.calculateOrderTotals();

        // Save order
        Order savedOrder = orderRepository.save(order);

        log.info("Order updated: {} by user: {}", savedOrder.getOrderNumber(), userId);

        // Return response
        return mapToOrderResponse(savedOrder, savedOrder.getCustomer(), 
                savedOrder.getSalespersonId() != null ? salespersonRepository.findById(savedOrder.getSalespersonId()).orElse(null) : null);
    }

    /**
     * Update order status with new request DTO.
     * Requirements: 5.1-5.11, 6.1-6.3
     */
    @Transactional
    public OrderResponse updateOrderStatusV2(Long orderId, com.ayurveda.platform.dto.request.UpdateOrderStatusRequest request, Long userId) {
        // Update status using existing method
        Order order = updateOrderStatus(orderId, request.getNewStatus(), userId, request.getNotes());

        // Map to response DTO
        return mapToOrderResponse(order, order.getCustomer(), 
                order.getSalespersonId() != null ? salespersonRepository.findById(order.getSalespersonId()).orElse(null) : null);
    }

    /**
     * Cancel an order with reason.
     * Requirements: 27.1-27.4
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason, Long userId) {
        // Req 27.2: a cancellation reason is mandatory. Enforce at the service
        // boundary so the rule holds for all callers, not just the API layer.
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }

        // Fetch order (throws if not found)
        Order order = getOrderById(orderId);

        // Req 27.1 / 27.3: transition to CANCELLED. updateOrderStatus validates
        // the transition against the state machine (terminal states DELIVERED,
        // CANCELLED and RETURNED cannot be cancelled) and restores stock for any
        // order whose stock was already reduced (PACKED or later).
        order = updateOrderStatus(orderId, Order.OrderStatus.CANCELLED, userId,
                "CANCELLATION: " + reason.trim());

        log.info("Order cancelled: {} by user: {}, reason: {}", order.getOrderNumber(), userId, reason);

        // Map to response DTO
        return mapToOrderResponse(order, order.getCustomer(), 
                order.getSalespersonId() != null ? salespersonRepository.findById(order.getSalespersonId()).orElse(null) : null);
    }

    /**
     * Return an order with details.
     * Requirements: 28.1-28.4
     */
    @Transactional
    public OrderResponse returnOrder(Long orderId, com.ayurveda.platform.dto.request.ReturnOrderRequest request, Long userId) {
        // Fetch order
        Order order = getOrderById(orderId);

        // Validate order can be returned (Req 28.1, 28.3)
        if (order.getStatus() != Order.OrderStatus.DELIVERED && order.getStatus() != Order.OrderStatus.DISPATCHED) {
            throw new IllegalArgumentException("Only DELIVERED or DISPATCHED orders can be returned");
        }

        // Validate return date is not in the future (design: Return Processing Algorithm)
        if (request.getReturnDate() != null && request.getReturnDate().isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Return date cannot be in the future");
        }

        // Build return notes capturing return information for the status history (Req 28.3, 28.4)
        StringBuilder returnNotes = new StringBuilder("RETURN: ");
        returnNotes.append(request.getReturnReason());
        if (request.getReturnDate() != null) {
            returnNotes.append(" | Return Date: ").append(request.getReturnDate());
        }
        if (request.getCustomerComments() != null) {
            returnNotes.append(" | Customer Comments: ").append(request.getCustomerComments());
        }
        if (request.getRefundRequested() != null && request.getRefundRequested()) {
            returnNotes.append(" | Refund Requested: ").append(request.getRefundMode() != null ? request.getRefundMode() : "Same as payment");
        }

        // Update status to RETURNED (this handles stock restoration)
        order = updateOrderStatus(orderId, Order.OrderStatus.RETURNED, userId, returnNotes.toString());

        log.info("Order returned: {} by user: {}, reason: {}", order.getOrderNumber(), userId, request.getReturnReason());

        // Map to response DTO
        return mapToOrderResponse(order, order.getCustomer(), 
                order.getSalespersonId() != null ? salespersonRepository.findById(order.getSalespersonId()).orElse(null) : null);
    }

    /**
     * Bulk update order status.
     * Requirements: 23.1-23.4
     */
    @Transactional
    public List<OrderResponse> bulkUpdateStatus(List<Long> orderIds, Order.OrderStatus targetStatus, String notes, Long userId) {
        List<OrderResponse> results = new ArrayList<>();

        for (Long orderId : orderIds) {
            try {
                // Update status
                Order order = updateOrderStatus(orderId, targetStatus, userId, notes);

                // Map to response DTO
                OrderResponse response = mapToOrderResponse(order, order.getCustomer(), 
                        order.getSalespersonId() != null ? salespersonRepository.findById(order.getSalespersonId()).orElse(null) : null);
                results.add(response);

                log.info("Bulk status update successful for order: {} to {}", order.getOrderNumber(), targetStatus);
            } catch (Exception e) {
                log.error("Bulk status update failed for order ID: {}, error: {}", orderId, e.getMessage());
                // Continue processing other orders (partial success)
                results.add(null);
            }
        }

        log.info("Bulk status update completed: {} successful, {} failed", 
                results.stream().filter(r -> r != null).count(),
                results.stream().filter(r -> r == null).count());

        return results;
    }
}

