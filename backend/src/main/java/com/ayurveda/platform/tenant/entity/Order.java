package com.ayurveda.platform.tenant.entity;

import com.ayurveda.platform.exception.InvalidStatusTransitionException;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Order entity in the tenant-specific database.
 * Tracks full lifecycle from NEW to DELIVERED/CANCELLED.
 */
@Entity
@Table(name = "orders", indexes = {
        // Unique fast lookup by order number (Req 30.x indexing strategy)
        @Index(name = "idx_orders_number", columnList = "order_number", unique = true),
        // Single-column indexes for common filters
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_date", columnList = "order_date"),
        @Index(name = "idx_orders_customer", columnList = "customer_id"),
        @Index(name = "idx_orders_salesperson", columnList = "salesperson_id"),
        @Index(name = "idx_orders_source", columnList = "order_source"),
        @Index(name = "idx_orders_payment", columnList = "payment_status"),
        // Composite indexes for common query patterns
        @Index(name = "idx_orders_status_date", columnList = "status, order_date"),
        @Index(name = "idx_orders_customer_date", columnList = "customer_id, order_date"),
        @Index(name = "idx_orders_salesperson_date", columnList = "salesperson_id, order_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Uniqueness enforced by the named unique index idx_orders_number (@Table indexes)
    @Column(name = "order_number", nullable = false, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "salesperson_id")
    private Long salespersonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_source", nullable = false)
    @Builder.Default
    private OrderSource orderSource = OrderSource.MANUAL;

    @Column(name = "raw_whatsapp_text", columnDefinition = "TEXT")
    private String rawWhatsappText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @Column(name = "subtotal", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "shipping_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shippingCharge = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 100)
    private String razorpayPaymentId;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_discount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal couponDiscount = BigDecimal.ZERO;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @JsonManagedReference("order-status-history")
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PaymentRecord> paymentRecords = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- Enums ---

    public enum OrderSource {
        WHATSAPP, MANUAL, STOREFRONT, API
    }

    public enum OrderStatus {
        NEW, CONFIRMED, PAID, PACKED, DISPATCHED, DELIVERED, CANCELLED, RETURNED
    }

    public enum PaymentMode {
        COD, UPI, BANK_TRANSFER, ONLINE, CREDIT
    }

    public enum PaymentStatus {
        PENDING, PARTIAL, PAID, REFUNDED
    }

    // --- Valid Status Transitions Map ---
    // Defines allowed transitions for each status based on Requirements 5.1-5.7
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new HashMap<>();
    
    static {
        // NEW can transition to CONFIRMED or CANCELLED (Requirement 5.2)
        VALID_TRANSITIONS.put(OrderStatus.NEW, Set.of(
                OrderStatus.CONFIRMED,
                OrderStatus.CANCELLED
        ));
        
        // CONFIRMED can transition to PAID or CANCELLED (Requirement 5.3)
        VALID_TRANSITIONS.put(OrderStatus.CONFIRMED, Set.of(
                OrderStatus.PAID,
                OrderStatus.CANCELLED
        ));
        
        // PAID can transition to PACKED or CANCELLED (Requirement 5.4)
        VALID_TRANSITIONS.put(OrderStatus.PAID, Set.of(
                OrderStatus.PACKED,
                OrderStatus.CANCELLED
        ));
        
        // PACKED can transition to DISPATCHED or back to PAID (Requirement 5.5)
        VALID_TRANSITIONS.put(OrderStatus.PACKED, Set.of(
                OrderStatus.DISPATCHED,
                OrderStatus.PAID
        ));
        
        // DISPATCHED can transition to DELIVERED or RETURNED (Requirement 5.6)
        VALID_TRANSITIONS.put(OrderStatus.DISPATCHED, Set.of(
                OrderStatus.DELIVERED,
                OrderStatus.RETURNED
        ));
        
        // DELIVERED can transition to RETURNED (Requirement 5.7)
        VALID_TRANSITIONS.put(OrderStatus.DELIVERED, Set.of(
                OrderStatus.RETURNED
        ));
        
        // CANCELLED and RETURNED are terminal states (no transitions allowed)
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, Set.of());
        VALID_TRANSITIONS.put(OrderStatus.RETURNED, Set.of());
    }

    // --- Helper methods ---

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    /**
     * Recalculate order totals from line items.
     * Implements Requirements 4.1, 4.2, 4.4, 4.5
     * - Subtotal: sum of all OrderItem lineTotals (Req 4.1)
     * - TotalAmount: subtotal - discount + tax + shipping (Req 4.2)
     * - 2 decimal place precision maintained (Req 4.4)
     * - Zero subtotal when no items (Req 4.5)
     */
    public void recalculateTotals() {
        // Calculate subtotal as sum of all order item line totals
        this.subtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        
        // Calculate total amount: subtotal - discount + tax + shipping
        this.totalAmount = subtotal
                .subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO)
                .add(taxAmount != null ? taxAmount : BigDecimal.ZERO)
                .add(shippingCharge != null ? shippingCharge : BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Alias for recalculateTotals() to match design document interface.
     * Calculates order totals from line items.
     */
    public void calculateOrderTotals() {
        recalculateTotals();
    }

    /**
     * Validates if a status transition is allowed based on state machine rules and business rules.
     * Implements Requirements 5.1-5.9
     * 
     * Validation Rules:
     * - Check if transition is allowed by state machine (Req 5.2-5.7)
     * - DISPATCHED requires PAID payment status (Req 5.9)
     * - DISPATCHED requires valid shipping address (Req 5.4, implicit)
     * - PACKED requires stock availability (Req 5.5, implicit)
     * 
     * @param newStatus The target status to transition to
     * @throws InvalidStatusTransitionException if transition is not allowed (Req 5.8)
     */
    public void isValidStatusTransition(OrderStatus newStatus) {
        OrderStatus currentStatus = this.status;
        
        // Check if transition is allowed by state machine
        Set<OrderStatus> allowedTransitions = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTransitions == null || !allowedTransitions.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    currentStatus.name(),
                    newStatus.name(),
                    String.format("Transition from %s to %s is not allowed by workflow rules", 
                            currentStatus, newStatus)
            );
        }
        
        // Business Rule: DISPATCHED requires PAID payment status (Requirement 5.9)
        if (newStatus == OrderStatus.DISPATCHED) {
            if (this.paymentStatus != PaymentStatus.PAID) {
                throw new InvalidStatusTransitionException(
                        currentStatus.name(),
                        newStatus.name(),
                        String.format("Cannot dispatch order - payment status is %s, must be PAID", 
                                this.paymentStatus)
                );
            }
            
            // Business Rule: DISPATCHED requires valid shipping address (Requirement 5.4)
            if (this.customer == null) {
                throw new InvalidStatusTransitionException(
                        currentStatus.name(),
                        newStatus.name(),
                        "Cannot dispatch order - no customer information available"
                );
            }
            
            // Validate customer has shipping address
            if (isAddressInvalid(this.customer)) {
                throw new InvalidStatusTransitionException(
                        currentStatus.name(),
                        newStatus.name(),
                        "Cannot dispatch order - customer must have complete shipping address (address, city, state, pincode)"
                );
            }
        }
        
        // Business Rule: PACKED requires stock availability check (Requirement 5.5)
        // Note: Actual stock validation should be performed in service layer before calling this
        // This method validates the transition rules, not data availability
        if (newStatus == OrderStatus.PACKED) {
            if (this.items == null || this.items.isEmpty()) {
                throw new InvalidStatusTransitionException(
                        currentStatus.name(),
                        newStatus.name(),
                        "Cannot pack order - order has no items"
                );
            }
        }
    }
    
    /**
     * Helper method to validate if customer address is complete for shipping.
     * 
     * @param customer The customer to validate
     * @return true if address is invalid/incomplete, false if valid
     */
    private boolean isAddressInvalid(Customer customer) {
        return customer.getAddressLine1() == null || customer.getAddressLine1().trim().isEmpty() ||
               customer.getCity() == null || customer.getCity().trim().isEmpty() ||
               customer.getState() == null || customer.getState().trim().isEmpty() ||
               customer.getPincode() == null || customer.getPincode().trim().isEmpty();
    }
}
