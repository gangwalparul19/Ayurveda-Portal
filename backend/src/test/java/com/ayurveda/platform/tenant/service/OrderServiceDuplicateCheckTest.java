package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.DuplicateCheckResult;
import com.ayurveda.platform.master.service.ConfigurationService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for duplicate order detection in OrderService.
 * Tests Requirements 11.1, 11.2, 11.3, 11.4, 11.5
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceDuplicateCheckTest {

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
    private ProductManagementService productManagementService;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private WhatsAppTextParser whatsAppParser;

    @Mock
    private CustomerService customerService;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer testCustomer;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        // Setup test customer
        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .city("Mumbai")
                .state("Maharashtra")
                .build();

        // Setup test products
        product1 = Product.builder()
                .id(1L)
                .sku("PROD001")
                .name("Ashwagandha Capsules")
                .salePrice(BigDecimal.valueOf(500.00))
                .mrp(BigDecimal.valueOf(600.00))
                .stockQuantity(100)
                .build();

        product2 = Product.builder()
                .id(2L)
                .sku("PROD002")
                .name("Triphala Powder")
                .salePrice(BigDecimal.valueOf(300.00))
                .mrp(BigDecimal.valueOf(350.00))
                .stockQuantity(50)
                .build();

        product3 = Product.builder()
                .id(3L)
                .sku("PROD003")
                .name("Brahmi Tablets")
                .salePrice(BigDecimal.valueOf(400.00))
                .mrp(BigDecimal.valueOf(450.00))
                .stockQuantity(75)
                .build();

        // Note: Configure duplicate check days in individual tests as needed
    }

    /**
     * Test Requirement 11.5: When no matching customer exists, system should indicate no duplicates found.
     */
    @Test
    void testCheckDuplicate_NoCustomerExists_ReturnsNoDuplicates() {
        // Arrange
        String customerPhone = "9876543210";
        List<Long> productIds = Arrays.asList(1L, 2L);
        LocalDate orderDate = LocalDate.now();

        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.empty());

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert
        assertNotNull(result);
        assertFalse(result.isHasDuplicates());
        assertEquals(0, result.getPotentialDuplicates().size());
        assertEquals(customerPhone, result.getCustomerPhone());

        verify(customerRepository).findByPhone(customerPhone);
        verify(orderRepository, never()).findByCustomerIdAndOrderDateBetween(anyLong(), any(), any());
    }

    /**
     * Test Requirement 11.1: Search for orders from same customer within 7 days.
     */
    @Test
    void testCheckDuplicate_NoOrdersInTimeWindow_ReturnsNoDuplicates() {
        // Arrange
        String customerPhone = "9876543210";
        List<Long> productIds = Arrays.asList(1L, 2L);
        LocalDate orderDate = LocalDate.now();

        when(configurationService.getDuplicateCheckDays()).thenReturn(7);
        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                eq(testCustomer.getId()),
                eq(orderDate.minusDays(7)),
                eq(orderDate.plusDays(7))
        )).thenReturn(new ArrayList<>());

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert
        assertNotNull(result);
        assertFalse(result.isHasDuplicates());
        assertEquals(0, result.getPotentialDuplicates().size());

        verify(customerRepository).findByPhone(customerPhone);
        verify(orderRepository).findByCustomerIdAndOrderDateBetween(
                testCustomer.getId(),
                orderDate.minusDays(7),
                orderDate.plusDays(7)
        );
    }

    /**
     * Test Requirement 11.3: Flag orders with 80% or greater product overlap.
     * This test creates an order with 100% product overlap (exact duplicate).
     */
    @Test
    void testCheckDuplicate_ExactDuplicate_100PercentSimilarity() {
        // Arrange
        String customerPhone = "9876543210";
        List<Long> productIds = Arrays.asList(1L, 2L, 3L);
        LocalDate orderDate = LocalDate.now();

        when(configurationService.getDuplicateCheckDays()).thenReturn(7);
        
        // Create existing order with same products
        Order existingOrder = createOrder("ORD-20260628-0001", orderDate.minusDays(2), 
                Arrays.asList(product1, product2, product3));

        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                eq(testCustomer.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(Arrays.asList(existingOrder));

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert
        assertNotNull(result);
        assertTrue(result.isHasDuplicates());
        assertEquals(1, result.getPotentialDuplicates().size());

        DuplicateCheckResult.DuplicateOrderInfo duplicate = result.getPotentialDuplicates().get(0);
        assertEquals(existingOrder.getOrderNumber(), duplicate.getOrderNumber());
        assertEquals(1.0, duplicate.getSimilarityScore(), 0.001); // 100% similarity
        assertEquals(2L, duplicate.getDaysDifference());
        assertEquals(3, duplicate.getCommonProductIds().size());
    }

    /**
     * Test Requirement 11.3: Flag orders with exactly 80% similarity (threshold).
     */
    @Test
    void testCheckDuplicate_80PercentSimilarity_Flagged() {
        // Arrange
        String customerPhone = "9876543210";
        // New order has products 1, 2, 3, 4, 5 (5 products)
        List<Long> productIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        LocalDate orderDate = LocalDate.now();

        when(configurationService.getDuplicateCheckDays()).thenReturn(7);

        // Existing order has products 1, 2, 3, 4 (4 products)
        // Intersection: {1, 2, 3, 4} = 4 products
        // Union: {1, 2, 3, 4, 5} = 5 products
        // Jaccard = 4/5 = 0.80 = 80%
        Order existingOrder = createOrder("ORD-20260628-0001", orderDate.minusDays(3),
                Arrays.asList(product1, product2, product3, createProduct(4L)));

        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(anyLong(), any(), any()))
                .thenReturn(Arrays.asList(existingOrder));

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert
        assertNotNull(result);
        assertTrue(result.isHasDuplicates());
        assertEquals(1, result.getPotentialDuplicates().size());

        DuplicateCheckResult.DuplicateOrderInfo duplicate = result.getPotentialDuplicates().get(0);
        assertEquals(0.80, duplicate.getSimilarityScore(), 0.001);
        assertEquals(4, duplicate.getCommonProductIds().size());
    }

    /**
     * Test Requirement 11.3: Do not flag orders with less than 80% similarity.
     */
    @Test
    void testCheckDuplicate_Below80PercentSimilarity_NotFlagged() {
        // Arrange
        String customerPhone = "9876543210";
        // New order has products 1, 2, 3, 4, 5, 6 (6 products)
        List<Long> productIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L);
        LocalDate orderDate = LocalDate.now();

        when(configurationService.getDuplicateCheckDays()).thenReturn(7);

        // Existing order has products 1, 2, 3, 4 (4 products)
        // Intersection: {1, 2, 3, 4} = 4 products
        // Union: {1, 2, 3, 4, 5, 6} = 6 products
        // Jaccard = 4/6 = 0.67 = 67% (below threshold)
        Order existingOrder = createOrder("ORD-20260628-0001", orderDate.minusDays(3),
                Arrays.asList(product1, product2, product3, createProduct(4L)));

        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(anyLong(), any(), any()))
                .thenReturn(Arrays.asList(existingOrder));

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert
        assertNotNull(result);
        assertFalse(result.isHasDuplicates());
        assertEquals(0, result.getPotentialDuplicates().size());
    }

    /**
     * Test Requirement 11.2: Calculate product overlap using Jaccard similarity.
     * Test various similarity scores.
     */
    @Test
    void testCheckDuplicate_PartialOverlap_CorrectSimilarity() {
        // Arrange
        String customerPhone = "9876543210";
        // New order has products 1, 2 (2 products)
        List<Long> productIds = Arrays.asList(1L, 2L);
        LocalDate orderDate = LocalDate.now();

        when(configurationService.getDuplicateCheckDays()).thenReturn(7);

        // Existing order has products 2, 3 (2 products)
        // Intersection: {2} = 1 product
        // Union: {1, 2, 3} = 3 products
        // Jaccard = 1/3 = 0.33 = 33%
        Order existingOrder = createOrder("ORD-20260628-0001", orderDate.minusDays(1),
                Arrays.asList(product2, product3));

        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(anyLong(), any(), any()))
                .thenReturn(Arrays.asList(existingOrder));

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert - Should not be flagged (below 80%)
        assertFalse(result.isHasDuplicates());
    }

    /**
     * Test Requirement 11.4: Return list of potential duplicate orders with similarity scores.
     */
    @Test
    void testCheckDuplicate_MultipleDuplicates_ReturnsAll() {
        // Arrange
        String customerPhone = "9876543210";
        List<Long> productIds = Arrays.asList(1L, 2L, 3L);
        LocalDate orderDate = LocalDate.now();

        when(configurationService.getDuplicateCheckDays()).thenReturn(7);

        // Create multiple orders with high similarity
        Order duplicate1 = createOrder("ORD-20260628-0001", orderDate.minusDays(2),
                Arrays.asList(product1, product2, product3)); // 100% similarity

        Order duplicate2 = createOrder("ORD-20260628-0002", orderDate.minusDays(5),
                Arrays.asList(product1, product2)); // 67% similarity (2/3) - won't be flagged

        Order duplicate3 = createOrder("ORD-20260628-0003", orderDate.plusDays(3),
                Arrays.asList(product1, product2, product3, createProduct(4L))); // 75% similarity (3/4) - won't be flagged

        // This order has products 1,2,3,4,5 with intersection 1,2,3 = 3/5 = 60% - won't be flagged
        Order duplicate4 = createOrder("ORD-20260628-0004", orderDate.minusDays(1),
                Arrays.asList(product1, product2, product3)); // 100% similarity

        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(anyLong(), any(), any()))
                .thenReturn(Arrays.asList(duplicate1, duplicate2, duplicate3, duplicate4));

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert - Only orders with >= 80% similarity should be flagged
        assertTrue(result.isHasDuplicates());
        // duplicate1 (100%) and duplicate4 (100%) should be flagged
        assertEquals(2, result.getPotentialDuplicates().size());

        // Verify order numbers of duplicates
        List<String> duplicateOrderNumbers = result.getPotentialDuplicates().stream()
                .map(DuplicateCheckResult.DuplicateOrderInfo::getOrderNumber)
                .toList();
        assertTrue(duplicateOrderNumbers.contains("ORD-20260628-0001"));
        assertTrue(duplicateOrderNumbers.contains("ORD-20260628-0004"));
    }

    /**
     * Test Requirement 11.1: Orders outside 7-day window should be ignored.
     */
    @Test
    void testCheckDuplicate_OrderOutsideTimeWindow_Ignored() {
        // Arrange
        String customerPhone = "9876543210";
        List<Long> productIds = Arrays.asList(1L, 2L, 3L);
        LocalDate orderDate = LocalDate.now();

        // Configure 7-day window
        when(configurationService.getDuplicateCheckDays()).thenReturn(7);

        // Order is 8 days old - outside 7-day window
        // Repository should not return this order based on date range query
        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                eq(testCustomer.getId()),
                eq(orderDate.minusDays(7)),
                eq(orderDate.plusDays(7))
        )).thenReturn(new ArrayList<>()); // Empty - order is outside window

        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert
        assertFalse(result.isHasDuplicates());
        assertEquals(0, result.getPotentialDuplicates().size());

        // Verify date range is correct
        verify(orderRepository).findByCustomerIdAndOrderDateBetween(
                testCustomer.getId(),
                orderDate.minusDays(7),
                orderDate.plusDays(7)
        );
    }

    /**
     * Test that configuration service provides correct duplicate check days.
     */
    @Test
    void testCheckDuplicate_UsesConfiguredDuplicateCheckDays() {
        // Arrange
        String customerPhone = "9876543210";
        List<Long> productIds = Arrays.asList(1L, 2L);
        LocalDate orderDate = LocalDate.now();

        // Configure custom duplicate check days
        when(configurationService.getDuplicateCheckDays()).thenReturn(14); // 14 days instead of 7

        when(customerRepository.findByPhone(customerPhone)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findByCustomerIdAndOrderDateBetween(anyLong(), any(), any()))
                .thenReturn(new ArrayList<>());

        // Act
        orderService.checkDuplicate(customerPhone, productIds, orderDate);

        // Assert - Verify 14-day window is used
        verify(configurationService).getDuplicateCheckDays();
        verify(orderRepository).findByCustomerIdAndOrderDateBetween(
                testCustomer.getId(),
                orderDate.minusDays(14),
                orderDate.plusDays(14)
        );
    }

    // --- Helper methods ---

    private Order createOrder(String orderNumber, LocalDate orderDate, List<Product> products) {
        Order order = Order.builder()
                .id((long) (Math.random() * 1000))
                .orderNumber(orderNumber)
                .orderDate(orderDate)
                .customer(testCustomer)
                .status(Order.OrderStatus.NEW)
                .totalAmount(BigDecimal.valueOf(1000.00))
                .items(new ArrayList<>())
                .build();

        for (Product product : products) {
            OrderItem item = OrderItem.builder()
                    .id((long) (Math.random() * 1000))
                    .order(order)
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .skuSnapshot(product.getSku())
                    .quantity(1)
                    .unitPrice(product.getSalePrice())
                    .build();
            order.getItems().add(item);
        }

        return order;
    }

    private Product createProduct(Long id) {
        return Product.builder()
                .id(id)
                .sku("PROD" + String.format("%03d", id))
                .name("Product " + id)
                .salePrice(BigDecimal.valueOf(100.00 * id))
                .mrp(BigDecimal.valueOf(120.00 * id))
                .stockQuantity(100)
                .build();
    }
}
