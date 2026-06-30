package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.SalespersonOrderResponse;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.repository.PlatformUserRepository;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.Salesperson;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.SalespersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for salesperson to view their own orders with masked customer details.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class SalespersonOrderService {

    private final OrderRepository orderRepository;
    private final SalespersonRepository salespersonRepository;
    private final PlatformUserRepository platformUserRepository;

    /**
     * Get salesperson's own orders with appropriate masking based on role.
     * 
     * @param username The logged-in username
     * @param pageable Pagination info
     * @return Page of orders with masked/unmasked customer details based on role
     */
    public Page<SalespersonOrderResponse> getMyOrders(String username, Pageable pageable) {
        PlatformUser user = platformUserRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        Salesperson salesperson = salespersonRepository.findByPlatformUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Salesperson record not found for user: " + username));
        
        // Get orders for this salesperson only
        Page<Order> orders = orderRepository.findBySalespersonId(salesperson.getId(), pageable);
        
        boolean shouldMask = user.getRole() == PlatformUser.UserRole.SALESPERSON;
        
        List<SalespersonOrderResponse> responseList = orders.getContent().stream()
                .map(order -> convertToResponse(order, salesperson, shouldMask))
                .collect(Collectors.toList());
        
        return new PageImpl<>(responseList, pageable, orders.getTotalElements());
    }

    /**
     * Get a specific order by ID.
     * Salesperson can only view their own orders.
     * Sales head/admin can view any order.
     */
    public SalespersonOrderResponse getOrderById(String username, Long orderId) {
        PlatformUser user = platformUserRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        
        // Check access permissions
        boolean isSalesperson = user.getRole() == PlatformUser.UserRole.SALESPERSON;
        boolean isSalesHeadOrAdmin = user.getRole() == PlatformUser.UserRole.MANAGER 
                                   || user.getRole() == PlatformUser.UserRole.TENANT_ADMIN
                                   || user.getRole() == PlatformUser.UserRole.SUPER_ADMIN;
        
        if (isSalesperson) {
            // Salesperson can only see their own orders
            Salesperson salesperson = salespersonRepository.findByPlatformUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Salesperson record not found"));
            
            if (!order.getSalespersonId().equals(salesperson.getId())) {
                log.warn("Salesperson {} attempted to access order {} belonging to another salesperson", 
                        username, orderId);
                throw new AccessDeniedException("You can only view your own orders");
            }
            
            return convertToResponse(order, salesperson, true);
        } else if (isSalesHeadOrAdmin) {
            // Sales head/admin can see all orders with full details
            Salesperson salesperson = null;
            if (order.getSalespersonId() != null) {
                salesperson = salespersonRepository.findById(order.getSalespersonId())
                        .orElse(null);
            }
            return convertToResponse(order, salesperson, false);
        } else {
            throw new AccessDeniedException("Insufficient permissions to view orders");
        }
    }

    /**
     * Get orders for all salespersons (Sales Head / Admin only).
     */
    public Page<SalespersonOrderResponse> getAllSalespersonOrders(String username, Pageable pageable) {
        PlatformUser user = platformUserRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        boolean isSalesHeadOrAdmin = user.getRole() == PlatformUser.UserRole.MANAGER 
                                   || user.getRole() == PlatformUser.UserRole.TENANT_ADMIN
                                   || user.getRole() == PlatformUser.UserRole.SUPER_ADMIN;
        
        if (!isSalesHeadOrAdmin) {
            throw new AccessDeniedException("Only Sales Head or Admin can view all orders");
        }
        
        // Get all orders that have a salesperson assigned
        Page<Order> orders = orderRepository.findBySalespersonIdIsNotNull(pageable);
        
        List<SalespersonOrderResponse> responseList = orders.getContent().stream()
                .map(order -> {
                    Salesperson sp = salespersonRepository.findById(order.getSalespersonId())
                            .orElse(null);
                    return convertToResponse(order, sp, false); // No masking for sales head
                })
                .collect(Collectors.toList());
        
        return new PageImpl<>(responseList, pageable, orders.getTotalElements());
    }

    /**
     * Convert Order entity to response DTO and apply masking if needed.
     */
    private SalespersonOrderResponse convertToResponse(Order order, Salesperson salesperson, boolean shouldMask) {
        // Calculate commission
        BigDecimal commission = BigDecimal.ZERO;
        if (salesperson != null && salesperson.getCommissionRate() != null) {
            commission = order.getTotalAmount()
                    .multiply(salesperson.getCommissionRate())
                    .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        }
        
        // Convert order items
        List<SalespersonOrderResponse.OrderItemSummary> itemSummaries = order.getItems().stream()
                .map(this::convertToItemSummary)
                .collect(Collectors.toList());
        
        SalespersonOrderResponse response = SalespersonOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? order.getCustomer().getName() : "N/A")
                .customerPhone(order.getCustomer() != null ? order.getCustomer().getPhone() : "N/A")
                .customerAddress(order.getCustomer() != null ? (order.getCustomer().getAddressLine1() + order.getCustomer().getAddressLine2()) : "N/A")
                .customerEmail(order.getCustomer() != null ? order.getCustomer().getEmail() : "N/A")
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingCharge(order.getShippingCharge())
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .items(itemSummaries)
                .commissionEarned(commission)
                .notes(order.getNotes())
                .build();
        
        // Apply masking for salesperson role
        if (shouldMask) {
            response.maskCustomerDetails();
        }
        
        return response;
    }

    private SalespersonOrderResponse.OrderItemSummary convertToItemSummary(OrderItem item) {
        return SalespersonOrderResponse.OrderItemSummary.builder()
                .productName(item.getProduct() != null ? item.getProduct().getName() : "Unknown")
                .productSku(item.getProduct() != null ? item.getProduct().getSku() : "N/A")
                .quantity(item.getQuantity())
                .price(item.getUnitPrice())
                .subtotal(item.getLineTotal())
                .build();
    }
}
