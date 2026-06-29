package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.entity.BillingExport;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.repository.BillingExportRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillingExportService Vyapar export functionality.
 * Tests the exportVyaparCsv method and related date-based export operations.
 * 
 * Tests Requirement 16: Vyapar Billing Export
 * - 16.1: Map order data to Vyapar CSV format
 * - 16.2: Include customer name, address, product details, quantities, prices, and totals
 * - 16.3: Export daily orders for specific date
 * - 16.4: Export monthly orders for specific month
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Billing Export - Vyapar CSV Export Tests")
class BillingExportVyaparExportTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BillingExportRepository billingExportRepository;

    @InjectMocks
    private BillingExportService billingExportService;

    private List<Order> testOrders;
    private Customer testCustomer;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2024, 1, 15);
        
        // Setup test customer
        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .addressLine1("123 Main Street")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .createdAt(LocalDateTime.now())
                .build();

        testOrders = new ArrayList<>();
    }

    @Test
    @DisplayName("Should export multiple orders to Vyapar CSV format with correct headers")
    void testExportVyaparCsv_MultipleOrders() throws Exception {
        // Arrange
        Order order1 = createTestOrder("ORD-20240115-0001", 2);
        Order order2 = createTestOrder("ORD-20240115-0002", 1);
        testOrders.add(order1);
        testOrders.add(order2);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0, "CSV should not be empty");

        // Parse and verify CSV content
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        
        String header = reader.readLine();
        assertNotNull(header);
        assertEquals("Invoice No,Date,Customer Name,Phone,Product,SKU,Qty,Rate,Discount,Tax,Line Total,Order Total,Payment Mode,Payment Status", 
                header, "CSV header should match expected format");

        // Verify order 1 lines (2 items)
        String line1 = reader.readLine();
        assertNotNull(line1);
        assertTrue(line1.startsWith("ORD-20240115-0001"));
        assertTrue(line1.contains("John Doe"));
        assertTrue(line1.contains("9876543210"));

        String line2 = reader.readLine();
        assertNotNull(line2);
        assertTrue(line2.startsWith("ORD-20240115-0001"));

        // Verify order 2 lines (1 item)
        String line3 = reader.readLine();
        assertNotNull(line3);
        assertTrue(line3.startsWith("ORD-20240115-0002"));

        reader.close();
    }

    @Test
    @DisplayName("Should export daily orders for specific date")
    void testExportVyaparCsv_DailyExport() {
        // Arrange
        LocalDate specificDate = LocalDate.of(2024, 1, 20);
        Order order = createTestOrder("ORD-20240120-0001", 1);
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(specificDate, specificDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(specificDate, specificDate, 1L);

        // Assert
        assertNotNull(result);
        verify(orderRepository).findByOrderDateBetween(specificDate, specificDate);
        
        // Verify billing export record was created
        ArgumentCaptor<BillingExport> exportCaptor = ArgumentCaptor.forClass(BillingExport.class);
        verify(billingExportRepository).save(exportCaptor.capture());
        
        BillingExport savedExport = exportCaptor.getValue();
        assertEquals(BillingExport.ExportType.VYAPAR_CSV, savedExport.getExportType());
        assertEquals(specificDate, savedExport.getDateRangeStart());
        assertEquals(specificDate, savedExport.getDateRangeEnd());
        assertEquals(1, savedExport.getRecordCount());
        assertEquals(1L, savedExport.getGeneratedBy());
    }

    @Test
    @DisplayName("Should export monthly orders for specific month")
    void testExportVyaparCsv_MonthlyExport() {
        // Arrange
        LocalDate monthStart = LocalDate.of(2024, 1, 1);
        LocalDate monthEnd = LocalDate.of(2024, 1, 31);
        
        Order order1 = createTestOrderWithDate("ORD-20240105-0001", LocalDate.of(2024, 1, 5), 1);
        Order order2 = createTestOrderWithDate("ORD-20240115-0001", LocalDate.of(2024, 1, 15), 2);
        Order order3 = createTestOrderWithDate("ORD-20240125-0001", LocalDate.of(2024, 1, 25), 1);
        testOrders.addAll(Arrays.asList(order1, order2, order3));

        when(orderRepository.findByOrderDateBetween(monthStart, monthEnd))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(monthStart, monthEnd, 1L);

        // Assert
        assertNotNull(result);
        verify(orderRepository).findByOrderDateBetween(monthStart, monthEnd);
        
        // Verify billing export record
        ArgumentCaptor<BillingExport> exportCaptor = ArgumentCaptor.forClass(BillingExport.class);
        verify(billingExportRepository).save(exportCaptor.capture());
        
        BillingExport savedExport = exportCaptor.getValue();
        assertEquals(3, savedExport.getRecordCount(), "Should count 3 orders exported");
        assertEquals(monthStart, savedExport.getDateRangeStart());
        assertEquals(monthEnd, savedExport.getDateRangeEnd());
    }

    @Test
    @DisplayName("Should exclude cancelled orders from export")
    void testExportVyaparCsv_ExcludeCancelledOrders() throws Exception {
        // Arrange
        Order deliveredOrder = createTestOrder("ORD-20240115-0001", 1);
        deliveredOrder.setStatus(Order.OrderStatus.DELIVERED);
        
        Order cancelledOrder = createTestOrder("ORD-20240115-0002", 1);
        cancelledOrder.setStatus(Order.OrderStatus.CANCELLED);
        
        testOrders.addAll(Arrays.asList(deliveredOrder, cancelledOrder));

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        
        String header = reader.readLine();
        String line1 = reader.readLine();
        String line2 = reader.readLine(); // Should be null as cancelled order is skipped
        
        assertNotNull(line1);
        assertTrue(line1.contains("ORD-20240115-0001"));
        assertNull(line2, "Cancelled orders should not be in CSV");
        
        reader.close();

        // Verify only 1 order counted
        ArgumentCaptor<BillingExport> exportCaptor = ArgumentCaptor.forClass(BillingExport.class);
        verify(billingExportRepository).save(exportCaptor.capture());
        assertEquals(1, exportCaptor.getValue().getRecordCount());
    }

    @Test
    @DisplayName("Should handle empty order list")
    void testExportVyaparCsv_EmptyOrders() throws Exception {
        // Arrange
        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(Collections.emptyList());

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        assertNotNull(result);
        
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        String header = reader.readLine();
        String line1 = reader.readLine();
        
        assertNotNull(header, "Header should be present");
        assertNull(line1, "No data lines should be present");
        
        reader.close();

        // Verify export record with 0 count
        ArgumentCaptor<BillingExport> exportCaptor = ArgumentCaptor.forClass(BillingExport.class);
        verify(billingExportRepository).save(exportCaptor.capture());
        assertEquals(0, exportCaptor.getValue().getRecordCount());
    }

    @Test
    @DisplayName("Should handle single order export")
    void testExportVyaparCsv_SingleOrder() throws Exception {
        // Arrange
        Order singleOrder = createTestOrder("ORD-20240115-0001", 1);
        testOrders.add(singleOrder);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        assertNotNull(result);
        
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String dataLine = reader.readLine();
        
        assertNotNull(dataLine);
        assertTrue(dataLine.contains("ORD-20240115-0001"));
        
        reader.close();
    }

    @Test
    @DisplayName("Should properly format CSV with commas in customer names")
    void testExportVyaparCsv_EscapeCommasInNames() throws Exception {
        // Arrange
        testCustomer.setName("Doe, John");
        Order order = createTestOrder("ORD-20240115-0001", 1);
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String dataLine = reader.readLine();
        
        assertNotNull(dataLine);
        assertTrue(dataLine.contains("\"Doe, John\""), "Name with comma should be quoted");
        
        reader.close();
    }

    @Test
    @DisplayName("Should include all order item details in CSV")
    void testExportVyaparCsv_OrderItemDetails() throws Exception {
        // Arrange
        Order order = createTestOrder("ORD-20240115-0001", 1);
        OrderItem item = order.getItems().get(0);
        item.setProductNameSnapshot("Test Product");
        item.setSkuSnapshot("TST-001");
        item.setQuantity(5);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setDiscount(new BigDecimal("10.00"));
        item.setTaxAmount(new BigDecimal("8.10"));
        item.setLineTotal(new BigDecimal("498.10"));
        
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String dataLine = reader.readLine();
        
        assertNotNull(dataLine);
        assertTrue(dataLine.contains("Test Product"), "Should contain product name");
        assertTrue(dataLine.contains("TST-001"), "Should contain SKU");
        assertTrue(dataLine.contains("5"), "Should contain quantity");
        assertTrue(dataLine.contains("100.00"), "Should contain unit price");
        assertTrue(dataLine.contains("10.00"), "Should contain discount");
        assertTrue(dataLine.contains("8.10"), "Should contain tax");
        assertTrue(dataLine.contains("498.10"), "Should contain line total");
        
        reader.close();
    }

    @Test
    @DisplayName("Should include order totals and payment information in CSV")
    void testExportVyaparCsv_OrderTotalsAndPayment() throws Exception {
        // Arrange
        Order order = createTestOrder("ORD-20240115-0001", 1);
        order.setTotalAmount(new BigDecimal("1500.00"));
        order.setPaymentMode(Order.PaymentMode.UPI);
        order.setPaymentStatus(Order.PaymentStatus.PAID);
        
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String dataLine = reader.readLine();
        
        assertNotNull(dataLine);
        assertTrue(dataLine.contains("1500.00"), "Should contain order total");
        assertTrue(dataLine.contains("UPI"), "Should contain payment mode");
        assertTrue(dataLine.contains("PAID"), "Should contain payment status");
        
        reader.close();
    }

    @Test
    @DisplayName("Should handle orders with null customer")
    void testExportVyaparCsv_NullCustomer() throws Exception {
        // Arrange
        Order order = createTestOrder("ORD-20240115-0001", 1);
        order.setCustomer(null);
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        assertNotNull(result);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String dataLine = reader.readLine();
        
        assertNotNull(dataLine);
        // Should have empty customer fields but still export order
        assertTrue(dataLine.contains("ORD-20240115-0001"));
        
        reader.close();
    }

    @Test
    @DisplayName("Should handle orders with null payment mode and status")
    void testExportVyaparCsv_NullPaymentInfo() throws Exception {
        // Arrange
        Order order = createTestOrder("ORD-20240115-0001", 1);
        order.setPaymentMode(null);
        order.setPaymentStatus(null);
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        assertNotNull(result);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String dataLine = reader.readLine();
        
        assertNotNull(dataLine);
        // CSV should still be valid with empty payment fields
        assertTrue(dataLine.contains("ORD-20240115-0001"));
        
        reader.close();
    }

    @Test
    @DisplayName("Should handle orders with null discount and tax amounts")
    void testExportVyaparCsv_NullOptionalAmounts() throws Exception {
        // Arrange
        Order order = createTestOrder("ORD-20240115-0001", 1);
        OrderItem item = order.getItems().get(0);
        item.setDiscount(null);
        item.setTaxAmount(null);
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        assertNotNull(result);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String dataLine = reader.readLine();
        
        assertNotNull(dataLine);
        assertTrue(dataLine.contains("0.00"), "Null amounts should default to 0.00");
        
        reader.close();
    }

    @Test
    @DisplayName("Should create proper billing export audit record")
    void testExportVyaparCsv_AuditRecord() {
        // Arrange
        Order order = createTestOrder("ORD-20240115-0001", 1);
        testOrders.add(order);

        LocalDate fromDate = LocalDate.of(2024, 1, 1);
        LocalDate toDate = LocalDate.of(2024, 1, 31);
        Long userId = 42L;

        when(orderRepository.findByOrderDateBetween(fromDate, toDate))
                .thenReturn(testOrders);

        // Act
        billingExportService.exportVyaparCsv(fromDate, toDate, userId);

        // Assert
        ArgumentCaptor<BillingExport> exportCaptor = ArgumentCaptor.forClass(BillingExport.class);
        verify(billingExportRepository).save(exportCaptor.capture());
        
        BillingExport savedExport = exportCaptor.getValue();
        assertEquals(BillingExport.ExportType.VYAPAR_CSV, savedExport.getExportType());
        assertEquals(fromDate, savedExport.getDateRangeStart());
        assertEquals(toDate, savedExport.getDateRangeEnd());
        assertEquals(1, savedExport.getRecordCount());
        assertEquals(userId, savedExport.getGeneratedBy());
        assertNotNull(savedExport.getGeneratedAt());
    }

    @Test
    @DisplayName("Should export orders with multiple items per order")
    void testExportVyaparCsv_MultipleItemsPerOrder() throws Exception {
        // Arrange
        Order order = createTestOrder("ORD-20240115-0001", 3);
        testOrders.add(order);

        when(orderRepository.findByOrderDateBetween(testDate, testDate))
                .thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportVyaparCsv(testDate, testDate, 1L);

        // Assert
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(result)));
        reader.readLine(); // header
        String line1 = reader.readLine();
        String line2 = reader.readLine();
        String line3 = reader.readLine();
        String line4 = reader.readLine(); // should be null
        
        assertNotNull(line1, "First item line should exist");
        assertNotNull(line2, "Second item line should exist");
        assertNotNull(line3, "Third item line should exist");
        assertNull(line4, "Should only have 3 item lines");
        
        // All lines should have same order number
        assertTrue(line1.startsWith("ORD-20240115-0001"));
        assertTrue(line2.startsWith("ORD-20240115-0001"));
        assertTrue(line3.startsWith("ORD-20240115-0001"));
        
        reader.close();
    }

    @Test
    @DisplayName("Should handle date range with no orders")
    void testExportVyaparCsv_NoOrdersInRange() {
        // Arrange
        LocalDate fromDate = LocalDate.of(2024, 2, 1);
        LocalDate toDate = LocalDate.of(2024, 2, 28);

        when(orderRepository.findByOrderDateBetween(fromDate, toDate))
                .thenReturn(Collections.emptyList());

        // Act
        byte[] result = billingExportService.exportVyaparCsv(fromDate, toDate, 1L);

        // Assert
        assertNotNull(result);
        verify(orderRepository).findByOrderDateBetween(fromDate, toDate);
        
        ArgumentCaptor<BillingExport> exportCaptor = ArgumentCaptor.forClass(BillingExport.class);
        verify(billingExportRepository).save(exportCaptor.capture());
        assertEquals(0, exportCaptor.getValue().getRecordCount());
    }

    // Helper methods

    private Order createTestOrder(String orderNumber, int itemCount) {
        return createTestOrderWithDate(orderNumber, testDate, itemCount);
    }

    private Order createTestOrderWithDate(String orderNumber, LocalDate orderDate, int itemCount) {
        Order order = Order.builder()
                .id(1L)
                .orderNumber(orderNumber)
                .customer(testCustomer)
                .salespersonId(1L)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.DELIVERED)
                .subtotal(new BigDecimal("1000.00"))
                .discountAmount(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("85.50"))
                .shippingCharge(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("1085.50"))
                .paymentMode(Order.PaymentMode.UPI)
                .paymentStatus(Order.PaymentStatus.PAID)
                .orderDate(orderDate)
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        for (int i = 0; i < itemCount; i++) {
            OrderItem item = OrderItem.builder()
                    .id((long) (i + 1))
                    .order(order)
                    .productNameSnapshot("Product " + (i + 1))
                    .skuSnapshot("SKU-00" + (i + 1))
                    .quantity(1)
                    .unitPrice(new BigDecimal("300.00"))
                    .mrpSnapshot(new BigDecimal("350.00"))
                    .discount(new BigDecimal("20.00"))
                    .taxAmount(new BigDecimal("25.20"))
                    .lineTotal(new BigDecimal("305.20"))
                    .build();
            order.getItems().add(item);
        }

        return order;
    }
}
