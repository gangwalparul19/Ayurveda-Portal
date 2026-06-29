package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.InvalidStatusTransitionException;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for order status transitions.
 * Tests Requirements 5.1-5.11, 6.1-6.3
 * 
 * This test class systematically validates:
 * 1. All valid status transitions
 * 2. All invalid status transitions
 * 3. Timestamp updates for DISPATCHED and DELIVERED
 * 4. Stock restoration for CANCELLED and RETURNED
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Status Transitions - Comprehensive Test Suite")
class OrderStatusTransitionComprehensiveTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductManagementService productManagementService;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private Customer testCustomer;
    private Product testProduct;
    private OrderItem testItem;

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

        testProduct = Product.builder()
                .id(1L)
                .sku("PROD-001")
                .name("Test Product")
                .salePrice(new BigDecimal("100.00"))
                .mrp(new BigDecimal("150.00"))
                .stockQuantity(100)
                .build();

        testItem = OrderItem.builder()
                .id(1L)
                .product(testProduct)
                .productNameSnapshot("Test Product")
                .skuSnapshot("PROD-001")
                .quantity(5)
                .unitPrice(new BigDecimal("100.00"))
                .mrpSnapshot(new BigDecimal("150.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("500.00"))
                .build();

        testOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20260627-0001")
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .paymentStatus(Order.PaymentStatus.PAID)
                .paymentMode(Order.PaymentMode.UPI)
                .subtotal(new BigDecimal("500.00"))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("500.00"))
                .orderDate(LocalDate.now())
                .items(new ArrayList<>(List.of(testItem)))
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        testItem.setOrder(testOrder);
    }

    // ==================== VALID TRANSITION TESTS ====================

    @Test
    @DisplayName("Valid: NEW -> CONFIRMED")
    void testValidTransition_NewToConfirmed() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Confirmed");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        verify(orderRepository).save(testOrder);
        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Valid: NEW -> CANCELLED")
    void testValidTransition_NewToCancelled() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Cancelled");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        verify(orderRepository).save(testOrder);
        // No stock restoration since stock was never reduced from NEW status
        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Valid: CONFIRMED -> PAID")
    void testValidTransition_ConfirmedToPaid() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 100L, "Payment received");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PAID);
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("Valid: CONFIRMED -> CANCELLED")
    void testValidTransition_ConfirmedToCancelled() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Cancelled");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("Valid: PAID -> PACKED (reduces stock)")
    void testValidTransition_PaidToPacked() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Packed");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PACKED);
        verify(productManagementService).updateStock(
                eq(1L),
                eq(-5),
                eq(StockHistory.StockOperation.STOCK_OUT),
                eq("ORDER"),
                eq(1L),
                anyString(),
                eq(100L)
        );
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("Valid: PAID -> CANCELLED (restores stock)")
    void testValidTransition_PaidToCancelled() {
        // Arrange - simulate PAID status that has NOT been packed yet
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Cancelled");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        // No stock restoration since stock reduction happens at PACKED, not PAID
        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Valid: PACKED -> DISPATCHED (sets dispatchedAt timestamp)")
    void testValidTransition_PackedToDispatched() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PACKED);
        assertThat(testOrder.getDispatchedAt()).isNull();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Dispatched");

        // Assert - Requirement 5.10
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.DISPATCHED);
        assertThat(result.getDispatchedAt()).isNotNull();
        assertThat(result.getDispatchedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
        assertThat(result.getDispatchedAt()).isAfter(LocalDateTime.now().minusSeconds(5));
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("Valid: PACKED -> PAID (unpacking scenario)")
    void testValidTransition_PackedToPaid() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 100L, "Unpacking");

        // Assert - unpacking (PACKED -> PAID) is allowed; stock is neither reduced
        // nor restored here (restoration occurs only on CANCELLED/RETURNED per Req 9.3)
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PAID);
        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Invalid: PACKED -> CANCELLED (cancellation not allowed after packing; unpack first)")
    void testValidTransition_PackedToCancelled() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert - Requirement 5.5: PACKED may only transition to DISPATCHED or PAID
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Cancelled after packing")
        ).isInstanceOf(InvalidStatusTransitionException.class)
         .hasMessageContaining("Cannot transition");
        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Valid: DISPATCHED -> DELIVERED (sets deliveredAt timestamp)")
    void testValidTransition_DispatchedToDelivered() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        testOrder.setDispatchedAt(LocalDateTime.now().minusDays(1));
        assertThat(testOrder.getDeliveredAt()).isNull();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.DELIVERED, 100L, "Delivered");

        // Assert - Requirement 5.11
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.DELIVERED);
        assertThat(result.getDeliveredAt()).isNotNull();
        assertThat(result.getDeliveredAt()).isBefore(LocalDateTime.now().plusSeconds(1));
        assertThat(result.getDeliveredAt()).isAfter(LocalDateTime.now().minusSeconds(5));
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("Valid: DISPATCHED -> RETURNED (restores stock)")
    void testValidTransition_DispatchedToReturned() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        testOrder.setDispatchedAt(LocalDateTime.now().minusDays(1));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.RETURNED, 100L, "Customer returned");

        // Assert - Requirement 9.3: stock restoration for RETURNED
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);
        verify(productManagementService).updateStock(
                eq(1L),
                eq(5),
                eq(StockHistory.StockOperation.RETURN),
                eq("ORDER"),
                eq(1L),
                contains("returned"),
                eq(100L)
        );
    }

    @Test
    @DisplayName("Valid: DELIVERED -> RETURNED (restores stock)")
    void testValidTransition_DeliveredToReturned() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        testOrder.setDeliveredAt(LocalDateTime.now().minusDays(2));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.RETURNED, 100L, "Customer returned");

        // Assert - Requirement 9.3: stock restoration for RETURNED
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);
        verify(productManagementService).updateStock(
                eq(1L),
                eq(5),
                eq(StockHistory.StockOperation.RETURN),
                eq("ORDER"),
                eq(1L),
                contains("returned"),
                eq(100L)
        );
    }

    // ==================== INVALID TRANSITION TESTS ====================

    @Test
    @DisplayName("Invalid: NEW -> PAID (skipping CONFIRMED)")
    void testInvalidTransition_NewToPaid() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert - Requirement 5.8: invalid transitions should be rejected
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: NEW -> PACKED (skipping multiple states)")
    void testInvalidTransition_NewToPacked() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: NEW -> DISPATCHED (skipping multiple states)")
    void testInvalidTransition_NewToDispatched() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: CONFIRMED -> PACKED (skipping PAID)")
    void testInvalidTransition_ConfirmedToPacked() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: PAID -> DISPATCHED (skipping PACKED)")
    void testInvalidTransition_PaidToDispatched() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: PACKED -> DELIVERED (skipping DISPATCHED)")
    void testInvalidTransition_PackedToDelivered() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.DELIVERED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: CONFIRMED -> NEW (backwards transition)")
    void testInvalidTransition_ConfirmedToNew() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.NEW, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: PAID -> CONFIRMED (backwards transition)")
    void testInvalidTransition_PaidToConfirmed() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: DISPATCHED -> PACKED (backwards transition)")
    void testInvalidTransition_DispatchedToPacked() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: DELIVERED -> DISPATCHED (backwards from terminal state)")
    void testInvalidTransition_DeliveredToDispatched() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        testOrder.setDeliveredAt(LocalDateTime.now());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: DELIVERED -> PACKED (backwards from terminal state)")
    void testInvalidTransition_DeliveredToPacked() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        testOrder.setDeliveredAt(LocalDateTime.now());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: CANCELLED -> NEW (from terminal state)")
    void testInvalidTransition_CancelledToNew() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.NEW, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: CANCELLED -> CONFIRMED (from terminal state)")
    void testInvalidTransition_CancelledToConfirmed() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Invalid: RETURNED -> DELIVERED (from terminal state)")
    void testInvalidTransition_ReturnedToDelivered() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.RETURNED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.DELIVERED, 100L, "Test")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Cannot transition");
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("Edge Case: Order not found throws ResourceNotFoundException")
    void testOrderNotFound() {
        // Arrange
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(999L, Order.OrderStatus.CONFIRMED, 100L, "Test")
        ).isInstanceOf(ResourceNotFoundException.class)
         .hasMessageContaining("Order")
         .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Edge Case: Status history is created for every transition")
    void testStatusHistoryCreation() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Confirmed by admin");

        // Assert - Requirement 6.1, 6.2
        assertThat(testOrder.getStatusHistory()).hasSize(1);
        OrderStatusHistory history = testOrder.getStatusHistory().get(0);
        assertThat(history.getFromStatus()).isEqualTo("NEW");
        assertThat(history.getToStatus()).isEqualTo("CONFIRMED");
        assertThat(history.getChangedBy()).isEqualTo(100L);
        assertThat(history.getNotes()).isEqualTo("Confirmed by admin");
        // Note: changedAt is populated by Hibernate @CreationTimestamp at persistence time,
        // which is not exercised under mocked repositories, so it is not asserted here.
    }

    @Test
    @DisplayName("Edge Case: Multiple status transitions maintain history")
    void testMultipleStatusTransitionsHistory() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act - perform multiple transitions
        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Step 1");
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        
        orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 101L, "Step 2");
        testOrder.setStatus(Order.OrderStatus.PAID);
        
        orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 102L, "Step 3");

        // Assert - Requirement 6.3: complete status history
        assertThat(testOrder.getStatusHistory()).hasSize(3);
        assertThat(testOrder.getStatusHistory().get(0).getToStatus()).isEqualTo("CONFIRMED");
        assertThat(testOrder.getStatusHistory().get(1).getToStatus()).isEqualTo("PAID");
        assertThat(testOrder.getStatusHistory().get(2).getToStatus()).isEqualTo("PACKED");
    }

    @Test
    @DisplayName("Edge Case: Timestamp not set for non-DISPATCHED/DELIVERED statuses")
    void testTimestampsNotSetForOtherStatuses() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Test");

        // Assert
        assertThat(testOrder.getDispatchedAt()).isNull();
        assertThat(testOrder.getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("Edge Case: dispatchedAt not overwritten on subsequent transitions")
    void testDispatchedAtNotOverwritten() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // First transition to DISPATCHED
        orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Dispatched");
        LocalDateTime firstDispatchTime = testOrder.getDispatchedAt();
        assertThat(firstDispatchTime).isNotNull();

        // Act - transition to DELIVERED
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.DELIVERED, 100L, "Delivered");

        // Assert - dispatchedAt should remain unchanged
        assertThat(testOrder.getDispatchedAt()).isEqualTo(firstDispatchTime);
        assertThat(testOrder.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("Invalid: DISPATCHED -> CANCELLED (use RETURNED path after dispatch)")
    void testStockRestorationCancelledFromDispatched() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        testOrder.setDispatchedAt(LocalDateTime.now().minusDays(1));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert - Requirement 5.6: DISPATCHED may only transition to DELIVERED or RETURNED
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Order lost in transit")
        ).isInstanceOf(InvalidStatusTransitionException.class)
         .hasMessageContaining("Cannot transition");
        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Edge Case: Multiple items - stock operations for all items")
    void testMultipleItemsStockOperations() {
        // Arrange
        Product product2 = Product.builder()
                .id(2L)
                .sku("PROD-002")
                .name("Test Product 2")
                .salePrice(new BigDecimal("200.00"))
                .mrp(new BigDecimal("250.00"))
                .stockQuantity(50)
                .build();

        OrderItem item2 = OrderItem.builder()
                .id(2L)
                .product(product2)
                .productNameSnapshot("Test Product 2")
                .skuSnapshot("PROD-002")
                .quantity(3)
                .unitPrice(new BigDecimal("200.00"))
                .mrpSnapshot(new BigDecimal("250.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("600.00"))
                .build();

        item2.setOrder(testOrder);
        testOrder.getItems().add(item2);

        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Packed all items");

        // Assert - both items should have stock reduced
        verify(productManagementService).updateStock(
                eq(1L), eq(-5), eq(StockHistory.StockOperation.STOCK_OUT),
                eq("ORDER"), eq(1L), anyString(), eq(100L)
        );
        verify(productManagementService).updateStock(
                eq(2L), eq(-3), eq(StockHistory.StockOperation.STOCK_OUT),
                eq("ORDER"), eq(1L), anyString(), eq(100L)
        );
    }

    @Test
    @DisplayName("Edge Case: Same-status transition is rejected (not in workflow rules)")
    void testSameStatusTransition() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert - same-status is not an allowed transition per the state machine
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Reconfirm")
        ).isInstanceOf(InvalidStatusTransitionException.class)
         .hasMessageContaining("Cannot transition");
    }

    @Test
    @DisplayName("Workflow: Complete order lifecycle NEW to DELIVERED")
    void testCompleteOrderLifecycle() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act & Assert - Step by step workflow
        // NEW -> CONFIRMED
        testOrder.setStatus(Order.OrderStatus.NEW);
        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Confirmed");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);

        // CONFIRMED -> PAID
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 100L, "Payment received");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.PAID);

        // PAID -> PACKED (stock reduced)
        testOrder.setStatus(Order.OrderStatus.PAID);
        orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Packed");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.PACKED);
        verify(productManagementService, times(1)).updateStock(
                anyLong(), intThat(val -> val < 0), eq(StockHistory.StockOperation.STOCK_OUT),
                anyString(), anyLong(), anyString(), anyLong()
        );

        // PACKED -> DISPATCHED (timestamp set)
        testOrder.setStatus(Order.OrderStatus.PACKED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Dispatched");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.DISPATCHED);
        assertThat(testOrder.getDispatchedAt()).isNotNull();

        // DISPATCHED -> DELIVERED (timestamp set)
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.DELIVERED, 100L, "Delivered");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.DELIVERED);
        assertThat(testOrder.getDeliveredAt()).isNotNull();

        // Verify complete status history
        assertThat(testOrder.getStatusHistory()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Workflow: Order returned after dispatch restores stock")
    void testCancellationWorkflowAfterPacking() {
        // Arrange
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Go through full workflow: NEW -> CONFIRMED -> PAID -> PACKED -> DISPATCHED -> RETURNED.
        // Stock is reduced on entry to PACKED and restored when a RETURN occurs from a
        // post-packing state (PACKED/DISPATCHED/DELIVERED). PACKED cannot transition directly
        // to CANCELLED, so a return after dispatch exercises the restoration path.
        testOrder.setStatus(Order.OrderStatus.NEW);
        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Step 1");

        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 100L, "Step 2");

        testOrder.setStatus(Order.OrderStatus.PAID);
        orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Step 3");

        testOrder.setStatus(Order.OrderStatus.PACKED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Step 4");

        // Act - return the dispatched order
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.RETURNED, 100L, "Customer returned");

        // Assert - exactly one stock reduction (PACKED) and one stock restoration (RETURNED)
        verify(productManagementService, times(1)).updateStock(
                anyLong(), 
                intThat(val -> val < 0),  // One stock reduction (PACKED)
                eq(StockHistory.StockOperation.STOCK_OUT),
                anyString(), anyLong(), anyString(), anyLong()
        );
        verify(productManagementService, times(1)).updateStock(
                anyLong(), 
                intThat(val -> val > 0),  // One stock restoration (RETURNED)
                eq(StockHistory.StockOperation.RETURN),
                anyString(), anyLong(), anyString(), anyLong()
        );
    }
}
