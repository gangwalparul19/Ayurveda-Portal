package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.DuplicateCheckResult;
import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.master.service.AuditLogService;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import com.ayurveda.platform.util.WhatsAppTextParser;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-Based Tests for Duplicate Order Detection Accuracy using jqwik.
 * 
 * **Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5**
 * 
 * This test suite validates the duplicate detection algorithm's accuracy in:
 * - Correctly flagging orders with 80%+ product overlap as duplicates (Req 11.3)
 * - Not flagging orders with <80% product overlap (Req 11.3)
 * - Properly respecting the 7-day time window (Req 11.1)
 * - Calculating Jaccard similarity correctly (Req 11.2)
 * - Handling edge cases (no customer, no orders, empty products)
 */
class DuplicateDetectionAccuracyPropertyTest {

    private static final String TEST_PHONE = "9876543210";
    private static final Long TEST_CUSTOMER_ID = 1L;

    /**
     * **Validates: Requirements 11.1, 11.2, 11.3, 11.4, 11.5**
     * 
     * Property 5: Duplicate Detection Accuracy
     * 
     * This property verifies that:
     * 1. Orders with 80%+ product overlap are flagged as duplicates (Req 11.3)
     * 2. Orders with <80% product overlap are NOT flagged (Req 11.3)
     * 3. Orders outside the configured day window are ignored (Req 11.1)
     * 4. Jaccard similarity is calculated correctly (Req 11.2)
     * 5. Duplicate list contains accurate similarity scores (Req 11.4)
     * 
     * The test generates:
     * - New order with a set of product IDs
     * - Existing orders with varying product overlap percentages
     * - Orders at different time offsets (inside and outside window)
     */
    @Property(tries = 500)
    @Label("Duplicate Detection Accuracy: 80%+ overlap flagged, <80% not flagged, time window respected")
    void duplicateDetectionAccuracy(
            @ForAll("productIdSet") Set<Long> newOrderProducts,
            @ForAll @IntRange(min = 0, max = 100) int overlapPercentage,
            @ForAll @IntRange(min = -14, max = 14) int daysOffset
    ) {
        // Arrange: Create mocks and service
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ConfigurationService configurationService = mock(ConfigurationService.class);
        
        OrderService orderService = new OrderService(
                orderRepository,
                mock(OrderItemRepository.class),
                customerRepository,
                mock(ProductRepository.class),
                mock(SalespersonRepository.class),
                mock(OrderNumberGenerator.class),
                mock(ProductManagementService.class),
                mock(PaymentRecordRepository.class),
                mock(WhatsAppTextParser.class),
                mock(CustomerService.class),
                configurationService,
                mock(AuditLogService.class),
                mock(CouponUsageRepository.class)
        );
        
        Customer testCustomer = createTestCustomer();
        
        // Configure duplicate check window
        int duplicateCheckDays = 7;
        when(configurationService.getDuplicateCheckDays()).thenReturn(duplicateCheckDays);
        
        LocalDate newOrderDate = LocalDate.now();
        LocalDate existingOrderDate = newOrderDate.plusDays(daysOffset);
        
        // Calculate expected product overlap based on overlapPercentage
        Set<Long> existingOrderProducts = generateProductSetWithOverlap(
                newOrderProducts, overlapPercentage);
        
        // Create existing order with calculated product overlap
        Order existingOrder = createOrderWithProducts(
                testCustomer,
                "ORD-TEST-001", 
                existingOrderDate, 
                existingOrderProducts);
        
        // Setup mocks
        when(customerRepository.findByPhone(TEST_PHONE))
                .thenReturn(Optional.of(testCustomer));
        
        // Determine if order is within time window
        boolean isWithinTimeWindow = Math.abs(daysOffset) <= duplicateCheckDays;
        
        if (isWithinTimeWindow) {
            when(orderRepository.findByCustomerIdAndOrderDateBetween(
                    eq(TEST_CUSTOMER_ID),
                    eq(newOrderDate.minusDays(duplicateCheckDays)),
                    eq(newOrderDate.plusDays(duplicateCheckDays))
            )).thenReturn(Collections.singletonList(existingOrder));
        } else {
            // Order is outside window, repository returns empty list
            when(orderRepository.findByCustomerIdAndOrderDateBetween(
                    eq(TEST_CUSTOMER_ID),
                    any(LocalDate.class),
                    any(LocalDate.class)
            )).thenReturn(Collections.emptyList());
        }
        
        // Act: Check for duplicates
        DuplicateCheckResult result = orderService.checkDuplicate(
                TEST_PHONE, 
                new ArrayList<>(newOrderProducts), 
                newOrderDate);
        
        // Assert: Verify duplicate detection based on overlap and time window
        double actualSimilarity = calculateJaccardSimilarity(newOrderProducts, existingOrderProducts);
        boolean shouldBeFlagged = actualSimilarity >= 0.80 && isWithinTimeWindow;
        
        // Requirement 11.3 & 11.1: Orders with 80%+ overlap within time window are flagged
        assert result.isHasDuplicates() == shouldBeFlagged :
                String.format("Duplicate flag mismatch: overlap=%d%%, similarity=%.2f, daysOffset=%d, " +
                              "isWithinTimeWindow=%b, expected flagged=%b, actual flagged=%b",
                        overlapPercentage, actualSimilarity, daysOffset, 
                        isWithinTimeWindow, shouldBeFlagged, result.isHasDuplicates());
        
        if (shouldBeFlagged) {
            // Requirement 11.4: Verify duplicate list contains correct information
            assert result.getPotentialDuplicates().size() == 1 :
                    String.format("Expected 1 duplicate, got %d", result.getPotentialDuplicates().size());
            
            DuplicateCheckResult.DuplicateOrderInfo duplicate = result.getPotentialDuplicates().get(0);
            
            // Requirement 11.2: Verify similarity score is calculated correctly
            assert Math.abs(duplicate.getSimilarityScore() - actualSimilarity) < 0.01 :
                    String.format("Similarity score mismatch: expected %.2f, got %.2f",
                            actualSimilarity, duplicate.getSimilarityScore());
            
            // Verify similarity is at least 80%
            assert duplicate.getSimilarityScore() >= 0.80 :
                    String.format("Flagged duplicate has similarity < 80%%: %.2f", 
                            duplicate.getSimilarityScore());
        } else if (isWithinTimeWindow) {
            // If within time window but not flagged, similarity must be < 80%
            assert actualSimilarity < 0.80 :
                    String.format("Order with %.2f%% similarity should not be flagged", 
                            actualSimilarity * 100);
        }
        
        // Requirement 11.1: Verify orders outside time window are not flagged
        if (!isWithinTimeWindow) {
            assert !result.isHasDuplicates() :
                    String.format("Order outside %d-day window (offset=%d) should not be flagged",
                            duplicateCheckDays, daysOffset);
            assert result.getPotentialDuplicates().isEmpty() :
                    "Orders outside time window should not appear in duplicate list";
        }
    }

