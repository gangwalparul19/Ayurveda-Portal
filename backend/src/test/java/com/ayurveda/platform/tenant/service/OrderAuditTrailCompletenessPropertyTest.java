package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.master.service.AuditLogService;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.OrderStatusHistory;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.CouponUsageRepository;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-Based Tests for Order Status Audit Trail Completeness using jqwik.
 *
 * <p><b>Property 7: Audit Trail Completeness</b>
 *
 * <p><b>Validates: Requirements 6.1, 6.2, 6.3</b>
 *
 * <p>Whenever an order's status is changed through
 * {@link OrderService#updateOrderStatus(Long, Order.OrderStatus, Long, String)},
 * the system must produce a complete, queryable audit trail:
 * <ul>
 *   <li><b>6.1</b> - each status change creates exactly one
 *       {@link OrderStatusHistory} record (no change is silently dropped);</li>
 *   <li><b>6.2</b> - that record carries the previous status, the new status,
 *       the user that performed the change, and a timestamp;</li>
 *   <li><b>6.3</b> - the complete chain of changes is retained on the order and
 *       can be retrieved, with each record's {@code fromStatus} continuing from
 *       the previous record's {@code toStatus}.</li>
 * </ul>
 *
 * <p>The property is exercised across many randomly generated, <i>valid</i>
 * transition paths through the order state machine (NEW &rarr; ... &rarr; a
 * terminal state) driven through the real {@link OrderService} logic. Only the
 * outermost collaborators are mocked, mirroring the existing
 * {@code OrderStatusTransitionComprehensiveTest} setup.
 *
 * <p>The {@code changedAt} timestamp is populated by the persistence layer via
 * Hibernate's {@code @CreationTimestamp} at commit time. As with the existing
 * Property 7 suite ({@code AuditTrailCompletenessPropertyTest}), the test
 * simulates that persistence side-effect so the completeness of the in-memory
 * records reflects what the database would store after each transactional
 * {@code updateOrderStatus} call.
 */
class OrderAuditTrailCompletenessPropertyTest {

    private static final long ORDER_ID = 1L;

    /** Order state machine transitions, mirroring {@code Order.VALID_TRANSITIONS}. */
    private static final Map<Order.OrderStatus, Set<Order.OrderStatus>> VALID_TRANSITIONS = new HashMap<>();

    static {
        VALID_TRANSITIONS.put(Order.OrderStatus.NEW,
                Set.of(Order.OrderStatus.CONFIRMED, Order.OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(Order.OrderStatus.CONFIRMED,
                Set.of(Order.OrderStatus.PAID, Order.OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(Order.OrderStatus.PAID,
                Set.of(Order.OrderStatus.PACKED, Order.OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(Order.OrderStatus.PACKED,
                Set.of(Order.OrderStatus.DISPATCHED, Order.OrderStatus.PAID));
        VALID_TRANSITIONS.put(Order.OrderStatus.DISPATCHED,
                Set.of(Order.OrderStatus.DELIVERED, Order.OrderStatus.RETURNED));
        VALID_TRANSITIONS.put(Order.OrderStatus.DELIVERED,
                Set.of(Order.OrderStatus.RETURNED));
        VALID_TRANSITIONS.put(Order.OrderStatus.CANCELLED, Set.of());
        VALID_TRANSITIONS.put(Order.OrderStatus.RETURNED, Set.of());
    }

    /**
     * **Validates: Requirements 6.1, 6.2, 6.3**
     *
     * Property 7: Audit Trail Completeness.
     *
     * For any valid sequence of order status changes:
     * <ol>
     *   <li>each change appends exactly one OrderStatusHistory record (6.1);</li>
     *   <li>the appended record's previousStatus, newStatus and user match the
     *       change that was requested, and a timestamp is recorded (6.2);</li>
     *   <li>the full history is retained and forms an unbroken chain from the
     *       order's original NEW status (6.3).</li>
     * </ol>
     */
    @Property(tries = 500)
    @Label("Property 7: Audit Trail Completeness - every order status change yields a complete OrderStatusHistory record")
    void auditTrailCompleteness(
            @ForAll("validTransitionPaths") List<Order.OrderStatus> path,
            @ForAll("userIds") List<Long> userIds
    ) {
        // Arrange: fresh collaborators and order per try so state is isolated.
        OrderRepository orderRepository = mock(OrderRepository.class);
        ProductManagementService productManagementService = mock(ProductManagementService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        OrderService orderService = new OrderService(
                orderRepository,
                null,   // orderItemRepository - unused on this path
                null,   // customerRepository - unused
                null,   // productRepository - unused
                null,   // salespersonRepository - unused
                null,   // orderNumberGenerator - unused
                productManagementService,
                null,   // paymentRecordRepository - unused
                null,   // whatsAppParser - unused
                null,   // customerService - unused
                null,   // configurationService - unused
                auditLogService,
                null    // couponUsageRepository - unused
        );

        Order order = buildOrderReadyForLifecycle();

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            // Simulate Hibernate flushing previously-added history records.
            stampPersistenceTimestamps(saved);
            return saved;
        });

        // Act + Assert: drive each status change and verify the audit trail grows
        // completely and correctly after every single change.
        Order.OrderStatus previousStatus = Order.OrderStatus.NEW;
        int expectedRecords = 0;

        for (int step = 0; step < path.size(); step++) {
            Order.OrderStatus newStatus = path.get(step);
            Long actingUser = userIds.get(step % userIds.size());
            String notes = "transition-" + step;

            orderService.updateOrderStatus(ORDER_ID, newStatus, actingUser, notes);

            // Simulate the @Transactional commit: @CreationTimestamp populates
            // changedAt for the record created by this status change.
            stampPersistenceTimestamps(order);

            expectedRecords++;

            // Requirement 6.1: exactly one history record per status change.
            assertThat(order.getStatusHistory())
                    .as("each status change must create exactly one OrderStatusHistory record")
                    .hasSize(expectedRecords);

            // Requirement 6.2: the newest record must be complete.
            OrderStatusHistory latest = order.getStatusHistory().get(expectedRecords - 1);
            assertThat(latest.getFromStatus())
                    .as("history must record the previous status")
                    .isEqualTo(previousStatus.name());
            assertThat(latest.getToStatus())
                    .as("history must record the new status")
                    .isEqualTo(newStatus.name());
            assertThat(latest.getChangedBy())
                    .as("history must record the acting user")
                    .isEqualTo(actingUser);
            assertThat(latest.getChangedAt())
                    .as("history must record a timestamp")
                    .isNotNull();

            previousStatus = newStatus;
        }

        // Requirement 6.3: the complete status history is retained and forms an
        // unbroken chain starting from the order's original NEW status.
        List<OrderStatusHistory> history = order.getStatusHistory();
        assertThat(history)
                .as("complete history retained for all status changes")
                .hasSize(path.size());

        Order.OrderStatus chainPrevious = Order.OrderStatus.NEW;
        for (int i = 0; i < history.size(); i++) {
            OrderStatusHistory record = history.get(i);
            assertThat(record.getFromStatus())
                    .as("record %d must continue the chain from the prior status", i)
                    .isEqualTo(chainPrevious.name());
            assertThat(record.getToStatus()).isEqualTo(path.get(i).name());
            assertThat(record.getChangedBy())
                    .as("every record must retain its acting user")
                    .isNotNull();
            assertThat(record.getChangedAt())
                    .as("every record must retain its timestamp")
                    .isNotNull();
            chainPrevious = path.get(i);
        }
    }

    // ===== Helpers =====

    /**
     * Builds an order positioned at NEW with everything required to traverse the
     * full lifecycle without tripping business rules in
     * {@code Order.isValidStatusTransition}: a PAID payment status and a customer
     * with a complete shipping address (needed for DISPATCHED), plus at least one
     * line item with a product (needed for PACKED).
     */
    private Order buildOrderReadyForLifecycle() {
        Customer customer = Customer.builder()
                .id(1L)
                .name("Audit Customer")
                .phone("9876543210")
                .addressLine1("123 Audit Street")
                .city("Audit City")
                .state("Audit State")
                .pincode("123456")
                .build();

        Product product = Product.builder()
                .id(1L)
                .sku("PROD-AUDIT")
                .name("Audit Product")
                .salePrice(new BigDecimal("100.00"))
                .mrp(new BigDecimal("150.00"))
                .stockQuantity(1000)
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .product(product)
                .productNameSnapshot("Audit Product")
                .skuSnapshot("PROD-AUDIT")
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .mrpSnapshot(new BigDecimal("150.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("100.00"))
                .build();

        Order order = Order.builder()
                .id(ORDER_ID)
                .orderNumber("ORD-20260101-0001")
                .customer(customer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .paymentStatus(Order.PaymentStatus.PAID)
                .paymentMode(Order.PaymentMode.UPI)
                .subtotal(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("100.00"))
                .orderDate(LocalDate.now())
                .items(new ArrayList<>(List.of(item)))
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        item.setOrder(order);
        return order;
    }

    /**
     * Simulates the persistence layer populating {@code changedAt} via
     * {@code @CreationTimestamp} when the surrounding transaction commits.
     */
    private void stampPersistenceTimestamps(Order order) {
        for (OrderStatusHistory record : order.getStatusHistory()) {
            if (record.getChangedAt() == null) {
                record.setChangedAt(LocalDateTime.now());
            }
        }
    }

    // ===== Generators =====

    /**
     * Generates valid transition paths through the order state machine. A list
     * of arbitrary choice indices is resolved against the state machine starting
     * from NEW, picking a valid next status at each step until a terminal state
     * is reached or the path length is exhausted. The resulting path therefore
     * always represents a legal sequence of at least one status change.
     */
    @Provide
    Arbitrary<List<Order.OrderStatus>> validTransitionPaths() {
        return Arbitraries.integers().between(0, 1000)
                .list().ofMinSize(1).ofMaxSize(8)
                .map(this::resolvePath);
    }

    private List<Order.OrderStatus> resolvePath(List<Integer> choices) {
        List<Order.OrderStatus> path = new ArrayList<>();
        Order.OrderStatus current = Order.OrderStatus.NEW;
        for (Integer choice : choices) {
            List<Order.OrderStatus> nextOptions = new ArrayList<>(VALID_TRANSITIONS.get(current));
            if (nextOptions.isEmpty()) {
                break; // terminal state reached
            }
            // Deterministic ordering so a given choice list always yields the same path.
            nextOptions.sort(Comparator.comparingInt(Enum::ordinal));
            Order.OrderStatus next = nextOptions.get(Math.abs(choice) % nextOptions.size());
            path.add(next);
            current = next;
        }
        return path;
    }

    /** Acting user identifiers - always present and positive. */
    @Provide
    Arbitrary<List<Long>> userIds() {
        return Arbitraries.longs().between(1L, 1_000_000L)
                .list().ofMinSize(1).ofMaxSize(8);
    }
}
