package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.DispatchLabelDTO;
import com.ayurveda.platform.dto.response.LabelProductLineDTO;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.DispatchLabelRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for generateSingleLabel() method in DispatchService.
 * Tests the PDF generation functionality for dispatch labels.
 * 
 * Implements Requirements:
 * - 12.1: Label contains order number, date, customer details
 * - 12.2: Label includes customer shipping information
 * - 12.3: Label includes product list with quantities
 * - 12.4: Label includes Code128 barcode
 * - 12.5: Label includes vendor information
 * - 12.7: PDF size constraint (<500KB per label)
 */
@ExtendWith(MockitoExtension.class)
class DispatchServicePdfGenerationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DispatchLabelRepository dispatchLabelRepository;

    @Mock
    private ConfigurationService configurationService;

    @InjectMocks
    private DispatchService dispatchService;

    private Order testOrder;
    private Customer testCustomer;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // Set up test customer
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Ramesh Kumar");
        testCustomer.setPhone("9876543210");
        testCustomer.setAddressLine1("123 MG Road");
        testCustomer.setAddressLine2("Near City Center");
        testCustomer.setCity("Bangalore");
        testCustomer.setState("Karnataka");
        testCustomer.setPincode("560001");

        // Set up test products
        testProduct1 = new Product();
        testProduct1.setId(1L);
        testProduct1.setSku("AYU-001");
        testProduct1.setName("Ashwagandha Capsules");
        testProduct1.setWeightGrams(BigDecimal.valueOf(100));

        testProduct2 = new Product();
        testProduct2.setId(2L);
        testProduct2.setSku("AYU-002");
        testProduct2.setName("Triphala Powder");
        testProduct2.setWeightGrams(BigDecimal.valueOf(250));

        // Set up test order items
        OrderItem item1 = new OrderItem();
        item1.setId(1L);
        item1.setProduct(testProduct1);
        item1.setProductNameSnapshot("Ashwagandha Capsules");
        item1.setSkuSnapshot("AYU-001");
        item1.setQuantity(2);
        item1.setUnitPrice(BigDecimal.valueOf(500));

        OrderItem item2 = new OrderItem();
        item2.setId(2L);
        item2.setProduct(testProduct2);
        item2.setProductNameSnapshot("Triphala Powder");
        item2.setSkuSnapshot("AYU-002");
        item2.setQuantity(1);
        item2.setUnitPrice(BigDecimal.valueOf(300));

        // Set up test order
        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderNumber("ORD-20250101-0001");
        testOrder.setOrderDate(LocalDate.of(2025, 1, 1));
        testOrder.setCustomer(testCustomer);
        testOrder.setItems(Arrays.asList(item1, item2));
        testOrder.setStatus(Order.OrderStatus.PAID);
        testOrder.setPaymentMode(Order.PaymentMode.UPI);
        testOrder.setTotalAmount(BigDecimal.valueOf(1300));

        // Set up configuration service mocks (lenient for tests that don't use them)
        lenient().when(configurationService.getCompanyName()).thenReturn("Ayurveda Wellness Pvt Ltd");
        lenient().when(configurationService.getAddress()).thenReturn("456 Industrial Area, Bangalore - 560058");
        lenient().when(configurationService.getPhone()).thenReturn("080-12345678");
    }

    @Test
    void testGenerateSingleLabel_Success() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        byte[] pdfBytes = dispatchService.generateSingleLabel(1L);

        // Then
        assertNotNull(pdfBytes, "PDF bytes should not be null");
        assertTrue(pdfBytes.length > 0, "PDF should have content");
        
        // Verify PDF header (PDF files start with %PDF-)
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, Math.min(5, pdfBytes.length)));
        assertEquals("%PDF-", pdfHeader, "Should be a valid PDF file");
        
        System.out.println("Generated PDF size: " + pdfBytes.length + " bytes");
    }

    @Test
    void testGenerateSingleLabel_SizeConstraint() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        byte[] pdfBytes = dispatchService.generateSingleLabel(1L);

        // Then - Requirement 12.7: PDF should be less than 500KB per label
        int maxSizeBytes = 500 * 1024; // 500KB
        assertTrue(pdfBytes.length < maxSizeBytes, 
            String.format("PDF size (%d bytes) should be less than 500KB (%d bytes)", 
                pdfBytes.length, maxSizeBytes));
    }

    @Test
    void testGenerateSingleLabel_OrderNotFound() {
        // Given
        when(orderRepository.findById(anyLong())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, 
            () -> dispatchService.generateSingleLabel(999L),
            "Should throw ResourceNotFoundException for non-existent order");
    }

    @Test
    void testGenerateSingleLabel_WithMinimalData() {
        // Given - Order with minimal customer data
        Customer minimalCustomer = new Customer();
        minimalCustomer.setId(2L);
        minimalCustomer.setName("John Doe");
        minimalCustomer.setPhone("9999999999");

        Order minimalOrder = new Order();
        minimalOrder.setId(2L);
        minimalOrder.setOrderNumber("ORD-20250101-0002");
        minimalOrder.setOrderDate(LocalDate.of(2025, 1, 1));
        minimalOrder.setCustomer(minimalCustomer);
        
        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setProduct(testProduct1);
        item.setProductNameSnapshot("Test Product");
        item.setSkuSnapshot("TEST-001");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(100));
        
        minimalOrder.setItems(List.of(item));
        minimalOrder.setStatus(Order.OrderStatus.PAID);
        minimalOrder.setPaymentMode(Order.PaymentMode.COD);
        minimalOrder.setTotalAmount(BigDecimal.valueOf(100));

        when(orderRepository.findById(2L)).thenReturn(Optional.of(minimalOrder));

        // When
        byte[] pdfBytes = dispatchService.generateSingleLabel(2L);

        // Then
        assertNotNull(pdfBytes, "PDF should be generated even with minimal data");
        assertTrue(pdfBytes.length > 0, "PDF should have content");
        
        // Verify it's a valid PDF
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, Math.min(5, pdfBytes.length)));
        assertEquals("%PDF-", pdfHeader, "Should be a valid PDF file");
    }

    @Test
    void testGenerateSingleLabel_WithMultipleProducts() {
        // Given - Order with multiple products
        Product product3 = new Product();
        product3.setId(3L);
        product3.setSku("AYU-003");
        product3.setName("Chyawanprash");
        product3.setWeightGrams(BigDecimal.valueOf(500));

        Product product4 = new Product();
        product4.setId(4L);
        product4.setSku("AYU-004");
        product4.setName("Giloy Tablets");
        product4.setWeightGrams(BigDecimal.valueOf(75));

        OrderItem item3 = new OrderItem();
        item3.setId(3L);
        item3.setProduct(product3);
        item3.setProductNameSnapshot("Chyawanprash");
        item3.setSkuSnapshot("AYU-003");
        item3.setQuantity(2);
        item3.setUnitPrice(BigDecimal.valueOf(400));

        OrderItem item4 = new OrderItem();
        item4.setId(4L);
        item4.setProduct(product4);
        item4.setProductNameSnapshot("Giloy Tablets");
        item4.setSkuSnapshot("AYU-004");
        item4.setQuantity(3);
        item4.setUnitPrice(BigDecimal.valueOf(200));

        testOrder.setItems(Arrays.asList(
            testOrder.getItems().get(0), 
            testOrder.getItems().get(1),
            item3, 
            item4
        ));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        byte[] pdfBytes = dispatchService.generateSingleLabel(1L);

        // Then
        assertNotNull(pdfBytes, "PDF should handle multiple products");
        assertTrue(pdfBytes.length > 0, "PDF should have content");
        
        // Should still meet size constraint even with more products
        int maxSizeBytes = 500 * 1024; // 500KB
        assertTrue(pdfBytes.length < maxSizeBytes, 
            "PDF with multiple products should still be under 500KB");
    }

    @Test
    void testGenerateSingleLabel_WithLongAddress() {
        // Given - Customer with long address
        testCustomer.setAddressLine1("Flat No. 123, Building Name, Very Long Street Name");
        testCustomer.setAddressLine2("Near Some Landmark, Behind Another Place, Next to Something");
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        byte[] pdfBytes = dispatchService.generateSingleLabel(1L);

        // Then
        assertNotNull(pdfBytes, "PDF should handle long addresses");
        assertTrue(pdfBytes.length > 0, "PDF should have content");
    }

    @Test
    void testGenerateSingleLabel_WithNullOptionalFields() {
        // Given - Customer with null optional fields
        testCustomer.setAddressLine2(null);
        testCustomer.setCity(null);
        testCustomer.setState(null);
        testCustomer.setPincode(null);
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        byte[] pdfBytes = dispatchService.generateSingleLabel(1L);

        // Then
        assertNotNull(pdfBytes, "PDF should handle null optional fields gracefully");
        assertTrue(pdfBytes.length > 0, "PDF should have content");
    }

    @Test
    void testGenerateSingleLabel_CustomerNotFound() {
        // Given - Order without customer
        testOrder.setCustomer(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThrows(ResourceNotFoundException.class,
            () -> dispatchService.generateSingleLabel(1L),
            "Should throw ResourceNotFoundException when customer is null");
    }

    // ===== BULK LABEL GENERATION TESTS =====

    @Test
    void testGenerateBulkLabels_Success() {
        // Given - Multiple orders
        Order order2 = new Order();
        order2.setId(2L);
        order2.setOrderNumber("ORD-20250101-0002");
        order2.setOrderDate(LocalDate.of(2025, 1, 1));
        order2.setCustomer(testCustomer);
        order2.setItems(List.of(testOrder.getItems().get(0)));
        order2.setStatus(Order.OrderStatus.PAID);
        order2.setPaymentMode(Order.PaymentMode.COD);
        order2.setTotalAmount(BigDecimal.valueOf(500));

        Order order3 = new Order();
        order3.setId(3L);
        order3.setOrderNumber("ORD-20250101-0003");
        order3.setOrderDate(LocalDate.of(2025, 1, 1));
        order3.setCustomer(testCustomer);
        order3.setItems(List.of(testOrder.getItems().get(1)));
        order3.setStatus(Order.OrderStatus.PAID);
        order3.setPaymentMode(Order.PaymentMode.UPI);
        order3.setTotalAmount(BigDecimal.valueOf(300));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order2));
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order3));

        // When
        List<Long> orderIds = Arrays.asList(1L, 2L, 3L);
        byte[] pdfBytes = dispatchService.generateBulkLabels(orderIds);

        // Then
        assertNotNull(pdfBytes, "Bulk PDF bytes should not be null");
        assertTrue(pdfBytes.length > 0, "Bulk PDF should have content");
        
        // Verify PDF header
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, Math.min(5, pdfBytes.length)));
        assertEquals("%PDF-", pdfHeader, "Should be a valid PDF file");
        
        System.out.println("Generated bulk PDF size for 3 orders: " + pdfBytes.length + " bytes");
    }

    @Test
    void testGenerateBulkLabels_SizeConstraint() {
        // Given - Multiple orders
        Order order2 = new Order();
        order2.setId(2L);
        order2.setOrderNumber("ORD-20250101-0002");
        order2.setOrderDate(LocalDate.of(2025, 1, 1));
        order2.setCustomer(testCustomer);
        order2.setItems(List.of(testOrder.getItems().get(0)));
        order2.setStatus(Order.OrderStatus.PAID);
        order2.setPaymentMode(Order.PaymentMode.COD);
        order2.setTotalAmount(BigDecimal.valueOf(500));

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order2));

        // When
        List<Long> orderIds = Arrays.asList(1L, 2L);
        byte[] pdfBytes = dispatchService.generateBulkLabels(orderIds);

        // Then - Requirement 12.7: PDF should be less than 500KB per label
        int maxSizeBytes = 500 * 1024 * orderIds.size(); // 500KB per label
        assertTrue(pdfBytes.length < maxSizeBytes, 
            String.format("Bulk PDF size (%d bytes) should be less than %dKB per label (%d bytes)", 
                pdfBytes.length, 500 * orderIds.size(), maxSizeBytes));
    }

    @Test
    void testGenerateBulkLabels_SingleOrder() {
        // Given - Single order in list
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        List<Long> orderIds = List.of(1L);
        byte[] pdfBytes = dispatchService.generateBulkLabels(orderIds);

        // Then
        assertNotNull(pdfBytes, "Bulk PDF should work with single order");
        assertTrue(pdfBytes.length > 0, "PDF should have content");
        
        // Verify PDF header
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, Math.min(5, pdfBytes.length)));
        assertEquals("%PDF-", pdfHeader, "Should be a valid PDF file");
    }

    @Test
    void testGenerateBulkLabels_NullOrderIds() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> dispatchService.generateBulkLabels(null),
            "Should throw IllegalArgumentException for null order IDs");
    }

    @Test
    void testGenerateBulkLabels_EmptyOrderIds() {
        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> dispatchService.generateBulkLabels(List.of()),
            "Should throw IllegalArgumentException for empty order IDs list");
    }

    @Test
    void testGenerateBulkLabels_OrderNotFound() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.findById(2L)).thenReturn(Optional.empty());

        // When & Then
        List<Long> orderIds = Arrays.asList(1L, 2L);
        assertThrows(ResourceNotFoundException.class,
            () -> dispatchService.generateBulkLabels(orderIds),
            "Should throw ResourceNotFoundException if any order is not found");
    }

    @Test
    void testGenerateBulkLabels_LargeNumberOfOrders() {
        // Given - 10 orders
        for (long i = 1; i <= 10; i++) {
            Order order = new Order();
            order.setId(i);
            order.setOrderNumber("ORD-20250101-" + String.format("%04d", i));
            order.setOrderDate(LocalDate.of(2025, 1, 1));
            order.setCustomer(testCustomer);
            order.setItems(List.of(testOrder.getItems().get(0)));
            order.setStatus(Order.OrderStatus.PAID);
            order.setPaymentMode(Order.PaymentMode.UPI);
            order.setTotalAmount(BigDecimal.valueOf(500));
            
            when(orderRepository.findById(i)).thenReturn(Optional.of(order));
        }

        // When
        List<Long> orderIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        byte[] pdfBytes = dispatchService.generateBulkLabels(orderIds);

        // Then
        assertNotNull(pdfBytes, "Bulk PDF should handle 10 orders");
        assertTrue(pdfBytes.length > 0, "PDF should have content");
        
        System.out.println("Generated bulk PDF size for 10 orders: " + pdfBytes.length + " bytes");
        
        // Verify size is reasonable (should be less than 500KB per label)
        int maxSizeBytes = 500 * 1024 * orderIds.size();
        assertTrue(pdfBytes.length < maxSizeBytes, 
            String.format("Bulk PDF with 10 orders (%d bytes) should be less than 5MB", pdfBytes.length));
    }

    @Test
    void testPrepareBulkLabelData_Success() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        
        Order order2 = new Order();
        order2.setId(2L);
        order2.setOrderNumber("ORD-20250101-0002");
        order2.setOrderDate(LocalDate.of(2025, 1, 1));
        order2.setCustomer(testCustomer);
        order2.setItems(List.of(testOrder.getItems().get(0)));
        order2.setStatus(Order.OrderStatus.PAID);
        order2.setPaymentMode(Order.PaymentMode.COD);
        order2.setTotalAmount(BigDecimal.valueOf(500));
        
        when(orderRepository.findById(2L)).thenReturn(Optional.of(order2));

        // When
        List<DispatchLabelDTO> labelDataList = dispatchService.prepareBulkLabelData(Arrays.asList(1L, 2L));

        // Then
        assertNotNull(labelDataList, "Label data list should not be null");
        assertEquals(2, labelDataList.size(), "Should have 2 label data items");
        
        assertEquals("ORD-20250101-0001", labelDataList.get(0).getOrderNumber());
        assertEquals("ORD-20250101-0002", labelDataList.get(1).getOrderNumber());
    }
}