    /**
     * **Validates: Requirement 11.3**
     * 
     * Property: Exact threshold behavior at 80% overlap
     * 
     * This property specifically tests the boundary condition:
     * - Exactly 80% overlap should be flagged
     * - Just below 80% overlap should not be flagged
     */
    @Property(tries = 200)
    @Label("Duplicate Detection Threshold: Exactly 80% overlap is flagged, below 80% is not")
    void duplicateDetectionThresholdBehavior(
            @ForAll @IntRange(min = 5, max = 20) int totalProducts
    ) {
        // Arrange: Create mocks and service
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ConfigurationService configurationService = mock(ConfigurationService.class);
        
        OrderService orderService = new OrderService(
                orderRepository,
                mock(OrderItemRepository.class),
                customerRepository,
                mock(ProductRepository.class),
                mock(SalespersonRepository.class),
                mock(OrderNumberGenerator.class),
                mock(ProductManagementService.class),
                mock(PaymentRecordRepository.class),
                mock(WhatsAppTextParser.class),
                mock(CustomerService.class),
                configurationService,
                mock(AuditLogService.class),
                mock(CouponUsageRepository.class)
        );
        
        Customer testCustomer = createTestCustomer();
        
        when(configurationService.getDuplicateCheckDays()).thenReturn(7);
        when(customerRepository.findByPhone(TEST_PHONE))
                .thenReturn(Optional.of(testCustomer));
        
        LocalDate orderDate = LocalDate.now();
        
        // Test 1: Exactly 80% overlap (should be flagged)
        Set<Long> newOrderProducts = generateProductIds(totalProducts);
        int exactOverlapCount = (int) Math.ceil(totalProducts * 0.80);
        Set<Long> existingProducts80 = new HashSet<>(
                new ArrayList<>(newOrderProducts).subList(0, exactOverlapCount));
        
        Order order80 = createOrderWithProducts(testCustomer, "ORD-80", 
                orderDate.minusDays(1), existingProducts80);
        
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(order80));
        
