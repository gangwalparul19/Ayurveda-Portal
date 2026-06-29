package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.ManualOrderRequest;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.exception.InsufficientStockException;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for manual order creation in OrderService.
 * Tests Requirements 1.1, 1.2, 1.3, 4.1
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceManualOrderTest {

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
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer testCustomer;
    private Salesperson testSalesperson;
    private Product testProduct1;
    private Product testProduct2;
    private ManualOrderRequest validOrderRequest;

    @BeforeEach
    void setUp() {
        // Setup test customer
        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .city("Mumbai")
                .state("Maharashtra")
                .build();

        // Setup test salesperson
        testSalesperson = Salesperson.builder()
                .id(1L)
                .name("Jane Smith")
                .employeeCode("EMP001")
                .status(Salesperson.SalespersonStatus.ACTIVE)
                .build();

        // Setup test products
        testProduct1 = Product.builder()
                .id(1L)
                .sku("PROD001")
                .name("Ashwagandha Capsules")
                .salePrice(BigDecimal.valueOf(500.00))
                .mrp(BigDecimal.valueOf(600.00))
                .stockQuantity(100)
                .build();

        testProduct2 = Product.builder()
                .id(2L)
                .sku("PROD002")
                .name("Triphala Powder")
                .salePrice(BigDecimal.valueOf(300.00))
                .mrp(BigDecimal.valueOf(350.00))
                .stockQuantity(50)
                .build();

        // Setup valid order request
        validOrderRequest = ManualOrderRequest.builder()
                .customerId(1L)
                .salespersonId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .discountAmount(BigDecimal.valueOf(50.00))
                .taxAmount(BigDecimal.valueOf(75.00))
                .shippingCharge(BigDecimal.valueOf(25.00))
                .orderDate(LocalDate.now())
                .notes("Test order")
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(500.00))
                                .discount(BigDecimal.ZERO)
                                .taxAmount(BigDecimal.ZERO)
                                .build(),
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(2L)
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(300.00))
                                .discount(BigDecimal.ZERO)
                                .taxAmount(BigDecimal.ZERO)
                                .build()
                ))
                .build();
    }

    // ==================== SUCCESSFUL ORDER CREATION TESTS ====================

    @Test
    void testCreateManualOrder_WithValidData_Success() {
        // Arrange - Requirement 1.1, 1.2, 1.3, 4.1
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(testProduct2));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240627-0001");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        // Act
        OrderResponse result = orderService.createManualOrder(validOrderRequest, 1L);

        // Assert
        assertNotNull(result);
        assertEquals("ORD-20240627-0001", result.getOrderNumber());
        assertEquals(Order.OrderSource.MANUAL, result.getOrderSource());
        assertEquals(Order.OrderStatus.NEW, result.getStatus());
        assertEquals(Order.PaymentMode.COD, result.getPaymentMode());
        assertEquals(Order.PaymentStatus.PENDING, result.getPaymentStatus());
        
        // Verify customer
        assertNotNull(result.getCustomer());
        assertEquals("John Doe", result.getCustomer().getName());
        assertEquals("9876543210", result.getCustomer().getPhone());
        
        // Verify salesperson
        assertNotNull(result.getSalesperson());
        assertEquals("Jane Smith", result.getSalesperson().getName());
        assertEquals("EMP001", result.getSalesperson().getEmployeeCode());
        
        // Verify items
        assertEquals(2, result.getItems().size());
        
        // Verify totals (Requirement 4.1)
        // Subtotal = (2 * 500) + (1 * 300) = 1300
        // Total = 1300 - 50 + 75 + 25 = 1350
        assertEquals(0, BigDecimal.valueOf(1300.00).compareTo(result.getSubtotal()));
        assertEquals(0, BigDecimal.valueOf(50.00).compareTo(result.getDiscountAmount()));
        assertEquals(0, BigDecimal.valueOf(75.00).compareTo(result.getTaxAmount()));
        assertEquals(0, BigDecimal.valueOf(25.00).compareTo(result.getShippingCharge()));
        assertEquals(0, BigDecimal.valueOf(1350.00).compareTo(result.getTotalAmount()));

        // Verify method calls
        verify(customerRepository).findById(1L);
        verify(salespersonRepository).findById(1L);
        verify(productRepository).findById(1L);
        verify(productRepository).findById(2L);
        verify(orderNumberGenerator).generateOrderNumber();
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void testCreateManualOrder_WithoutSalesperson_Success() {
        // Arrange - Salesperson is optional
        ManualOrderRequest requestWithoutSalesperson = ManualOrderRequest.builder()
                .customerId(1L)
                .salespersonId(null)
                .paymentMode(Order.PaymentMode.UPI)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240627-0002");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(2L);
            return order;
        });

        // Act
        OrderResponse result = orderService.createManualOrder(requestWithoutSalesperson, 1L);

        // Assert
        assertNotNull(result);
        assertNull(result.getSalesperson());
        verify(salespersonRepository, never()).findById(any());
    }

    @Test
    void testCreateManualOrder_ProductSnapshotCreated_Success() {
        // Arrange - Requirement 1.3: Create OrderItems with product snapshots
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240627-0003");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(3L);
            return order;
        });

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // Act
        OrderResponse result = orderService.createManualOrder(request, 1L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        OrderResponse.OrderItemResponse item = result.getItems().get(0);
        assertEquals("Ashwagandha Capsules", item.getProductName());
        assertEquals("PROD001", item.getSku());
        assertEquals(0, BigDecimal.valueOf(500.00).compareTo(item.getUnitPrice()));
        assertEquals(0, BigDecimal.valueOf(600.00).compareTo(item.getMrp()));
    }

    // ==================== VALIDATION FAILURE TESTS ====================

    @Test
    void testCreateManualOrder_CustomerNotFound_ThrowsException() {
        // Arrange - Requirement 1.2: Validate customer exists
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(999L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.createManualOrder(request, 1L)
        );

        assertTrue(exception.getMessage().contains("Customer"));
        assertTrue(exception.getMessage().contains("999"));
        verify(customerRepository).findById(999L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testCreateManualOrder_NoItems_ThrowsException() {
        // Arrange - Requirement 1.1: Validate at least one order item is present
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList())
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createManualOrder(request, 1L)
        );

        assertTrue(exception.getMessage().contains("At least one order item is required"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testCreateManualOrder_InsufficientStock_ThrowsException() {
        // Arrange - Validate product stock availability
        testProduct1.setStockQuantity(1); // Only 1 in stock

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(5) // Requesting 5, but only 1 available
                                .build()
                ))
                .build();

        // Act & Assert
        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> orderService.createManualOrder(request, 1L)
        );

        assertTrue(exception.getMessage().contains("Ashwagandha Capsules"));
        assertTrue(exception.getMessage().contains("Available: 1"));
        assertTrue(exception.getMessage().contains("Required: 5"));
        assertEquals("Ashwagandha Capsules", exception.getProductName());
        assertEquals(1, exception.getAvailableStock());
        assertEquals(5, exception.getRequiredQuantity());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testCreateManualOrder_ProductNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(999L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.createManualOrder(request, 1L)
        );

        assertTrue(exception.getMessage().contains("Product"));
        assertTrue(exception.getMessage().contains("999"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testCreateManualOrder_SalespersonNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(salespersonRepository.findById(999L)).thenReturn(Optional.empty());

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .salespersonId(999L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> orderService.createManualOrder(request, 1L)
        );

        assertTrue(exception.getMessage().contains("Salesperson"));
        assertTrue(exception.getMessage().contains("999"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    // ==================== ORDER NUMBER UNIQUENESS TESTS ====================

    @Test
    void testCreateManualOrder_OrderNumberGenerated_Uniqueness() {
        // Arrange - Requirement 2.1, 2.2: Order number generation
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240627-0001");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // Act
        OrderResponse result = orderService.createManualOrder(request, 1L);

        // Assert
        assertNotNull(result.getOrderNumber());
        assertEquals("ORD-20240627-0001", result.getOrderNumber());
        verify(orderNumberGenerator).generateOrderNumber();
    }

    // ==================== TOTAL CALCULATION TESTS ====================

    @Test
    void testCreateManualOrder_TotalCalculation_Correct() {
        // Arrange - Requirement 4.1: Calculate order totals
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240627-0001");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .discountAmount(BigDecimal.valueOf(100.00))
                .taxAmount(BigDecimal.valueOf(50.00))
                .shippingCharge(BigDecimal.valueOf(30.00))
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(3)
                                .unitPrice(BigDecimal.valueOf(500.00))
                                .build()
                ))
                .build();

        // Act
        OrderResponse result = orderService.createManualOrder(request, 1L);

        // Assert
        // Subtotal = 3 * 500 = 1500
        // Total = 1500 - 100 + 50 + 30 = 1480
        assertEquals(0, BigDecimal.valueOf(1500.00).compareTo(result.getSubtotal()));
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(result.getDiscountAmount()));
        assertEquals(0, BigDecimal.valueOf(50.00).compareTo(result.getTaxAmount()));
        assertEquals(0, BigDecimal.valueOf(30.00).compareTo(result.getShippingCharge()));
        assertEquals(0, BigDecimal.valueOf(1480.00).compareTo(result.getTotalAmount()));
    }

    @Test
    void testCreateManualOrder_DefaultValues_Applied() {
        // Arrange - Test that default values are applied when not provided
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240627-0001");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                // No discount, tax, shipping provided
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(1)
                                .build()
                ))
                .build();

        // Act
        OrderResponse result = orderService.createManualOrder(request, 1L);

        // Assert - Defaults should be zero
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getDiscountAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTaxAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getShippingCharge()));
        assertEquals(Order.PaymentStatus.PENDING, result.getPaymentStatus());
    }
}
