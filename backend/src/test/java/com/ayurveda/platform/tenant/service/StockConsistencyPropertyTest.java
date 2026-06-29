package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.InsufficientStockException;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.entity.StockHistory;
import com.ayurveda.platform.tenant.repository.OrderItemRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.repository.StockHistoryRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for Stock Consistency using jqwik.
 * 
 * **Validates: Requirements 9.2, 9.3, 9.4, 9.6**
 * 
 * This test suite validates stock management consistency across all possible sequences
 * of stock operations using property-based testing.
 */
class StockConsistencyPropertyTest {

    private static final Long TEST_PRODUCT_ID = 1L;
    private static final Long TEST_USER_ID = 999L;

    /**
     * **Validates: Requirements 9.2, 9.3, 9.4, 9.6**
     * 
     * Property 3: Stock Consistency
     * 
     * This property verifies that for any sequence of valid stock operations:
     * - Final stock quantity = initial stock - total outflows + inflows
     * - Stock quantity never becomes negative
     * - StockHistory accurately records all operations
     * 
     * Requirements validated:
     * - 9.2: Stock is reduced when order status transitions to PACKED
     * - 9.3: Stock is restored when order is CANCELLED or RETURNED
     * - 9.4: Stock quantity never becomes negative
     * - 9.6: StockHistory includes quantity before, changed, and after
     */
    @Property(tries = 500)
    @Label("Stock Consistency: finalStock = initialStock - outflows + inflows AND stock never negative")
    void stockConsistencyAcrossOperations(
            @ForAll @IntRange(min = 50, max = 500) int initialStock,
            @ForAll("validStockOperations") List<StockOperation> operations
    ) {
        // Arrange: Set up product with initial stock and mocks
        Product testProduct = createTestProduct(initialStock);
        
        ProductRepository productRepository = mock(ProductRepository.class);
        StockHistoryRepository stockHistoryRepository = mock(StockHistoryRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        
        ProductManagementService service = new ProductManagementService(
            productRepository, orderItemRepository, stockHistoryRepository);
        
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            testProduct.setStockQuantity(saved.getStockQuantity());
            return saved;
        });
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(invocation -> 
            invocation.getArgument(0));

        // Track expected calculations
        int currentStock = initialStock;
        int totalInflows = 0;
        int totalOutflows = 0;
        int successfulOperations = 0;
        List<Integer> stockSnapshots = new ArrayList<>();
        stockSnapshots.add(currentStock);

        // Act: Execute all stock operations in sequence
        for (StockOperation op : operations) {
            try {
                service.updateStock(
                    TEST_PRODUCT_ID, 
                    op.quantity, 
                    op.operationType,
                    op.referenceType,
                    op.referenceId,
                    op.notes,
                    TEST_USER_ID
                );

                // Update tracking
                currentStock += op.quantity;
                if (op.quantity > 0) {
                    totalInflows += op.quantity;
                } else {
                    totalOutflows += Math.abs(op.quantity);
                }
                stockSnapshots.add(currentStock);
                successfulOperations++; // Count successful operations

                // Assert: Stock never negative after successful operation (Requirement 9.4)
                assert currentStock >= 0 : 
                    String.format("Stock should never be negative, got %d after operation: %s", 
                        currentStock, op);

            } catch (InsufficientStockException e) {
                // Expected behavior: operation rejected if it would make stock negative
                // Verify that the operation WOULD have made stock negative
                int wouldBeStock = currentStock + op.quantity;
                assert wouldBeStock < 0 : 
                    String.format("InsufficientStockException should only be thrown when " +
                        "operation would result in negative stock. Current: %d, Change: %d, " +
                        "Would be: %d", currentStock, op.quantity, wouldBeStock);
                
                // Stock should remain unchanged after exception
                stockSnapshots.add(currentStock);
            }
        }

        // Assert: Final consistency check (Requirements 9.2, 9.3, 9.6)
        int expectedFinalStock = initialStock + totalInflows - totalOutflows;
        int actualFinalStock = testProduct.getStockQuantity();

        assert actualFinalStock == expectedFinalStock :
            String.format("Final stock mismatch: expected %d (initial=%d + inflows=%d - outflows=%d), got %d. " +
                "Operations: %s", 
                expectedFinalStock, initialStock, totalInflows, totalOutflows, actualFinalStock, operations);

        // Assert: Final stock equals current tracked stock
        assert actualFinalStock == currentStock :
            String.format("Final stock should match tracked stock: expected %d, got %d",
                currentStock, actualFinalStock);

