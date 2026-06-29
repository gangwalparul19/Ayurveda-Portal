package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.InsufficientStockException;
import com.ayurveda.platform.exception.InvalidStatusTransitionException;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.AdditionalMatchers.lt;

/**
 * Unit tests for OrderService.updateOrderStatus() method with stock management.
 * Tests Requirements 5.9, 5.10, 5.11, 6.1, 6.2, 6.3, 9.2, 9.3
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service - Status Transition Tests")
class OrderServiceStatusTransitionTest {

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

    private Order testOrder;
    private Customer testCustomer;
    private Product testProduct1;
    private Product testProduct2;
    private OrderItem testItem1;
    private OrderItem testItem2;

    @BeforeEach
    void setUp() {
        // Setup test customer
        testCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .phone("9876543210")
                .addressLine1("123 Test Street")
                .city("Test City")
                .state("Test State")
                .pincode("123456")
                .build();

        // Setup test products
        testProduct1 = Product.builder()
                .id(1L)
                .sku("PROD-001")
                .name("Product 1")
                .salePrice(new BigDecimal("100.00"))
                .mrp(new BigDecimal("150.00"))
                .stockQuantity(100)
                .build();

        testProduct2 = Product.builder()
                .id(2L)
                .sku("PROD-002")
                .name("Product 2")
                .salePrice(new BigDecimal("200.00"))
                .mrp(new BigDecimal("250.00"))
                .stockQuantity(50)
                .build();

        // Setup order items
        testItem1 = OrderItem.builder()
                .id(1L)
                .product(testProduct1)
                .productNameSnapshot("Product 1")
                .skuSnapshot("PROD-001")
                .quantity(5)
                .unitPrice(new BigDecimal("100.00"))
                .mrpSnapshot(new BigDecimal("150.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("500.00"))
                .build();

        testItem2 = OrderItem.builder()
                .id(2L)
                .product(testProduct2)
                .productNameSnapshot("Product 2")
                .skuSnapshot("PROD-002")
                .quantity(3)
                .unitPrice(new BigDecimal("200.00"))
                .mrpSnapshot(new BigDecimal("250.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("600.00"))
                .build();

        // Setup test order
        testOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20260627-0001")
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .paymentStatus(Order.PaymentStatus.PAID)
                .paymentMode(Order.PaymentMode.UPI)
                .subtotal(new BigDecimal("1100.00"))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1100.00"))
                .orderDate(LocalDate.now())
                .items(new ArrayList<>(List.of(testItem1, testItem2)))
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        // Set bidirectional relationship
        testItem1.setOrder(testOrder);
        testItem2.setOrder(testOrder);
    }

    @Test
    @DisplayName("Test transition to PACKED - should reduce stock for all items")
    void testTransitionToPacked_ShouldReduceStock() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Packing complete");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PACKED);
        
        // Verify stock reduction for both products (Requirement 9.2)
        verify(productManagementService).updateStock(
                eq(1L),
                eq(-5),  // Negative for reduction
                eq(StockHistory.StockOperation.STOCK_OUT),
                eq("ORDER"),
                eq(1L),
                contains("ORD-20260627-0001"),
                eq(100L)
        );
        
        verify(productManagementService).updateStock(
                eq(2L),
                eq(-3),  // Negative for reduction
                eq(StockHistory.StockOperation.STOCK_OUT),
                eq("ORDER"),
                eq(1L),
                contains("ORD-20260627-0001"),
                eq(100L)
        );

        // Verify order saved
        verify(orderRepository).save(testOrder);
    }

    @Test
    @DisplayName("Invalid: CANCELLED from PACKED is rejected (must unpack first)")
    void testTransitionToCancelledFromPacked_ShouldRestoreStock() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert - Requirement 5.5: PACKED may only transition to DISPATCHED or PAID
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Customer cancelled")
        ).isInstanceOf(InvalidStatusTransitionException.class);

        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Invalid: CANCELLED from DISPATCHED is rejected (use RETURNED path)")
    void testTransitionToCancelledFromDispatched_ShouldRestoreStock() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        testOrder.setDispatchedAt(LocalDateTime.now());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // Act & Assert - Requirement 5.6: DISPATCHED may only transition to DELIVERED or RETURNED
        assertThatThrownBy(() ->
            orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Delivery failed")
        ).isInstanceOf(InvalidStatusTransitionException.class);

        verify(productManagementService, never()).updateStock(anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Test transition to RETURNED from DELIVERED - should restore stock")
    void testTransitionToReturnedFromDelivered_ShouldRestoreStock() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        testOrder.setDeliveredAt(LocalDateTime.now());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.RETURNED, 100L, "Customer returned items");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);
        
        // Verify stock restoration for both products (Requirement 9.3)
        verify(productManagementService).updateStock(
                eq(1L),
                eq(5),  // Positive for restoration
                eq(StockHistory.StockOperation.RETURN),
                eq("ORDER"),
                eq(1L),
                contains("returned"),
                eq(100L)
        );
        
        verify(productManagementService).updateStock(
                eq(2L),
                eq(3),  // Positive for restoration
                eq(StockHistory.StockOperation.RETURN),
                eq("ORDER"),
                eq(1L),
                contains("returned"),
                eq(100L)
        );
    }

    @Test
    @DisplayName("Test transition to CANCELLED from NEW - should NOT restore stock")
    void testTransitionToCancelledFromNew_ShouldNotRestoreStock() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, 100L, "Cancelled before packing");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        
        // Verify NO stock operations (stock was never reduced)
        verify(productManagementService, never()).updateStock(
                anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong()
        );
    }

    @Test
    @DisplayName("Test transition to DISPATCHED - should set dispatchedAt timestamp")
    void testTransitionToDispatched_ShouldSetTimestamp() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.DISPATCHED, 100L, "Order dispatched");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.DISPATCHED);
        assertThat(result.getDispatchedAt()).isNotNull();
        assertThat(result.getDispatchedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
        assertThat(result.getDispatchedAt()).isAfter(LocalDateTime.now().minusSeconds(5));
    }

    @Test
    @DisplayName("Test transition to DELIVERED - should set deliveredAt timestamp")
    void testTransitionToDelivered_ShouldSetTimestamp() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        testOrder.setDispatchedAt(LocalDateTime.now().minusDays(1));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.DELIVERED, 100L, "Order delivered");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.DELIVERED);
        assertThat(result.getDeliveredAt()).isNotNull();
        assertThat(result.getDeliveredAt()).isBefore(LocalDateTime.now().plusSeconds(1));
        assertThat(result.getDeliveredAt()).isAfter(LocalDateTime.now().minusSeconds(5));
    }

    @Test
    @DisplayName("Test status history is created for transition")
    void testStatusHistoryCreated() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Order confirmed by customer");

        // Assert - verify status history was added
        assertThat(testOrder.getStatusHistory()).isNotEmpty();
        OrderStatusHistory history = testOrder.getStatusHistory().get(0);
        assertThat(history.getFromStatus()).isEqualTo("NEW");
        assertThat(history.getToStatus()).isEqualTo("CONFIRMED");
        assertThat(history.getChangedBy()).isEqualTo(100L);
        assertThat(history.getNotes()).isEqualTo("Order confirmed by customer");
    }

    @Test
    @DisplayName("Test order not found throws exception")
    void testOrderNotFound_ThrowsException() {
        // Arrange
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(999L, Order.OrderStatus.CONFIRMED, 100L, "Test")
        )
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Order")
        .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Test insufficient stock during PACKED transition throws exception")
    void testInsufficientStock_ThrowsException() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        
        // Mock stock service to throw InsufficientStockException
        doThrow(new InsufficientStockException("Product 1", "PROD-001", 2, 5))
                .when(productManagementService).updateStock(
                        eq(1L), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong()
                );

        // Act & Assert
        assertThatThrownBy(() -> 
            orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Test")
        )
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Product 1");
    }

    @Test
    @DisplayName("Test transition to CONFIRMED - no stock operations")
    void testTransitionToConfirmed_NoStockOperations() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Confirmed by admin");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        
        // Verify NO stock operations for CONFIRMED status
        verify(productManagementService, never()).updateStock(
                anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong()
        );
    }

    @Test
    @DisplayName("Test transition to PAID - no stock operations")
    void testTransitionToPaid_NoStockOperations() {
        // Arrange
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // Act
        Order result = orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 100L, "Payment received");

        // Assert
        assertThat(result.getStatus()).isEqualTo(Order.OrderStatus.PAID);
        
        // Verify NO stock operations for PAID status
        verify(productManagementService, never()).updateStock(
                anyLong(), anyInt(), any(), anyString(), anyLong(), anyString(), anyLong()
        );
    }

    @Test
    @DisplayName("Test complete workflow NEW -> CONFIRMED -> PAID -> PACKED")
    void testCompleteWorkflow_WithStockReduction() {
        // Test NEW -> CONFIRMED
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        
        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, 100L, "Step 1");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);

        // Test CONFIRMED -> PAID
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        orderService.updateOrderStatus(1L, Order.OrderStatus.PAID, 100L, "Step 2");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.PAID);

        // Test PAID -> PACKED (should reduce stock)
        testOrder.setStatus(Order.OrderStatus.PAID);
        orderService.updateOrderStatus(1L, Order.OrderStatus.PACKED, 100L, "Step 3");
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.PACKED);

        // Verify stock was reduced only for PACKED transition
        verify(productManagementService, times(2)).updateStock(
                anyLong(),
                intThat(value -> value < 0),  // Negative values for reduction
                eq(StockHistory.StockOperation.STOCK_OUT),
                eq("ORDER"),
                eq(1L),
                anyString(),
                eq(100L)
        );
    }
}
