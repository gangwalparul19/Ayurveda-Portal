package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.bulkUpdateStatus().
 *
 * The bulk operation updates the status of each order independently, continuing
 * on individual failures (partial success). It returns an index-aligned list of
 * OrderResponse where a {@code null} entry indicates the corresponding order failed.
 *
 * Tests Requirement 23: Bulk Order Operations (23.1 - 23.4)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service - Bulk Operations Tests")
class OrderServiceBulkOperationsTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SalespersonRepository salespersonRepository;

    @Mock
    private ProductManagementService productManagementService;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .phone("9876543210")
                .addressLine1("123 Test Street")
                .city("Test City")
                .state("Test State")
                .pincode("123456")
                .build();
    }

    /**
     * Build a minimal but complete order in the given status. Items and history
     * collections are initialized so the response mapping does not fail.
     */
    private Order buildOrder(Long id, Order.OrderStatus status) {
        Product product = Product.builder()
                .id(id)
                .sku("PROD-" + id)
                .name("Product " + id)
                .salePrice(new BigDecimal("100.00"))
                .mrp(new BigDecimal("150.00"))
                .stockQuantity(100)
                .build();

        OrderItem item = OrderItem.builder()
                .id(id)
                .product(product)
                .productNameSnapshot("Product " + id)
                .skuSnapshot("PROD-" + id)
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .mrpSnapshot(new BigDecimal("150.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("200.00"))
                .build();

        Order order = Order.builder()
                .id(id)
                .orderNumber("ORD-20260627-000" + id)
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(status)
                .paymentStatus(Order.PaymentStatus.PAID)
                .paymentMode(Order.PaymentMode.UPI)
                .subtotal(new BigDecimal("200.00"))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("200.00"))
                .orderDate(LocalDate.now())
                .items(new ArrayList<>(List.of(item)))
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        item.setOrder(order);
        return order;
    }

    @Test
    @DisplayName("Bulk update succeeds for all valid orders (Req 23.1, 23.2, 23.4)")
    void bulkUpdateStatus_allValidOrders_allSucceed() {
        // Arrange - three orders in NEW status, all can transition to CONFIRMED
        Order order1 = buildOrder(1L, Order.OrderStatus.NEW);
        Order order2 = buildOrder(2L, Order.OrderStatus.NEW);
        Order order3 = buildOrder(3L, Order.OrderStatus.NEW);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order2));
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order3));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Long> orderIds = List.of(1L, 2L, 3L);

        // Act
        List<OrderResponse> results = orderService.bulkUpdateStatus(
                orderIds, Order.OrderStatus.CONFIRMED, "Bulk confirm", 100L);

        // Assert - index aligned, no nulls, all transitioned
        assertThat(results).hasSize(3);
        assertThat(results).doesNotContainNull();
        assertThat(results).allSatisfy(r ->
                assertThat(r.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED));

        // Each order saved (Req 23.4 - status history persisted per successful order)
        verify(orderRepository, times(3)).save(any(Order.class));

        // Status history record created for each successful order (Req 23.4)
        assertThat(order1.getStatusHistory()).hasSize(1);
        assertThat(order2.getStatusHistory()).hasSize(1);
        assertThat(order3.getStatusHistory()).hasSize(1);
        assertThat(order1.getStatusHistory().get(0).getToStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Bulk update returns partial success when some orders fail (Req 23.2, 23.3)")
    void bulkUpdateStatus_someInvalidOrders_partialSuccess() {
        // Arrange
        // order 1: NEW -> CONFIRMED  (valid)
        // order 2: not found         (failure)
        // order 3: CANCELLED -> CONFIRMED (invalid transition, terminal state)
        // order 4: NEW -> CONFIRMED  (valid)
        Order order1 = buildOrder(1L, Order.OrderStatus.NEW);
        Order order3 = buildOrder(3L, Order.OrderStatus.CANCELLED);
        Order order4 = buildOrder(4L, Order.OrderStatus.NEW);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(2L)).thenReturn(Optional.empty()); // not found
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order3));
        when(orderRepository.findById(4L)).thenReturn(Optional.of(order4));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Long> orderIds = List.of(1L, 2L, 3L, 4L);

        // Act
        List<OrderResponse> results = orderService.bulkUpdateStatus(
                orderIds, Order.OrderStatus.CONFIRMED, "Bulk confirm", 100L);

        // Assert - index alignment preserved with nulls for failures (Req 23.3)
        assertThat(results).hasSize(4);
        assertThat(results.get(0)).isNotNull();
        assertThat(results.get(0).getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(results.get(1)).isNull();  // not found
        assertThat(results.get(2)).isNull();  // invalid transition
        assertThat(results.get(3)).isNotNull();
        assertThat(results.get(3).getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);

        // Only the two successful orders are persisted
        verify(orderRepository, times(2)).save(any(Order.class));

        // Processing continued past failures: order 4 was still reached and updated (Req 23.3)
        assertThat(order4.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);

        // Failed orders are untouched
        assertThat(order3.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("Bulk update with empty order list returns empty results and does nothing")
    void bulkUpdateStatus_emptyList_returnsEmpty() {
        // Act
        List<OrderResponse> results = orderService.bulkUpdateStatus(
                Collections.emptyList(), Order.OrderStatus.CONFIRMED, "noop", 100L);

        // Assert
        assertThat(results).isEmpty();
        verify(orderRepository, never()).findById(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Bulk update where every order fails returns all-null index-aligned results")
    void bulkUpdateStatus_allOrdersFail_allNull() {
        // Arrange - all order IDs are missing
        when(orderRepository.findById(anyLong())).thenReturn(Optional.empty());

        List<Long> orderIds = List.of(10L, 11L, 12L);

        // Act
        List<OrderResponse> results = orderService.bulkUpdateStatus(
                orderIds, Order.OrderStatus.CONFIRMED, "Bulk confirm", 100L);

        // Assert
        assertThat(results).hasSize(3);
        assertThat(results).containsOnlyNulls();
        verify(orderRepository, never()).save(any(Order.class));
    }
}
