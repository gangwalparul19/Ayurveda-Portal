package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.WhatsAppOrderRequest;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.exception.InsufficientStockException;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import com.ayurveda.platform.util.WhatsAppTextParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WhatsApp order creation in OrderService.
 * Tests Requirements 1.2, 1.3, 3.6
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceWhatsAppOrderTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SalespersonRepository salespersonRepository;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private ProductManagementService productManagementService;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private WhatsAppTextParser whatsAppParser;

    @Mock
    private CustomerService customerService;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer testCustomer;
    private Salesperson testSalesperson;
    private Product testProduct1;
    private Product testProduct2;
    private WhatsAppOrderRequest validWhatsAppRequest;
    private WhatsAppTextParser.ParsedWhatsAppOrder parsedOrder;

    @BeforeEach
    void setUp() {
        // Setup test customer
        testCustomer = Customer.builder()
                .id(1L)
                .name("Rajesh Kumar")
                .phone("9876543210")
                .email(null)
                .addressLine1("123 Main Street")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
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

        // Setup WhatsApp message
        String whatsappText = """
                Name: Rajesh Kumar
                Phone: 9876543210
                Address: 123 Main Street, Mumbai, 400001
                
                2 x Ashwagandha Capsules
                1 x Triphala Powder
                
                Payment: COD
                """;

        // Setup valid WhatsApp order request
        validWhatsAppRequest = WhatsAppOrderRequest.builder()
                .whatsappText(whatsappText)
                .salespersonId(1L)
                .build();

        // Setup parsed order
        WhatsAppTextParser.ParsedCustomer parsedCustomer = new WhatsAppTextParser.ParsedCustomer();
        parsedCustomer.setName("Rajesh Kumar");
        parsedCustomer.setPhone("9876543210");
        parsedCustomer.setAddress("123 Main Street, Mumbai");
        parsedCustomer.setPincode("400001");

        WhatsAppTextParser.ParsedItem item1 = WhatsAppTextParser.ParsedItem.builder()
                .rawText("Ashwagandha Capsules")
                .quantity(2)
                .matchedProductId(1L)
                .matchedProductName("Ashwagandha Capsules")
                .confidence(1.0)
                .build();

        WhatsAppTextParser.ParsedItem item2 = WhatsAppTextParser.ParsedItem.builder()
                .rawText("Triphala Powder")
                .quantity(1)
                .matchedProductId(2L)
                .matchedProductName("Triphala Powder")
                .confidence(1.0)
                .build();

        WhatsAppTextParser.ParsedPayment parsedPayment = WhatsAppTextParser.ParsedPayment.builder()
                .paymentMode("COD")
                .amount(null)
                .build();

        parsedOrder = WhatsAppTextParser.ParsedWhatsAppOrder.builder()
                .customer(parsedCustomer)
                .items(Arrays.asList(item1, item2))
                .payment(parsedPayment)
                .warnings(List.of())
                .confidenceScore(0.95)
                .rawText(whatsappText)
                .build();
    }

    /**
     * Test successful WhatsApp order creation.
     * Validates Requirements 1.2, 1.3, 3.6
     */
    @Test
    void testCreateWhatsAppOrder_Success() {
        // Arrange
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(customerService.findOrCreateCustomer(anyString(), anyString(), any(), anyString(), 
                any(), any(), anyString())).thenReturn(testCustomer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(testProduct2));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240101-0001");
        
        Order savedOrder = createMockOrder();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse result = orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);

        // Assert
        assertNotNull(result);
        assertEquals("ORD-20240101-0001", result.getOrderNumber());
        assertEquals(Order.OrderSource.WHATSAPP, result.getOrderSource());
        assertEquals(Order.OrderStatus.NEW, result.getStatus());
        assertEquals(2, result.getItems().size());
        assertEquals(Order.PaymentMode.COD, result.getPaymentMode());
        
        // Verify parser was called
        verify(whatsAppParser).parseWhatsAppMessage(validWhatsAppRequest.getWhatsappText());
        
        // Verify customer was found/created
        verify(customerService).findOrCreateCustomer(
                eq("Rajesh Kumar"), eq("9876543210"), any(), anyString(), any(), any(), eq("400001"));
        
        // Verify products were validated
        verify(productRepository).findById(1L);
        verify(productRepository).findById(2L);
        
        // Verify order was saved
        verify(orderRepository).save(any(Order.class));
    }

    /**
     * Test WhatsApp order creation with manual customer override.
     * Validates that manual overrides are applied correctly.
     */
    @Test
    void testCreateWhatsAppOrder_WithCustomerOverride() {
        // Arrange
        WhatsAppOrderRequest.CustomerOverride customerOverride = WhatsAppOrderRequest.CustomerOverride.builder()
                .name("Rajesh Kumar Sharma")
                .phone("9876543211")
                .address("456 New Street, Mumbai")
                .pincode("400002")
                .build();
        
        validWhatsAppRequest.setCustomerOverride(customerOverride);
        
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(customerService.findOrCreateCustomer(anyString(), anyString(), any(), anyString(), 
                any(), any(), anyString())).thenReturn(testCustomer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(testProduct2));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240101-0002");
        
        Order savedOrder = createMockOrder();
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse result = orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);

        // Assert
        assertNotNull(result);
        
        // Verify customer override was applied
        verify(customerService).findOrCreateCustomer(
                eq("Rajesh Kumar Sharma"), eq("9876543211"), any(), 
                eq("456 New Street, Mumbai"), any(), any(), eq("400002"));
    }

    /**
     * Test WhatsApp order creation with items override.
     * Validates that manual item overrides replace parsed items.
     */
    @Test
    void testCreateWhatsAppOrder_WithItemsOverride() {
        // Arrange
        WhatsAppOrderRequest.OrderItemOverride itemOverride = WhatsAppOrderRequest.OrderItemOverride.builder()
                .productId(1L)
                .quantity(5)
                .build();
        
        validWhatsAppRequest.setItemsOverride(List.of(itemOverride));
        
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(customerService.findOrCreateCustomer(anyString(), anyString(), any(), anyString(), 
                any(), any(), anyString())).thenReturn(testCustomer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240101-0003");
        
        // Create mock order with only 1 item (from override)
        Order savedOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20240101-0003")
                .customer(testCustomer)
                .salespersonId(testSalesperson.getId())
                .orderSource(Order.OrderSource.WHATSAPP)
                .status(Order.OrderStatus.NEW)
                .orderDate(LocalDate.now())
                .paymentMode(Order.PaymentMode.COD)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .subtotal(BigDecimal.valueOf(2500.00))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(2500.00))
                .rawWhatsappText(validWhatsAppRequest.getWhatsappText())
                .build();
        
        OrderItem item1 = OrderItem.builder()
                .id(1L)
                .product(testProduct1)
                .productNameSnapshot("Ashwagandha Capsules")
                .skuSnapshot("PROD001")
                .quantity(5)
                .unitPrice(BigDecimal.valueOf(500.00))
                .mrpSnapshot(BigDecimal.valueOf(600.00))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(BigDecimal.valueOf(2500.00))
                .build();
        savedOrder.getItems().add(item1);
        
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse result = orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getItems().size()); // Only 1 item from override
        
        // Verify only the overridden product was fetched
        verify(productRepository, times(1)).findById(1L);
        verify(productRepository, never()).findById(2L);
    }

    /**
     * Test WhatsApp order creation fails when salesperson not found.
     * Validates Requirement: Salesperson validation
     */
    @Test
    void testCreateWhatsAppOrder_SalespersonNotFound() {
        // Arrange
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);
        });
        
        verify(salespersonRepository).findById(1L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    /**
     * Test WhatsApp order creation fails when no items parsed.
     * Validates Requirement 1.1: At least one order item required
     */
    @Test
    void testCreateWhatsAppOrder_NoItemsParsed() {
        // Arrange
        parsedOrder.setItems(List.of()); // Empty items list
        
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(customerService.findOrCreateCustomer(anyString(), anyString(), any(), anyString(), 
                any(), any(), anyString())).thenReturn(testCustomer);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);
        });
        
        assertTrue(exception.getMessage().contains("At least one order item is required"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    /**
     * Test WhatsApp order creation fails when product not found.
     * Validates Requirement: Product validation
     */
    @Test
    void testCreateWhatsAppOrder_ProductNotFound() {
        // Arrange
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(customerService.findOrCreateCustomer(anyString(), anyString(), any(), anyString(), 
                any(), any(), anyString())).thenReturn(testCustomer);
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);
        });
        
        verify(productRepository).findById(1L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    /**
     * Test WhatsApp order creation fails when insufficient stock.
     * Validates stock availability check
     */
    @Test
    void testCreateWhatsAppOrder_InsufficientStock() {
        // Arrange
        testProduct1.setStockQuantity(1); // Only 1 in stock, but order needs 2
        
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(customerService.findOrCreateCustomer(anyString(), anyString(), any(), anyString(), 
                any(), any(), anyString())).thenReturn(testCustomer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));

        // Act & Assert
        assertThrows(InsufficientStockException.class, () -> {
            orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);
        });
        
        verify(orderRepository, never()).save(any(Order.class));
    }

    /**
     * Test WhatsApp order creation stores raw WhatsApp text.
     * Validates Requirement 1.3: Store original WhatsApp message
     */
    @Test
    void testCreateWhatsAppOrder_StoresRawWhatsAppText() {
        // Arrange
        when(whatsAppParser.parseWhatsAppMessage(anyString())).thenReturn(parsedOrder);
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(customerService.findOrCreateCustomer(anyString(), anyString(), any(), anyString(), 
                any(), any(), anyString())).thenReturn(testCustomer);
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct1));
        when(productRepository.findById(2L)).thenReturn(Optional.of(testProduct2));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240101-0004");
        
        Order savedOrder = createMockOrder();
        savedOrder.setRawWhatsappText(validWhatsAppRequest.getWhatsappText());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderResponse result = orderService.createWhatsAppOrder(validWhatsAppRequest, 123L);

        // Assert
        assertNotNull(result);
        
        // Verify order save was called with raw WhatsApp text
        verify(orderRepository).save(argThat(order -> 
                order.getRawWhatsappText() != null && 
                order.getRawWhatsappText().equals(validWhatsAppRequest.getWhatsappText())
        ));
    }

    /**
     * Helper method to create a mock Order entity for testing.
     */
    private Order createMockOrder() {
        Order order = Order.builder()
                .id(1L)
                .orderNumber("ORD-20240101-0001")
                .customer(testCustomer)
                .salespersonId(testSalesperson.getId())
                .orderSource(Order.OrderSource.WHATSAPP)
                .status(Order.OrderStatus.NEW)
                .orderDate(LocalDate.now())
                .paymentMode(Order.PaymentMode.COD)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .subtotal(BigDecimal.valueOf(1300.00))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(1300.00))
                .rawWhatsappText(validWhatsAppRequest.getWhatsappText())
                .build();
        
        // Create order items
        OrderItem item1 = OrderItem.builder()
                .id(1L)
                .product(testProduct1)
                .productNameSnapshot("Ashwagandha Capsules")
                .skuSnapshot("PROD001")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(500.00))
                .mrpSnapshot(BigDecimal.valueOf(600.00))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(BigDecimal.valueOf(1000.00))
                .build();
        
        OrderItem item2 = OrderItem.builder()
                .id(2L)
                .product(testProduct2)
                .productNameSnapshot("Triphala Powder")
                .skuSnapshot("PROD002")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(300.00))
                .mrpSnapshot(BigDecimal.valueOf(350.00))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(BigDecimal.valueOf(300.00))
                .build();
        
        order.getItems().add(item1);
        order.getItems().add(item2);
        
        return order;
    }
}
