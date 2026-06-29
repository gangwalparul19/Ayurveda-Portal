package com.ayurveda.platform.tenant.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for business database entities to verify structure and relationships.
 */
class BusinessEntitiesTest {

    @Test
    void testOrderEntityCreation() {
        // Given
        Customer customer = Customer.builder()
                .name("Test Customer")
                .phone("9876543210")
                .build();

        Order order = Order.builder()
                .orderNumber("ORD-20240627-0001")
                .customer(customer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .paymentMode(Order.PaymentMode.COD)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .orderDate(LocalDate.now())
                .build();

        // Then
        assertNotNull(order);
        assertEquals("ORD-20240627-0001", order.getOrderNumber());
        assertEquals(Order.OrderStatus.NEW, order.getStatus());
        assertEquals(Order.OrderSource.MANUAL, order.getOrderSource());
        assertEquals(customer, order.getCustomer());
    }

    @Test
    void testOrderItemEntityCreation() {
        // Given
        Product product = Product.builder()
                .sku("PROD-001")
                .name("Test Product")
                .mrp(new BigDecimal("100.00"))
                .salePrice(new BigDecimal("90.00"))
                .build();

        OrderItem item = OrderItem.builder()
                .product(product)
                .productNameSnapshot("Test Product")
                .skuSnapshot("PROD-001")
                .quantity(2)
                .unitPrice(new BigDecimal("90.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .build();

        // When
        item.calculateLineTotal();

        // Then
        assertNotNull(item);
        assertEquals(2, item.getQuantity());
        assertEquals(new BigDecimal("180.00"), item.getLineTotal());
    }

    @Test
    void testProductEntityCreation() {
        // Given
        Product product = Product.builder()
                .sku("PROD-001")
                .name("Ayurvedic Oil")
                .category("Oils")
                .mrp(new BigDecimal("500.00"))
                .salePrice(new BigDecimal("450.00"))
                .stockQuantity(100)
                .lowStockThreshold(10)
                .isActive(true)
                .build();

        // Then
        assertNotNull(product);
        assertEquals("PROD-001", product.getSku());
        assertEquals("Ayurvedic Oil", product.getName());
        assertEquals(100, product.getStockQuantity());
        assertTrue(product.getIsActive());
    }

    @Test
    void testCustomerEntityCreation() {
        // Given
        Customer customer = Customer.builder()
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .addressLine1("123 Main St")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .build();

        // Then
        assertNotNull(customer);
        assertEquals("John Doe", customer.getName());
        assertEquals("9876543210", customer.getPhone());
        assertEquals("Mumbai", customer.getCity());
    }

    @Test
    void testPaymentRecordEntityCreation() {
        // Given
        Order order = Order.builder()
                .orderNumber("ORD-20240627-0001")
                .totalAmount(new BigDecimal("1000.00"))
                .build();

        PaymentRecord payment = PaymentRecord.builder()
                .order(order)
                .amount(new BigDecimal("500.00"))
                .paymentMode(Order.PaymentMode.UPI)
                .transactionReference("TXN123456")
                .paymentDate(LocalDateTime.now())
                .recordedBy(1L)
                .notes("Partial payment received")
                .build();

        // Then
        assertNotNull(payment);
        assertEquals(new BigDecimal("500.00"), payment.getAmount());
        assertEquals(Order.PaymentMode.UPI, payment.getPaymentMode());
        assertEquals("TXN123456", payment.getTransactionReference());
        assertEquals(1L, payment.getRecordedBy());
    }

    @Test
    void testSalespersonEntityCreation() {
        // Given
        Salesperson salesperson = Salesperson.builder()
                .employeeCode("EMP-001")
                .name("Sales Person 1")
                .phone("9876543210")
                .email("sales1@example.com")
                .status(Salesperson.SalespersonStatus.ACTIVE)
                .commissionRate(new BigDecimal("5.00"))
                .platformUserId(1L)
                .joiningDate(LocalDate.now())
                .build();

        // Then
        assertNotNull(salesperson);
        assertEquals("EMP-001", salesperson.getEmployeeCode());
        assertEquals("Sales Person 1", salesperson.getName());
        assertEquals(Salesperson.SalespersonStatus.ACTIVE, salesperson.getStatus());
        assertEquals(new BigDecimal("5.00"), salesperson.getCommissionRate());
        assertEquals(1L, salesperson.getPlatformUserId());
    }

    @Test
    void testOrderStatusHistoryEntityCreation() {
        // Given
        Order order = Order.builder()
                .orderNumber("ORD-20240627-0001")
                .status(Order.OrderStatus.CONFIRMED)
                .build();

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus("NEW")
                .toStatus("CONFIRMED")
                .changedBy(1L)
                .notes("Order confirmed by admin")
                .build();

        // Then
        assertNotNull(history);
        assertEquals("NEW", history.getFromStatus());
        assertEquals("CONFIRMED", history.getToStatus());
        assertEquals(1L, history.getChangedBy());
        assertEquals("Order confirmed by admin", history.getNotes());
    }

    @Test
    void testStockHistoryEntityCreation() {
        // Given
        Product product = Product.builder()
                .sku("PROD-001")
                .name("Test Product")
                .stockQuantity(100)
                .build();

        StockHistory stockHistory = StockHistory.builder()
                .product(product)
                .operation(StockHistory.StockOperation.STOCK_OUT)
                .quantityBefore(100)
                .quantityChanged(-10)
                .quantityAfter(90)
                .referenceType("ORDER")
                .referenceId(1L)
                .performedBy(1L)
                .notes("Stock reduced for order")
                .build();

        // Then
        assertNotNull(stockHistory);
        assertEquals(StockHistory.StockOperation.STOCK_OUT, stockHistory.getOperation());
        assertEquals(100, stockHistory.getQuantityBefore());
        assertEquals(-10, stockHistory.getQuantityChanged());
        assertEquals(90, stockHistory.getQuantityAfter());
        assertEquals("ORDER", stockHistory.getReferenceType());
        assertEquals(1L, stockHistory.getReferenceId());
    }

    @Test
    void testOrderTotalCalculation() {
        // Given
        Order order = Order.builder()
                .orderNumber("ORD-20240627-0001")
                .subtotal(BigDecimal.ZERO)
                .discountAmount(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("18.00"))
                .shippingCharge(new BigDecimal("30.00"))
                .build();

        Product product = Product.builder()
                .sku("PROD-001")
                .name("Test Product")
                .salePrice(new BigDecimal("100.00"))
                .build();

        OrderItem item1 = OrderItem.builder()
                .product(product)
                .productNameSnapshot("Test Product")
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .build();
        item1.calculateLineTotal();

        order.addItem(item1);

        // When
        order.recalculateTotals();

        // Then
        assertEquals(new BigDecimal("200.00"), order.getSubtotal());
        // totalAmount = 200 - 50 + 18 + 30 = 198
        assertEquals(new BigDecimal("198.00"), order.getTotalAmount());
    }

    @Test
    void testOrderItemLineCalculation() {
        // Given
        OrderItem item = OrderItem.builder()
                .quantity(3)
                .unitPrice(new BigDecimal("100.00"))
                .discount(new BigDecimal("30.00"))
                .taxAmount(new BigDecimal("15.00"))
                .build();

        // When
        item.calculateLineTotal();

        // Then
        // lineTotal = (3 * 100) - 30 + 15 = 285
        assertEquals(new BigDecimal("285.00"), item.getLineTotal());
    }

    @Test
    void testOrderItemLineCalculationWithDecimalPrecision() {
        // Given - Test Requirement 4.3 and 4.4: 2 decimal place precision
        OrderItem item = OrderItem.builder()
                .quantity(3)
                .unitPrice(new BigDecimal("99.99"))
                .discount(new BigDecimal("25.50"))
                .taxAmount(new BigDecimal("18.75"))
                .build();

        // When
        item.calculateLineTotal();

        // Then
        // lineTotal = (3 * 99.99) - 25.50 + 18.75 = 299.97 - 25.50 + 18.75 = 293.22
        assertEquals(new BigDecimal("293.22"), item.getLineTotal());
        assertEquals(2, item.getLineTotal().scale());
    }

    @Test
    void testOrderTotalCalculationWithMultipleItems() {
        // Given - Test Requirement 4.1: Subtotal from sum of line totals
        Order order = Order.builder()
                .orderNumber("ORD-20240627-0002")
                .discountAmount(new BigDecimal("10.00"))
                .taxAmount(new BigDecimal("25.50"))
                .shippingCharge(new BigDecimal("50.00"))
                .build();

        OrderItem item1 = OrderItem.builder()
                .productNameSnapshot("Product 1")
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .build();
        item1.calculateLineTotal();

        OrderItem item2 = OrderItem.builder()
                .productNameSnapshot("Product 2")
                .quantity(3)
                .unitPrice(new BigDecimal("50.00"))
                .discount(new BigDecimal("10.00"))
                .taxAmount(new BigDecimal("5.00"))
                .build();
        item2.calculateLineTotal();

        order.addItem(item1);
        order.addItem(item2);

        // When
        order.recalculateTotals();

        // Then - Requirement 4.1: Subtotal is sum of all line totals
        // item1 lineTotal = 2 * 100 = 200.00
        // item2 lineTotal = 3 * 50 - 10 + 5 = 145.00
        // subtotal = 200 + 145 = 345.00
        assertEquals(new BigDecimal("345.00"), order.getSubtotal());
        
        // Requirement 4.2: totalAmount = subtotal - discount + tax + shipping
        // totalAmount = 345 - 10 + 25.50 + 50 = 410.50
        assertEquals(new BigDecimal("410.50"), order.getTotalAmount());
        assertEquals(2, order.getTotalAmount().scale());
    }

    @Test
    void testOrderTotalCalculationWithNoItems() {
        // Given - Test Requirement 4.5: Zero subtotal when no items
        Order order = Order.builder()
                .orderNumber("ORD-20240627-0003")
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .build();

        // When
        order.recalculateTotals();

        // Then
        assertEquals(new BigDecimal("0.00"), order.getSubtotal());
        assertEquals(new BigDecimal("0.00"), order.getTotalAmount());
    }

    @Test
    void testOrderTotalCalculationWithNullValues() {
        // Given - Test handling of null discount, tax, and shipping
        Order order = Order.builder()
                .orderNumber("ORD-20240627-0004")
                .discountAmount(null)
                .taxAmount(null)
                .shippingCharge(null)
                .build();

        OrderItem item = OrderItem.builder()
                .productNameSnapshot("Product 1")
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .discount(null)
                .taxAmount(null)
                .build();
        item.calculateLineTotal();

        order.addItem(item);

        // When
        order.recalculateTotals();

        // Then
        assertEquals(new BigDecimal("100.00"), order.getSubtotal());
        assertEquals(new BigDecimal("100.00"), order.getTotalAmount());
    }

    @Test
    void testCalculateOrderTotalsAliasMethod() {
        // Given - Test that calculateOrderTotals() alias works correctly
        Order order = Order.builder()
                .orderNumber("ORD-20240627-0005")
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .build();

        OrderItem item = OrderItem.builder()
                .productNameSnapshot("Product 1")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .build();
        item.calculateLineTotal();

        order.addItem(item);

        // When - Using the alias method
        order.calculateOrderTotals();

        // Then
        assertEquals(new BigDecimal("100.00"), order.getSubtotal());
        assertEquals(new BigDecimal("100.00"), order.getTotalAmount());
    }

    @Test
    void testDecimalPrecisionRounding() {
        // Given - Test that rounding is applied correctly
        OrderItem item = OrderItem.builder()
                .quantity(3)
                .unitPrice(new BigDecimal("33.333"))
                .discount(new BigDecimal("0.001"))
                .taxAmount(new BigDecimal("5.555"))
                .build();

        // When
        item.calculateLineTotal();

        // Then - Should round to 2 decimal places using HALF_UP
        // lineTotal = (3 * 33.333) - 0.001 + 5.555 = 99.999 - 0.001 + 5.555 = 105.553 -> 105.55
        assertEquals(new BigDecimal("105.55"), item.getLineTotal());
        assertEquals(2, item.getLineTotal().scale());
    }

    @Test
    void testSalespersonStatusEnum() {
        // Test all enum values
        assertEquals(3, Salesperson.SalespersonStatus.values().length);
        assertNotNull(Salesperson.SalespersonStatus.valueOf("ACTIVE"));
        assertNotNull(Salesperson.SalespersonStatus.valueOf("INACTIVE"));
        assertNotNull(Salesperson.SalespersonStatus.valueOf("ON_LEAVE"));
    }

    @Test
    void testStockOperationEnum() {
        // Test all enum values
        assertEquals(4, StockHistory.StockOperation.values().length);
        assertNotNull(StockHistory.StockOperation.valueOf("STOCK_IN"));
        assertNotNull(StockHistory.StockOperation.valueOf("STOCK_OUT"));
        assertNotNull(StockHistory.StockOperation.valueOf("ADJUSTMENT"));
        assertNotNull(StockHistory.StockOperation.valueOf("RETURN"));
    }

    @Test
    void testOrderStatusEnum() {
        // Test all enum values
        assertEquals(8, Order.OrderStatus.values().length);
        assertNotNull(Order.OrderStatus.valueOf("NEW"));
        assertNotNull(Order.OrderStatus.valueOf("CONFIRMED"));
        assertNotNull(Order.OrderStatus.valueOf("PAID"));
        assertNotNull(Order.OrderStatus.valueOf("PACKED"));
        assertNotNull(Order.OrderStatus.valueOf("DISPATCHED"));
        assertNotNull(Order.OrderStatus.valueOf("DELIVERED"));
        assertNotNull(Order.OrderStatus.valueOf("CANCELLED"));
        assertNotNull(Order.OrderStatus.valueOf("RETURNED"));
    }

    @Test
    void testPaymentModeEnum() {
        // Test all enum values
        assertEquals(5, Order.PaymentMode.values().length);
        assertNotNull(Order.PaymentMode.valueOf("COD"));
        assertNotNull(Order.PaymentMode.valueOf("UPI"));
        assertNotNull(Order.PaymentMode.valueOf("BANK_TRANSFER"));
        assertNotNull(Order.PaymentMode.valueOf("ONLINE"));
        assertNotNull(Order.PaymentMode.valueOf("CREDIT"));
    }

    @Test
    void testPaymentStatusEnum() {
        // Test all enum values
        assertEquals(4, Order.PaymentStatus.values().length);
        assertNotNull(Order.PaymentStatus.valueOf("PENDING"));
        assertNotNull(Order.PaymentStatus.valueOf("PARTIAL"));
        assertNotNull(Order.PaymentStatus.valueOf("PAID"));
        assertNotNull(Order.PaymentStatus.valueOf("REFUNDED"));
    }

    @Test
    void testOrderSourceEnum() {
        // Test all enum values
        assertEquals(4, Order.OrderSource.values().length);
        assertNotNull(Order.OrderSource.valueOf("WHATSAPP"));
        assertNotNull(Order.OrderSource.valueOf("MANUAL"));
        assertNotNull(Order.OrderSource.valueOf("STOREFRONT"));
        assertNotNull(Order.OrderSource.valueOf("API"));
    }
}
