package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.repository.BillingExportRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillingExportService Vyapar export methods.
 * Tests the three export methods: exportToVyaparFormat, exportDailyOrdersToVyapar, exportMonthlyOrdersToVyapar.
 * 
 * Tests Requirements:
 * - 16.2: Export orders in Vyapar-compatible CSV format
 * - 16.3: Support daily order exports for a specific date
 * - 16.4: Support monthly order exports for a specific month
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Billing Export - Vyapar Export Methods Tests")
class BillingExportVyaparExportMethodsTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private BillingExportRepository billingExportRepository;

    @InjectMocks
    private BillingExportService billingExportService;

    private List<Order> testOrders;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .phone("9876543210")
                .email("test@example.com")
                .addressLine1("123 Test Street")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .createdAt(LocalDateTime.now())
                .build();

        testOrders = new ArrayList<>();
        testOrders.add(createTestOrder(1L, "ORD-20240115-0001", LocalDate.of(2024, 1, 15), Order.OrderStatus.DELIVERED));
        testOrders.add(createTestOrder(2L, "ORD-20240115-0002", LocalDate.of(2024, 1, 15), Order.OrderStatus.PAID));
    }

    private Order createTestOrder(Long id, String orderNumber, LocalDate orderDate, Order.OrderStatus status) {
        Order order = Order.builder()
                .id(id)
                .orderNumber(orderNumber)
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(status)
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

        OrderItem item = OrderItem.builder()
                .id(id)
                .order(order)
                .productNameSnapshot("Test Product")
                .skuSnapshot("TEST-001")
                .quantity(2)
                .unitPrice(new BigDecimal("500.00"))
                .mrpSnapshot(new BigDecimal("600.00"))
                .discount(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("85.50"))
                .lineTotal(new BigDecimal("1035.50"))
                .build();

        order.getItems().add(item);
        return order;
    }

    // ==================== exportToVyaparFormat Tests ====================

    @Test
    @DisplayName("Should export selected orders to CSV format - Requirement 16.2")
    void testExportToVyaparFormat_Success() throws Exception {
        // Arrange
        List<Long> orderIds = Arrays.asList(1L, 2L);
        when(orderRepository.findAllById(orderIds)).thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(orderIds);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);

        // Verify CSV content
        String csvContent = new String(result);
        assertTrue(csvContent.contains("Invoice No"));
        assertTrue(csvContent.contains("ORD-20240115-0001"));
        assertTrue(csvContent.contains("ORD-20240115-0002"));
        assertTrue(csvContent.contains("Test Customer"));
        assertTrue(csvContent.contains("9876543210"));

        verify(orderRepository, times(1)).findAllById(orderIds);
    }

    @Test
    @DisplayName("Should handle empty order IDs list")
    void testExportToVyaparFormat_EmptyList() {
        // Arrange
        List<Long> emptyList = Collections.emptyList();

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(emptyList);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.length);
        verify(orderRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Should handle null order IDs list")
    void testExportToVyaparFormat_NullList() {
        // Act
        byte[] result = billingExportService.exportToVyaparFormat(null);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.length);
        verify(orderRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Should return empty when no orders found for IDs")
    void testExportToVyaparFormat_NoOrdersFound() {
        // Arrange
        List<Long> orderIds = Arrays.asList(999L, 1000L);
        when(orderRepository.findAllById(orderIds)).thenReturn(Collections.emptyList());

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(orderIds);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.length);
        verify(orderRepository, times(1)).findAllById(orderIds);
    }

    @Test
    @DisplayName("Should skip cancelled orders in export")
    void testExportToVyaparFormat_SkipCancelledOrders() throws Exception {
        // Arrange
        Order cancelledOrder = createTestOrder(3L, "ORD-20240115-0003", LocalDate.of(2024, 1, 15), Order.OrderStatus.CANCELLED);
        testOrders.add(cancelledOrder);
        
        List<Long> orderIds = Arrays.asList(1L, 2L, 3L);
        when(orderRepository.findAllById(orderIds)).thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(orderIds);

        // Assert
        String csvContent = new String(result);
        assertFalse(csvContent.contains("ORD-20240115-0003"), "Cancelled order should not be in export");
        assertTrue(csvContent.contains("ORD-20240115-0001"));
        assertTrue(csvContent.contains("ORD-20240115-0002"));
    }

    @Test
    @DisplayName("Should include all required CSV columns - Requirement 16.4")
    void testExportToVyaparFormat_CSVFormat() throws Exception {
        // Arrange
        List<Long> orderIds = Arrays.asList(1L);
        when(orderRepository.findAllById(orderIds)).thenReturn(Collections.singletonList(testOrders.get(0)));

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(orderIds);

        // Assert
        String csvContent = new String(result);
        String[] lines = csvContent.split("\n");
        
        // Check header
        assertTrue(lines.length >= 2, "CSV should have header + at least one data row");
        String header = lines[0];
        assertTrue(header.contains("Invoice No"));
        assertTrue(header.contains("Customer Name"));
        assertTrue(header.contains("Customer Phone"));
        assertTrue(header.contains("Product Name"));
        assertTrue(header.contains("SKU"));
        assertTrue(header.contains("Quantity"));
        assertTrue(header.contains("Unit Price"));
        assertTrue(header.contains("Subtotal"));
        assertTrue(header.contains("Total Amount"));
        assertTrue(header.contains("Payment Mode"));
        assertTrue(header.contains("Payment Status"));
    }

    // ==================== exportDailyOrdersToVyapar Tests ====================

    @Test
    @DisplayName("Should export daily orders for specific date - Requirement 16.3")
    void testExportDailyOrdersToVyapar_Success() throws Exception {
        // Arrange
        LocalDate targetDate = LocalDate.of(2024, 1, 15);
        when(orderRepository.findByOrderDate(targetDate)).thenReturn(testOrders);

        // Act
        byte[] result = billingExportService.exportDailyOrdersToVyapar(targetDate);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);

        String csvContent = new String(result);
        assertTrue(csvContent.contains("ORD-20240115-0001"));
        assertTrue(csvContent.contains("ORD-20240115-0002"));

        verify(orderRepository, times(1)).findByOrderDate(targetDate);
    }

    @Test
    @DisplayName("Should return empty CSV when no orders for date")
    void testExportDailyOrdersToVyapar_NoOrders() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2024, 12, 25);
        when(orderRepository.findByOrderDate(targetDate)).thenReturn(Collections.emptyList());

        // Act
        byte[] result = billingExportService.exportDailyOrdersToVyapar(targetDate);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.length);
        verify(orderRepository, times(1)).findByOrderDate(targetDate);
    }

    @Test
    @DisplayName("Should throw exception when date is null")
    void testExportDailyOrdersToVyapar_NullDate() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> billingExportService.exportDailyOrdersToVyapar(null)
        );
        
        assertEquals("Date cannot be null", exception.getMessage());
        verify(orderRepository, never()).findByOrderDate(any());
    }

    @Test
    @DisplayName("Should export only non-cancelled orders for specific date")
    void testExportDailyOrdersToVyapar_ExcludeCancelled() throws Exception {
        // Arrange
        Order cancelledOrder = createTestOrder(3L, "ORD-20240115-0003", LocalDate.of(2024, 1, 15), Order.OrderStatus.CANCELLED);
        List<Order> ordersWithCancelled = new ArrayList<>(testOrders);
        ordersWithCancelled.add(cancelledOrder);

        LocalDate targetDate = LocalDate.of(2024, 1, 15);
        when(orderRepository.findByOrderDate(targetDate)).thenReturn(ordersWithCancelled);

        // Act
        byte[] result = billingExportService.exportDailyOrdersToVyapar(targetDate);

        // Assert
        String csvContent = new String(result);
        assertFalse(csvContent.contains("ORD-20240115-0003"));
        assertTrue(csvContent.contains("ORD-20240115-0001"));
    }

    // ==================== exportMonthlyOrdersToVyapar Tests ====================

    @Test
    @DisplayName("Should export monthly orders for specific month - Requirement 16.3")
    void testExportMonthlyOrdersToVyapar_Success() throws Exception {
        // Arrange
        YearMonth targetMonth = YearMonth.of(2024, 1);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        
        Order order3 = createTestOrder(3L, "ORD-20240110-0001", LocalDate.of(2024, 1, 10), Order.OrderStatus.DELIVERED);
        Order order4 = createTestOrder(4L, "ORD-20240120-0001", LocalDate.of(2024, 1, 20), Order.OrderStatus.PAID);
        List<Order> monthlyOrders = Arrays.asList(testOrders.get(0), testOrders.get(1), order3, order4);

        when(orderRepository.findByOrderDateBetween(startDate, endDate)).thenReturn(monthlyOrders);

        // Act
        byte[] result = billingExportService.exportMonthlyOrdersToVyapar(targetMonth);

        // Assert
        assertNotNull(result);
        assertTrue(result.length > 0);

        String csvContent = new String(result);
        assertTrue(csvContent.contains("ORD-20240115-0001"));
        assertTrue(csvContent.contains("ORD-20240115-0002"));
        assertTrue(csvContent.contains("ORD-20240110-0001"));
        assertTrue(csvContent.contains("ORD-20240120-0001"));

        verify(orderRepository, times(1)).findByOrderDateBetween(startDate, endDate);
    }

    @Test
    @DisplayName("Should handle month with no orders")
    void testExportMonthlyOrdersToVyapar_NoOrders() {
        // Arrange
        YearMonth targetMonth = YearMonth.of(2024, 12);
        LocalDate startDate = LocalDate.of(2024, 12, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);

        when(orderRepository.findByOrderDateBetween(startDate, endDate)).thenReturn(Collections.emptyList());

        // Act
        byte[] result = billingExportService.exportMonthlyOrdersToVyapar(targetMonth);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.length);
        verify(orderRepository, times(1)).findByOrderDateBetween(startDate, endDate);
    }

    @Test
    @DisplayName("Should throw exception when month is null")
    void testExportMonthlyOrdersToVyapar_NullMonth() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> billingExportService.exportMonthlyOrdersToVyapar(null)
        );
        
        assertEquals("Month cannot be null", exception.getMessage());
        verify(orderRepository, never()).findByOrderDateBetween(any(), any());
    }

    @Test
    @DisplayName("Should handle February correctly (different month length)")
    void testExportMonthlyOrdersToVyapar_February() throws Exception {
        // Arrange - February 2024 (leap year)
        YearMonth targetMonth = YearMonth.of(2024, 2);
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 29); // Leap year

        Order febOrder = createTestOrder(5L, "ORD-20240229-0001", LocalDate.of(2024, 2, 29), Order.OrderStatus.DELIVERED);
        when(orderRepository.findByOrderDateBetween(startDate, endDate)).thenReturn(Collections.singletonList(febOrder));

        // Act
        byte[] result = billingExportService.exportMonthlyOrdersToVyapar(targetMonth);

        // Assert
        String csvContent = new String(result);
        assertTrue(csvContent.contains("ORD-20240229-0001"));
        verify(orderRepository, times(1)).findByOrderDateBetween(startDate, endDate);
    }

    @Test
    @DisplayName("Should exclude cancelled orders from monthly export")
    void testExportMonthlyOrdersToVyapar_ExcludeCancelled() throws Exception {
        // Arrange
        YearMonth targetMonth = YearMonth.of(2024, 1);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        Order cancelledOrder = createTestOrder(3L, "ORD-20240116-0001", LocalDate.of(2024, 1, 16), Order.OrderStatus.CANCELLED);
        List<Order> ordersWithCancelled = new ArrayList<>(testOrders);
        ordersWithCancelled.add(cancelledOrder);

        when(orderRepository.findByOrderDateBetween(startDate, endDate)).thenReturn(ordersWithCancelled);

        // Act
        byte[] result = billingExportService.exportMonthlyOrdersToVyapar(targetMonth);

        // Assert
        String csvContent = new String(result);
        assertFalse(csvContent.contains("ORD-20240116-0001"));
        assertTrue(csvContent.contains("ORD-20240115-0001"));
    }

    // ==================== CSV Format Validation Tests ====================

    @Test
    @DisplayName("Should properly escape CSV special characters")
    void testCSVEscaping() throws Exception {
        // Arrange
        Customer specialCustomer = Customer.builder()
                .id(2L)
                .name("Customer, With \"Comma\"")
                .phone("9876543210")
                .email("test@example.com")
                .addressLine1("Address with, comma")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .createdAt(LocalDateTime.now())
                .build();

        Order specialOrder = createTestOrder(5L, "ORD-20240115-0005", LocalDate.of(2024, 1, 15), Order.OrderStatus.DELIVERED);
        specialOrder.setCustomer(specialCustomer);
        specialOrder.getItems().get(0).setProductNameSnapshot("Product \"Special\" Name, Test");

        List<Long> orderIds = Arrays.asList(5L);
        when(orderRepository.findAllById(orderIds)).thenReturn(Collections.singletonList(specialOrder));

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(orderIds);

        // Assert
        String csvContent = new String(result);
        assertTrue(csvContent.contains("\"Customer, With \"\"Comma\"\"\""));
        assertTrue(csvContent.contains("\"Product \"\"Special\"\" Name, Test\""));
    }

    @Test
    @DisplayName("Should format decimal values correctly in CSV")
    void testDecimalFormatting() throws Exception {
        // Arrange
        Order order = createTestOrder(1L, "ORD-20240115-0001", LocalDate.of(2024, 1, 15), Order.OrderStatus.DELIVERED);
        order.setSubtotal(new BigDecimal("1234.56"));
        order.setTotalAmount(new BigDecimal("1357.89"));

        List<Long> orderIds = Arrays.asList(1L);
        when(orderRepository.findAllById(orderIds)).thenReturn(Collections.singletonList(order));

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(orderIds);

        // Assert
        String csvContent = new String(result);
        assertTrue(csvContent.contains("1234.56"));
        assertTrue(csvContent.contains("1357.89"));
    }

    @Test
    @DisplayName("Should generate one row per order line item")
    void testMultipleLineItems() throws Exception {
        // Arrange
        Order order = createTestOrder(1L, "ORD-20240115-0001", LocalDate.of(2024, 1, 15), Order.OrderStatus.DELIVERED);
        
        OrderItem item2 = OrderItem.builder()
                .id(2L)
                .order(order)
                .productNameSnapshot("Second Product")
                .skuSnapshot("TEST-002")
                .quantity(1)
                .unitPrice(new BigDecimal("300.00"))
                .mrpSnapshot(new BigDecimal("350.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(new BigDecimal("27.00"))
                .lineTotal(new BigDecimal("327.00"))
                .build();
        
        order.getItems().add(item2);

        List<Long> orderIds = Arrays.asList(1L);
        when(orderRepository.findAllById(orderIds)).thenReturn(Collections.singletonList(order));

        // Act
        byte[] result = billingExportService.exportToVyaparFormat(orderIds);

        // Assert
        String csvContent = new String(result);
        String[] lines = csvContent.split("\n");
        
        // Header + 2 line items
        assertEquals(3, lines.length, "Should have header + 2 data rows");
        assertTrue(lines[1].contains("Test Product"));
        assertTrue(lines[2].contains("Second Product"));
    }
}
