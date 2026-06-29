package com.ayurveda.platform.tenant.entity;

import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Property-Based Tests for Order Total Calculation using jqwik.
 * 
 * This test suite validates Requirements 4.1, 4.2, 4.3, 4.4 using property-based testing
 * to verify order total calculation consistency across all possible input combinations.
 */
class OrderTotalCalculationPropertyTest {

    /**
     * **Validates: Requirements 4.1, 4.2, 4.3, 4.4**
     * 
     * Property 1: Order Total Consistency
     * 
     * This property verifies that for any valid order with items, discounts, taxes, and shipping charges:
     * totalAmount = subtotal - discountAmount + taxAmount + shippingCharge
     * 
     * Where:
     * - subtotal = sum of all OrderItem lineTotals
     * - lineTotal = (quantity × unitPrice) - itemDiscount + itemTax
     * 
     * Requirements validated:
     * - 4.1: Subtotal calculated as sum of all OrderItem line totals
     * - 4.2: Total amount = subtotal - discount + tax + shipping
     * - 4.3: Line total = (quantity × unitPrice) - discount + tax
     * - 4.4: 2 decimal place precision maintained using BigDecimal
     */
    @Property(tries = 1000)
    @Label("Order Total Consistency: totalAmount = subtotal - discountAmount + taxAmount + shippingCharge")
    void orderTotalConsistency(
            @ForAll("orderItems") List<OrderItem> items,
            @ForAll @BigRange(min = "0.00", max = "10000.00") BigDecimal discountAmount,
            @ForAll @BigRange(min = "0.00", max = "5000.00") BigDecimal taxAmount,
            @ForAll @BigRange(min = "0.00", max = "1000.00") BigDecimal shippingCharge
    ) {
        // Arrange: Create an order with the generated items and adjustments
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-001")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .discountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP))
                .taxAmount(taxAmount.setScale(2, RoundingMode.HALF_UP))
                .shippingCharge(shippingCharge.setScale(2, RoundingMode.HALF_UP))
                .items(new ArrayList<>())
                .build();

        // Add items to order
        for (OrderItem item : items) {
            item.calculateLineTotal();
            order.addItem(item);
        }

        // Act: Calculate order totals
        order.recalculateTotals();

        // Assert: Verify total calculation formula
        // Expected subtotal = sum of all item line totals
        BigDecimal expectedSubtotal = items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Expected total = subtotal - discount + tax + shipping
        BigDecimal expectedTotal = expectedSubtotal
                .subtract(discountAmount.setScale(2, RoundingMode.HALF_UP))
                .add(taxAmount.setScale(2, RoundingMode.HALF_UP))
                .add(shippingCharge.setScale(2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);

        // Verify subtotal calculation (Requirement 4.1)
        assert order.getSubtotal().compareTo(expectedSubtotal) == 0 :
                String.format("Subtotal mismatch: expected %s, got %s", 
                        expectedSubtotal, order.getSubtotal());

        // Verify total amount calculation (Requirement 4.2)
        assert order.getTotalAmount().compareTo(expectedTotal) == 0 :
                String.format("Total amount mismatch: expected %s, got %s", 
                        expectedTotal, order.getTotalAmount());

        // Verify 2 decimal place precision (Requirement 4.4)
        assert order.getSubtotal().scale() == 2 :
                String.format("Subtotal scale should be 2, got %d", order.getSubtotal().scale());
        assert order.getTotalAmount().scale() == 2 :
                String.format("Total amount scale should be 2, got %d", order.getTotalAmount().scale());

        // Verify each item's line total is calculated correctly (Requirement 4.3)
        for (OrderItem item : items) {
            BigDecimal expectedLineTotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .subtract(item.getDiscount())
                    .add(item.getTaxAmount())
                    .setScale(2, RoundingMode.HALF_UP);

            assert item.getLineTotal().compareTo(expectedLineTotal) == 0 :
                    String.format("Line total mismatch for item: expected %s, got %s",
                            expectedLineTotal, item.getLineTotal());
        }
    }

    /**
     * Arbitrary generator for OrderItem list.
     * Generates 1-10 order items with random valid values.
     */
    @Provide
    Arbitrary<List<OrderItem>> orderItems() {
        return orderItem().list().ofMinSize(1).ofMaxSize(10);
    }

    /**
     * Arbitrary generator for a single OrderItem.
     * Generates realistic order items with:
     * - Quantity: 1-100 units
     * - Unit price: $1.00 - $500.00
     * - Discount: $0.00 - $100.00
     * - Tax: $0.00 - $50.00
     */
    @Provide
    Arbitrary<OrderItem> orderItem() {
        Arbitrary<Integer> quantity = Arbitraries.integers().between(1, 100);
        Arbitrary<BigDecimal> unitPrice = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(500.00))
                .ofScale(2);
        Arbitrary<BigDecimal> discount = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(100.00))
                .ofScale(2);
        Arbitrary<BigDecimal> taxAmount = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.valueOf(50.00))
                .ofScale(2);

        return Combinators.combine(quantity, unitPrice, discount, taxAmount)
                .as((qty, price, disc, tax) -> 
                    OrderItem.builder()
                            .productNameSnapshot("Test Product")
                            .skuSnapshot("TEST-SKU")
                            .quantity(qty)
                            .unitPrice(price.setScale(2, RoundingMode.HALF_UP))
                            .discount(disc.setScale(2, RoundingMode.HALF_UP))
                            .taxAmount(tax.setScale(2, RoundingMode.HALF_UP))
                            .build()
                );
    }

    /**
     * Additional property test: Order with empty items should have zero subtotal
     * **Validates: Requirement 4.5**
     */
    @Property(tries = 100)
    @Label("Order with no items should have zero subtotal")
    void orderWithNoItemsHasZeroSubtotal(
            @ForAll @BigRange(min = "0.00", max = "1000.00") BigDecimal discountAmount,
            @ForAll @BigRange(min = "0.00", max = "500.00") BigDecimal taxAmount,
            @ForAll @BigRange(min = "0.00", max = "200.00") BigDecimal shippingCharge
    ) {
        // Arrange: Create an order with no items
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-EMPTY")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .discountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP))
                .taxAmount(taxAmount.setScale(2, RoundingMode.HALF_UP))
                .shippingCharge(shippingCharge.setScale(2, RoundingMode.HALF_UP))
                .items(new ArrayList<>())
                .build();

        // Act: Calculate order totals
        order.recalculateTotals();

        // Assert: Subtotal should be zero when no items exist
        assert order.getSubtotal().compareTo(BigDecimal.ZERO) == 0 :
                String.format("Subtotal should be zero for empty order, got %s", order.getSubtotal());

        // Total should be: 0 - discount + tax + shipping
        BigDecimal expectedTotal = BigDecimal.ZERO
                .subtract(discountAmount.setScale(2, RoundingMode.HALF_UP))
                .add(taxAmount.setScale(2, RoundingMode.HALF_UP))
                .add(shippingCharge.setScale(2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);

        assert order.getTotalAmount().compareTo(expectedTotal) == 0 :
                String.format("Total amount mismatch for empty order: expected %s, got %s",
                        expectedTotal, order.getTotalAmount());
    }

    /**
     * Edge case property test: Large orders with many items
     * Verifies calculation remains accurate with higher volumes
     */
    @Property(tries = 100)
    @Label("Order total calculation remains accurate with many items")
    void orderTotalAccuracyWithManyItems(
            @ForAll("largeOrderItems") List<OrderItem> items
    ) {
        // Arrange: Create an order with many items
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-LARGE")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();

        // Add items to order
        for (OrderItem item : items) {
            item.calculateLineTotal();
            order.addItem(item);
        }

        // Act: Calculate order totals
        order.recalculateTotals();

        // Assert: Verify manual calculation matches
        BigDecimal manualSubtotal = BigDecimal.ZERO;
        for (OrderItem item : items) {
            manualSubtotal = manualSubtotal.add(item.getLineTotal());
        }
        manualSubtotal = manualSubtotal.setScale(2, RoundingMode.HALF_UP);

        assert order.getSubtotal().compareTo(manualSubtotal) == 0 :
                String.format("Subtotal mismatch for large order: expected %s, got %s",
                        manualSubtotal, order.getSubtotal());

        // For large orders, total should equal subtotal (no adjustments)
        assert order.getTotalAmount().compareTo(order.getSubtotal()) == 0 :
                String.format("Total should equal subtotal when no adjustments: expected %s, got %s",
                        order.getSubtotal(), order.getTotalAmount());
    }

    /**
     * Arbitrary generator for large order item lists.
     * Generates 20-50 order items to test scalability.
     */
    @Provide
    Arbitrary<List<OrderItem>> largeOrderItems() {
        return orderItem().list().ofMinSize(20).ofMaxSize(50);
    }
}