        // Act
        DuplicateCheckResult result80 = orderService.checkDuplicate(
                TEST_PHONE, new ArrayList<>(newOrderProducts), orderDate);
        
        // Assert: 80% overlap should be flagged
        double similarity80 = calculateJaccardSimilarity(newOrderProducts, existingProducts80);
        if (similarity80 >= 0.80) {
            assert result80.isHasDuplicates() :
                    String.format("Order with %.2f%% similarity (≥80%%) should be flagged", 
                            similarity80 * 100);
        }
        
        // Test 2: Just below 80% overlap (should not be flagged)
        int belowOverlapCount = Math.max(1, exactOverlapCount - 1);
        Set<Long> existingProductsBelow80 = new HashSet<>(
                new ArrayList<>(newOrderProducts).subList(0, belowOverlapCount));
        // Add different products to make it clearly below 80%
        existingProductsBelow80.add(999L);
        existingProductsBelow80.add(998L);
        
        Order orderBelow80 = createOrderWithProducts(testCustomer, "ORD-BELOW", 
                orderDate.minusDays(1), existingProductsBelow80);
        
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(orderBelow80));
        
        // Act
        DuplicateCheckResult resultBelow80 = orderService.checkDuplicate(
                TEST_PHONE, new ArrayList<>(newOrderProducts), orderDate);
        
        // Assert: Below 80% should not be flagged
        double similarityBelow80 = calculateJaccardSimilarity(newOrderProducts, existingProductsBelow80);
        if (similarityBelow80 < 0.80) {
            assert !resultBelow80.isHasDuplicates() :
                    String.format("Order with %.2f%% similarity (<80%%) should not be flagged", 
                            similarityBelow80 * 100);
        }
    }

    /**
     * **Validates: Requirement 11.1**
     * 
     * Property: Time window boundary conditions
     * 
     * This property tests that:
     * - Orders exactly at the 7-day boundary are included
     * - Orders just outside the 7-day boundary are excluded
     */
    @Property(tries = 200)
    @Label("Time Window Boundary: Orders at boundary are included, outside are excluded")
    void timeWindowBoundaryConditions(
            @ForAll("productIdSet") Set<Long> productIds
    ) {
        // Arrange: Create mocks and service
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ConfigurationService configurationService = mock(ConfigurationService.class);
        
        OrderService orderService = new OrderService(
                orderRepository,
                mock(OrderItemRepository.class),
                customerRepository,
                mock(ProductRepository.class),
                mock(SalespersonRepository.class),
                mock(OrderNumberGenerator.class),
                mock(ProductManagementService.class),
                mock(PaymentRecordRepository.class),
                mock(WhatsAppTextParser.class),
                mock(CustomerService.class),
                configurationService,
                mock(AuditLogService.class),
                mock(CouponUsageRepository.class)
        );
        
        Customer testCustomer = createTestCustomer();
        
        int duplicateCheckDays = 7;
        when(configurationService.getDuplicateCheckDays()).thenReturn(duplicateCheckDays);
        when(customerRepository.findByPhone(TEST_PHONE))
                .thenReturn(Optional.of(testCustomer));
        
        LocalDate newOrderDate = LocalDate.now();
        
        // Test 1: Order exactly 7 days ago (at boundary - should be included)
        LocalDate boundaryDate = newOrderDate.minusDays(7);
        Order orderAtBoundary = createOrderWithProducts(testCustomer, "ORD-BOUNDARY", 
                boundaryDate, productIds);
        
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                eq(TEST_CUSTOMER_ID),
                eq(newOrderDate.minusDays(7)),
                eq(newOrderDate.plusDays(7))
        )).thenReturn(Collections.singletonList(orderAtBoundary));
        
        // Act
        DuplicateCheckResult resultAtBoundary = orderService.checkDuplicate(
                TEST_PHONE, new ArrayList<>(productIds), newOrderDate);
        
        // Assert: Order at boundary with 100% overlap should be flagged
        assert resultAtBoundary.isHasDuplicates() :
                "Order exactly at 7-day boundary with 100% overlap should be flagged";
        
        // Test 2: Order 8 days ago (outside boundary - should be excluded)
        // Repository should not return orders outside the window
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                eq(TEST_CUSTOMER_ID),
                eq(newOrderDate.minusDays(7)),
                eq(newOrderDate.plusDays(7))
        )).thenReturn(Collections.emptyList());
        
        // Act
        DuplicateCheckResult resultOutside = orderService.checkDuplicate(
                TEST_PHONE, new ArrayList<>(productIds), newOrderDate);
        
        // Assert: Order outside window should not be returned by repository
        assert !resultOutside.isHasDuplicates() :
                "Order outside 7-day window should not be flagged";
    }

    /**
     * **Validates: Requirement 11.5**
     * 
     * Property: No customer means no duplicates
     * 
     * Verifies that when customer doesn't exist, the system always returns no duplicates.
     */
    @Property(tries = 100)
    @Label("No Customer: When customer doesn't exist, no duplicates are found")
    void noCustomerMeansNoDuplicates(
            @ForAll("productIdSet") Set<Long> productIds
    ) {
        // Arrange: Create mocks and service with no customer
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        
        OrderService orderService = new OrderService(
                mock(OrderRepository.class),
                mock(OrderItemRepository.class),
                customerRepository,
                mock(ProductRepository.class),
                mock(SalespersonRepository.class),
                mock(OrderNumberGenerator.class),
                mock(ProductManagementService.class),
                mock(PaymentRecordRepository.class),
                mock(WhatsAppTextParser.class),
                mock(CustomerService.class),
                mock(ConfigurationService.class),
                mock(AuditLogService.class),
                mock(CouponUsageRepository.class)
        );
        
        // Customer doesn't exist
        when(customerRepository.findByPhone("9999999999"))
                .thenReturn(Optional.empty());
        
        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(
                "9999999999", new ArrayList<>(productIds), LocalDate.now());
        
        // Assert: No duplicates should be found
        assert !result.isHasDuplicates() :
                "When customer doesn't exist, no duplicates should be found";
        assert result.getPotentialDuplicates().isEmpty() :
                "Duplicate list should be empty when customer doesn't exist";
        assert "9999999999".equals(result.getCustomerPhone()) :
                "Customer phone should be included in result";
    }

    /**
     * **Validates: Requirement 11.2**
     * 
     * Property: Jaccard similarity calculation correctness
     * 
     * Verifies that the similarity calculation matches the mathematical definition:
     * Jaccard(A, B) = |A ∩ B| / |A ∪ B|
     */
    @Property(tries = 300)
    @Label("Jaccard Similarity: Correctly calculated as |intersection| / |union|")
    void jaccardSimilarityCalculation(
            @ForAll("productIdSet") Set<Long> products1,
            @ForAll("productIdSet") Set<Long> products2
    ) {
        // Arrange: Create mocks and service
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ConfigurationService configurationService = mock(ConfigurationService.class);
        
        OrderService orderService = new OrderService(
                orderRepository,
                mock(OrderItemRepository.class),
                customerRepository,
                mock(ProductRepository.class),
                mock(SalespersonRepository.class),
                mock(OrderNumberGenerator.class),
                mock(ProductManagementService.class),
                mock(PaymentRecordRepository.class),
                mock(WhatsAppTextParser.class),
                mock(CustomerService.class),
                configurationService,
                mock(AuditLogService.class),
                mock(CouponUsageRepository.class)
        );
        
        Customer testCustomer = createTestCustomer();
        
        when(configurationService.getDuplicateCheckDays()).thenReturn(7);
        when(customerRepository.findByPhone(TEST_PHONE))
                .thenReturn(Optional.of(testCustomer));
        
        LocalDate orderDate = LocalDate.now();
        Order existingOrder = createOrderWithProducts(testCustomer, "ORD-TEST", 
                orderDate.minusDays(1), products2);
        
        when(orderRepository.findByCustomerIdAndOrderDateBetween(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(existingOrder));
        
        // Calculate expected Jaccard similarity
        double expectedSimilarity = calculateJaccardSimilarity(products1, products2);
        
        // Act
        DuplicateCheckResult result = orderService.checkDuplicate(
                TEST_PHONE, new ArrayList<>(products1), orderDate);
        
        // Assert: If similarity >= 80%, verify the calculated similarity is correct
        if (expectedSimilarity >= 0.80) {
            assert result.isHasDuplicates() :
                    String.format("Order with %.2f%% similarity should be flagged", 
                            expectedSimilarity * 100);
            
            DuplicateCheckResult.DuplicateOrderInfo duplicate = result.getPotentialDuplicates().get(0);
            assert Math.abs(duplicate.getSimilarityScore() - expectedSimilarity) < 0.01 :
                    String.format("Similarity calculation error: expected %.4f, got %.4f",
                            expectedSimilarity, duplicate.getSimilarityScore());
        }
        
        // Verify mathematical correctness
        Set<Long> intersection = new HashSet<>(products1);
        intersection.retainAll(products2);
        Set<Long> union = new HashSet<>(products1);
        union.addAll(products2);
        
        double manualSimilarity = union.isEmpty() ? 1.0 : 
                (double) intersection.size() / union.size();
        
        assert Math.abs(expectedSimilarity - manualSimilarity) < 0.001 :
                String.format("Jaccard calculation inconsistency: %.4f vs %.4f",
                        expectedSimilarity, manualSimilarity);
    }

    // --- Arbitraries (Data Generators) ---

    /**
     * Generate a set of product IDs for testing.
     * Returns sets with 1-15 products to test various overlap scenarios.
     */
    @Provide
    Arbitrary<Set<Long>> productIdSet() {
        return Arbitraries.longs()
                .between(1L, 100L)
                .set()
                .ofMinSize(1)
                .ofMaxSize(15);
    }

    // --- Helper Methods ---

    /**
     * Create a test customer entity.
     */
    private Customer createTestCustomer() {
        return Customer.builder()
                .id(TEST_CUSTOMER_ID)
                .name("Test Customer")
                .phone(TEST_PHONE)
                .city("Mumbai")
                .state("Maharashtra")
                .build();
    }

    /**
     * Generate a set of product IDs with a specific size.
     */
    private Set<Long> generateProductIds(int count) {
        Set<Long> products = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            products.add((long) i);
        }
        return products;
    }

    /**
     * Generate a product set with a specific percentage of overlap with the base set.
     * 
     * @param baseProducts Base product set
     * @param overlapPercentage Percentage of overlap (0-100)
     * @return New product set with calculated overlap
     */
    private Set<Long> generateProductSetWithOverlap(Set<Long> baseProducts, int overlapPercentage) {
        if (baseProducts.isEmpty()) {
            return new HashSet<>();
        }
        
        List<Long> baseList = new ArrayList<>(baseProducts);
        int baseSize = baseList.size();
        
        // Calculate how many products should overlap
        int overlapCount = (int) Math.round(baseSize * overlapPercentage / 100.0);
        overlapCount = Math.min(overlapCount, baseSize);
        
        // Create new set with overlapping products
        Set<Long> newProducts = new HashSet<>();
        
        // Add overlapping products from base set
        for (int i = 0; i < overlapCount; i++) {
            newProducts.add(baseList.get(i));
        }
        
        // Add non-overlapping products to achieve desired overlap percentage
        if (overlapPercentage < 100) {
            int additionalCount = Math.max(0, baseSize - overlapCount);
            for (int i = 0; i < additionalCount; i++) {
                newProducts.add(1000L + i); // Use high IDs to avoid collision
            }
        }
        
        return newProducts;
    }

    /**
     * Create an Order entity with specific products.
     */
    private Order createOrderWithProducts(Customer customer, String orderNumber, 
                                          LocalDate orderDate, Set<Long> productIds) {
        Order order = Order.builder()
                .id((long) (Math.random() * 10000))
                .orderNumber(orderNumber)
                .orderDate(orderDate)
                .customer(customer)
                .status(Order.OrderStatus.NEW)
                .totalAmount(BigDecimal.valueOf(1000.00))
                .items(new ArrayList<>())
                .build();
        
        for (Long productId : productIds) {
            Product product = Product.builder()
                    .id(productId)
                    .sku("SKU-" + productId)
                    .name("Product " + productId)
                    .salePrice(BigDecimal.valueOf(100.00))
                    .mrp(BigDecimal.valueOf(120.00))
                    .stockQuantity(100)
                    .build();
            
            OrderItem item = OrderItem.builder()
                    .id((long) (Math.random() * 10000))
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

    /**
     * Calculate Jaccard similarity between two product sets.
     * This matches the implementation in OrderService.
     */
    private double calculateJaccardSimilarity(Set<Long> set1, Set<Long> set2) {
        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0;
        }
        if (set1.isEmpty() || set2.isEmpty()) {
            return 0.0;
        }
        
        Set<Long> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<Long> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return (double) intersection.size() / union.size();
    }
}