        // Verify StockHistory was created for each successful operation
        verify(stockHistoryRepository, times(successfulOperations)).save(any(StockHistory.class));
    }

    /**
     * **Validates: Requirements 9.4**
     * 
     * Property: Stock Never Negative
     * 
     * This property specifically verifies that the system prevents stock from going negative
     * by throwing InsufficientStockException when an operation would result in negative stock.
     */
    @Property(tries = 300)
    @Label("Stock Never Negative: Operations that would make stock negative are rejected")
    void stockNeverBecomesNegative(
            @ForAll @IntRange(min = 1, max = 100) int initialStock,
            @ForAll @IntRange(min = 101, max = 500) int excessiveOutflow
    ) {
        // Arrange: Set up product with known stock
        Product testProduct = createTestProduct(initialStock);
        
        ProductRepository productRepository = mock(ProductRepository.class);
        StockHistoryRepository stockHistoryRepository = mock(StockHistoryRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        
        ProductManagementService service = new ProductManagementService(
            productRepository, orderItemRepository, stockHistoryRepository);
        
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // Act & Assert: Attempt to remove more stock than available
        try {
            service.updateStock(
                TEST_PRODUCT_ID, 
                -excessiveOutflow, 
                StockHistory.StockOperation.STOCK_OUT,
                "ORDER",
                123L,
                "Excessive order",
                TEST_USER_ID
            );
            
            // If we reach here, the test should fail
            assert false : 
                String.format("Should have thrown InsufficientStockException when trying to " +
                    "remove %d from stock of %d", excessiveOutflow, initialStock);
                    
        } catch (InsufficientStockException e) {
            // Expected: exception thrown
            assert testProduct.getStockQuantity() == initialStock :
                String.format("Stock should remain unchanged after exception: expected %d, got %d",
                    initialStock, testProduct.getStockQuantity());
        }

        // Verify that save was never called (transaction should roll back)
        verify(productRepository, never()).save(any(Product.class));
        verify(stockHistoryRepository, never()).save(any(StockHistory.class));
    }

    /**
     * **Validates: Requirements 9.2, 9.3**
     * 
     * Property: Inflows and Outflows Balance
     * 
     * This property verifies that STOCK_IN and RETURN operations (inflows) properly
     * increase stock, while STOCK_OUT operations (outflows) properly decrease stock.
     */
    @Property(tries = 300)
    @Label("Inflows increase stock and Outflows decrease stock correctly")
    void inflowsAndOutflowsBalance(
            @ForAll @IntRange(min = 100, max = 500) int initialStock,
            @ForAll @IntRange(min = 1, max = 50) int inflowAmount,
            @ForAll @IntRange(min = 1, max = 50) int outflowAmount
    ) {
        // Arrange
        Product testProduct = createTestProduct(initialStock);
        
        ProductRepository productRepository = mock(ProductRepository.class);
        StockHistoryRepository stockHistoryRepository = mock(StockHistoryRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        
        ProductManagementService service = new ProductManagementService(
            productRepository, orderItemRepository, stockHistoryRepository);
        
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            testProduct.setStockQuantity(saved.getStockQuantity());
            return saved;
        });
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(invocation -> 
            invocation.getArgument(0));

        // Act: Perform inflow operation (STOCK_IN)
        service.updateStock(
            TEST_PRODUCT_ID, 
            inflowAmount, 
            StockHistory.StockOperation.STOCK_IN,
            "PURCHASE",
            100L,
            "Stock replenishment",
            TEST_USER_ID
        );

        int stockAfterInflow = testProduct.getStockQuantity();

        // Assert: Stock increased by inflow amount (Requirement 9.2 - inverse)
        assert stockAfterInflow == initialStock + inflowAmount :
            String.format("Stock after inflow should be %d, got %d", 
                initialStock + inflowAmount, stockAfterInflow);

        // Act: Perform outflow operation (STOCK_OUT)
        service.updateStock(
            TEST_PRODUCT_ID, 
            -outflowAmount, 
            StockHistory.StockOperation.STOCK_OUT,
            "ORDER",
            200L,
            "Order packed",
            TEST_USER_ID
        );

        int finalStock = testProduct.getStockQuantity();

        // Assert: Stock decreased by outflow amount (Requirement 9.2)
        assert finalStock == stockAfterInflow - outflowAmount :
            String.format("Stock after outflow should be %d, got %d", 
                stockAfterInflow - outflowAmount, finalStock);

        // Assert: Net change is correct
        int netChange = inflowAmount - outflowAmount;
        assert finalStock == initialStock + netChange :
            String.format("Final stock should be %d (initial=%d + net=%d), got %d",
                initialStock + netChange, initialStock, netChange, finalStock);
    }

    /**
     * **Validates: Requirements 9.3**
     * 
     * Property: Return Operations Restore Stock
     * 
     * This property verifies that RETURN operations correctly restore stock
     * after items were previously removed (simulating order cancellation/return).
     */
    @Property(tries = 200)
    @Label("RETURN operations correctly restore stock")
    void returnOperationsRestoreStock(
            @ForAll @IntRange(min = 100, max = 500) int initialStock,
            @ForAll @IntRange(min = 10, max = 50) int orderQuantity
    ) {
        // Arrange
        Product testProduct = createTestProduct(initialStock);
        
        ProductRepository productRepository = mock(ProductRepository.class);
        StockHistoryRepository stockHistoryRepository = mock(StockHistoryRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        
        ProductManagementService service = new ProductManagementService(
            productRepository, orderItemRepository, stockHistoryRepository);
        
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            testProduct.setStockQuantity(saved.getStockQuantity());
            return saved;
        });
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(invocation -> 
            invocation.getArgument(0));

        // Act: Simulate order packing (stock out)
        service.updateStock(
            TEST_PRODUCT_ID, 
            -orderQuantity, 
            StockHistory.StockOperation.STOCK_OUT,
            "ORDER",
            300L,
            "Order packed",
            TEST_USER_ID
        );

        int stockAfterPacking = testProduct.getStockQuantity();

        // Assert: Stock reduced after packing
        assert stockAfterPacking == initialStock - orderQuantity :
            String.format("Stock after packing should be %d, got %d", 
                initialStock - orderQuantity, stockAfterPacking);

        // Act: Simulate order return (stock back in)
        service.updateStock(
            TEST_PRODUCT_ID, 
            orderQuantity, 
            StockHistory.StockOperation.RETURN,
            "ORDER",
            300L,
            "Order returned",
            TEST_USER_ID
        );

        int finalStock = testProduct.getStockQuantity();

        // Assert: Stock restored to initial level (Requirement 9.3)
        assert finalStock == initialStock :
            String.format("Stock should be restored to initial level %d after return, got %d",
                initialStock, finalStock);
    }

    /**
     * **Validates: Requirements 9.6, 21.2**
     * 
     * Property: StockHistory Records Complete Information
     * 
     * This property verifies that every stock operation creates a complete
     * StockHistory record with before, changed, and after quantities.
     */
    @Property(tries = 200)
    @Label("StockHistory records include quantity before, changed, and after")
    void stockHistoryRecordsCompleteInformation(
            @ForAll @IntRange(min = 50, max = 200) int initialStock,
            @ForAll @IntRange(min = -49, max = 100) int quantityChange
    ) {
        // Skip if change would make stock negative
        Assume.that(initialStock + quantityChange >= 0);

        // Arrange
        Product testProduct = createTestProduct(initialStock);
        
        ProductRepository productRepository = mock(ProductRepository.class);
        StockHistoryRepository stockHistoryRepository = mock(StockHistoryRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        ArgumentCaptor<StockHistory> historyCaptor = ArgumentCaptor.forClass(StockHistory.class);
        
        ProductManagementService service = new ProductManagementService(
            productRepository, orderItemRepository, stockHistoryRepository);
        
        when(productRepository.findById(TEST_PRODUCT_ID)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            testProduct.setStockQuantity(saved.getStockQuantity());
            return saved;
        });
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(invocation -> 
            invocation.getArgument(0));

        // Determine operation type
        StockHistory.StockOperation opType = quantityChange >= 0 
            ? StockHistory.StockOperation.STOCK_IN 
            : StockHistory.StockOperation.STOCK_OUT;

        // Act
        service.updateStock(
            TEST_PRODUCT_ID, 
            quantityChange, 
            opType,
            "TEST",
            400L,
            "Test operation",
            TEST_USER_ID
        );

        // Assert: Verify StockHistory record was created with correct values
        verify(stockHistoryRepository).save(historyCaptor.capture());
        StockHistory savedHistory = historyCaptor.getValue();

        // Requirement 9.6 & 21.2: History includes quantity before, changed, and after
        assert savedHistory.getQuantityBefore().equals(initialStock) :
            String.format("History quantityBefore should be %d, got %d", 
                initialStock, savedHistory.getQuantityBefore());

        assert savedHistory.getQuantityChanged().equals(quantityChange) :
            String.format("History quantityChanged should be %d, got %d", 
                quantityChange, savedHistory.getQuantityChanged());

        int expectedAfter = initialStock + quantityChange;
        assert savedHistory.getQuantityAfter().equals(expectedAfter) :
            String.format("History quantityAfter should be %d, got %d", 
                expectedAfter, savedHistory.getQuantityAfter());

        // Verify consistency: quantityAfter = quantityBefore + quantityChanged
        assert savedHistory.getQuantityAfter().equals(
            savedHistory.getQuantityBefore() + savedHistory.getQuantityChanged()) :
            "StockHistory quantityAfter should equal quantityBefore + quantityChanged";
    }

    // ==================== Helper Methods ====================

    private Product createTestProduct(int stockQuantity) {
        return Product.builder()
                .id(TEST_PRODUCT_ID)
                .sku("TEST-SKU-001")
                .name("Test Product")
                .description("Test Description")
                .category("Test Category")
                .mrp(BigDecimal.valueOf(100.00))
                .salePrice(BigDecimal.valueOf(80.00))
                .unit("pcs")
                .stockQuantity(stockQuantity)
                .lowStockThreshold(10)
                .isActive(true)
                .build();
    }

    // ==================== Arbitrary Generators ====================

    /**
     * Generates a list of valid stock operations that won't make stock negative.
     * Creates realistic sequences of STOCK_IN, STOCK_OUT, RETURN, and ADJUSTMENT operations.
     */
    @Provide
    Arbitrary<List<StockOperation>> validStockOperations() {
        return stockOperation().list().ofMinSize(5).ofMaxSize(30);
    }

    /**
     * Generates a single stock operation with realistic values.
     * Biases toward smaller operations to ensure sequences remain valid.
     */
    @Provide
    Arbitrary<StockOperation> stockOperation() {
        Arbitrary<StockHistory.StockOperation> operationType = Arbitraries.of(
            StockHistory.StockOperation.STOCK_IN,
            StockHistory.StockOperation.STOCK_OUT,
            StockHistory.StockOperation.RETURN,
            StockHistory.StockOperation.ADJUSTMENT
        );

        // Generate quantities biased toward smaller values to avoid exhausting stock
        Arbitrary<Integer> quantity = Arbitraries.integers().between(1, 100)
                .edgeCases(config -> config.add(1, 20, 50, 100));

        Arbitrary<String> referenceType = Arbitraries.of(
            "ORDER", "PURCHASE", "ADJUSTMENT", "RETURN", null
        );

        Arbitrary<Long> referenceId = Arbitraries.longs().between(1L, 1000L);

        Arbitrary<String> notes = Arbitraries.of(
            "Stock operation", "Inventory adjustment", "Order fulfillment", 
            "Return processing", null
        );

        return Combinators.combine(operationType, quantity, referenceType, referenceId, notes)
            .as((opType, qty, refType, refId, note) -> {
                // Adjust quantity sign based on operation type
                int signedQty;
                if (opType == StockHistory.StockOperation.STOCK_OUT) {
                    signedQty = -Math.abs(qty);
                } else if (opType == StockHistory.StockOperation.ADJUSTMENT) {
                    // Adjustments can be positive or negative
                    signedQty = qty * (Arbitraries.integers().between(0, 1).sample() == 0 ? -1 : 1);
                } else {
                    // STOCK_IN and RETURN are positive
                    signedQty = Math.abs(qty);
                }

                return new StockOperation(opType, signedQty, refType, refId, note);
            });
    }

    /**
     * Helper class to represent a stock operation with all its parameters.
     */
    private static class StockOperation {
        final StockHistory.StockOperation operationType;
        final int quantity;
        final String referenceType;
        final Long referenceId;
        final String notes;

        StockOperation(StockHistory.StockOperation operationType, int quantity, 
                      String referenceType, Long referenceId, String notes) {
            this.operationType = operationType;
            this.quantity = quantity;
            this.referenceType = referenceType;
            this.referenceId = referenceId;
            this.notes = notes;
        }

        @Override
        public String toString() {
            return String.format("StockOp{type=%s, qty=%d, ref=%s:%d}", 
                operationType, quantity, referenceType, referenceId);
        }
    }
}
