package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.response.DispatchLabelDTO;
import com.ayurveda.platform.dto.response.LabelProductLineDTO;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.service.DispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DispatchController dispatch label generation endpoints.
 * Tests REST API controllers for single and bulk label generation.
 * 
 * Implements Requirements:
 * - 12.1: Generate single dispatch labels for orders
 * - 12.6: Generate bulk dispatch labels for multiple orders
 * - 12.1: Validate order status is PAID or later
 */
@ExtendWith(MockitoExtension.class)
class DispatchControllerTest {

    @Mock
    private DispatchService dispatchService;

    @InjectMocks
    private DispatchController dispatchController;

    private DispatchLabelDTO mockLabelData;
    private byte[] mockPdfBytes;

    @BeforeEach
    void setUp() {
        // Set up mock label data
        mockLabelData = DispatchLabelDTO.builder()
                .orderNumber("ORD-20240115-0001")
                .orderDate(LocalDate.of(2024, 1, 15))
                .customerName("John Doe")
                .shippingAddress("123 Main Street, Apartment 4B")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .phone("9876543210")
                .products(new ArrayList<>())
                .totalItems(3)
                .totalWeight(BigDecimal.valueOf(750))
                .orderAmount(BigDecimal.valueOf(1500.00))
                .paymentMode(Order.PaymentMode.UPI)
                .barcode("ORD-20240115-0001")
                .vendorName("Test Ayurveda Company")
                .vendorAddress("456 Vendor Street, Mumbai 400002")
                .vendorPhone("9123456789")
                .build();

        // Mock PDF bytes (simulating a small PDF)
        mockPdfBytes = "Mock PDF Content".getBytes();
    }

    @Test
    @DisplayName("Test GET /labels/{orderId} - Generate single dispatch label PDF")
    void testGenerateSingleLabel_Success() {
        // Arrange
        Long orderId = 1L;
        when(dispatchService.generateSingleLabel(orderId)).thenReturn(mockPdfBytes);

        // Act
        ResponseEntity<byte[]> response = dispatchController.generateSingleLabel(orderId);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(mockPdfBytes, response.getBody());
        
        // Verify headers
        HttpHeaders headers = response.getHeaders();
        assertEquals(MediaType.APPLICATION_PDF, headers.getContentType());
        assertTrue(headers.getContentDisposition().toString().contains("dispatch-label-1.pdf"));
        assertEquals(mockPdfBytes.length, headers.getContentLength());

        // Verify service was called
        verify(dispatchService, times(1)).generateSingleLabel(orderId);
    }

    @Test
    @DisplayName("Test GET /labels/{orderId} - Order not found")
    void testGenerateSingleLabel_OrderNotFound() {
        // Arrange
        Long orderId = 999L;
        when(dispatchService.generateSingleLabel(orderId))
                .thenThrow(new RuntimeException("Order not found"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            dispatchController.generateSingleLabel(orderId);
        });

        verify(dispatchService, times(1)).generateSingleLabel(orderId);
    }

    @Test
    @DisplayName("Test GET /labels/{orderId} - Invalid order status")
    void testGenerateSingleLabel_InvalidStatus() {
        // Arrange
        Long orderId = 1L;
        when(dispatchService.generateSingleLabel(orderId))
                .thenThrow(new IllegalStateException("Order status must be PAID or later"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            dispatchController.generateSingleLabel(orderId);
        });

        verify(dispatchService, times(1)).generateSingleLabel(orderId);
    }

    @Test
    @DisplayName("Test POST /labels/bulk - Generate bulk dispatch labels PDF")
    void testGenerateBulkLabels_Success() {
        // Arrange
        List<Integer> orderIds = List.of(1, 2, 3);
        Map<String, Object> request = Map.of("orderIds", orderIds);
        
        byte[] bulkPdfBytes = "Mock Bulk PDF Content".getBytes();
        when(dispatchService.generateBulkLabels(anyList())).thenReturn(bulkPdfBytes);

        // Act
        ResponseEntity<byte[]> response = dispatchController.generateBulkLabels(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(bulkPdfBytes, response.getBody());
        
        // Verify headers
        HttpHeaders headers = response.getHeaders();
        assertEquals(MediaType.APPLICATION_PDF, headers.getContentType());
        assertTrue(headers.getContentDisposition().toString().contains("dispatch-labels-bulk.pdf"));
        assertEquals(bulkPdfBytes.length, headers.getContentLength());

        // Verify service was called with converted Long list
        verify(dispatchService, times(1)).generateBulkLabels(argThat(list -> 
            list.size() == 3 && 
            list.get(0).equals(1L) && 
            list.get(1).equals(2L) && 
            list.get(2).equals(3L)
        ));
    }

    @Test
    @DisplayName("Test POST /labels/bulk - Empty order IDs list")
    void testGenerateBulkLabels_EmptyList() {
        // Arrange
        List<Integer> orderIds = List.of();
        Map<String, Object> request = Map.of("orderIds", orderIds);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            dispatchController.generateBulkLabels(request);
        });

        // Verify service was NOT called
        verify(dispatchService, never()).generateBulkLabels(anyList());
    }

    @Test
    @DisplayName("Test POST /labels/bulk - Null order IDs")
    void testGenerateBulkLabels_NullList() {
        // Arrange
        Map<String, Object> request = Map.of();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            dispatchController.generateBulkLabels(request);
        });

        // Verify service was NOT called
        verify(dispatchService, never()).generateBulkLabels(anyList());
    }

    @Test
    @DisplayName("Test POST /labels/bulk - Single order ID")
    void testGenerateBulkLabels_SingleOrder() {
        // Arrange
        List<Integer> orderIds = List.of(1);
        Map<String, Object> request = Map.of("orderIds", orderIds);
        
        byte[] bulkPdfBytes = "Mock Single PDF Content".getBytes();
        when(dispatchService.generateBulkLabels(anyList())).thenReturn(bulkPdfBytes);

        // Act
        ResponseEntity<byte[]> response = dispatchController.generateBulkLabels(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(bulkPdfBytes, response.getBody());

        verify(dispatchService, times(1)).generateBulkLabels(argThat(list -> 
            list.size() == 1 && list.get(0).equals(1L)
        ));
    }

    @Test
    @DisplayName("Test POST /labels/bulk - Large batch of orders")
    void testGenerateBulkLabels_LargeBatch() {
        // Arrange
        List<Integer> orderIds = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            orderIds.add(i);
        }
        Map<String, Object> request = Map.of("orderIds", orderIds);
        
        byte[] bulkPdfBytes = new byte[1024 * 100]; // 100KB mock PDF
        when(dispatchService.generateBulkLabels(anyList())).thenReturn(bulkPdfBytes);

        // Act
        ResponseEntity<byte[]> response = dispatchController.generateBulkLabels(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(bulkPdfBytes, response.getBody());

        verify(dispatchService, times(1)).generateBulkLabels(argThat(list -> 
            list.size() == 50 && list.get(0).equals(1L) && list.get(49).equals(50L)
        ));
    }

    @Test
    @DisplayName("Test GET /labels/{orderId}/data - Get label data without PDF generation")
    void testGetLabelData_Success() {
        // Arrange
        Long orderId = 1L;
        when(dispatchService.prepareLabelData(orderId)).thenReturn(mockLabelData);

        // Act
        ResponseEntity<DispatchLabelDTO> response = dispatchController.getLabelData(orderId);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(mockLabelData.getOrderNumber(), response.getBody().getOrderNumber());
        assertEquals(mockLabelData.getCustomerName(), response.getBody().getCustomerName());
        assertEquals(mockLabelData.getTotalItems(), response.getBody().getTotalItems());

        verify(dispatchService, times(1)).prepareLabelData(orderId);
    }

    @Test
    @DisplayName("Test GET /labels/{orderId}/data - Order not found")
    void testGetLabelData_OrderNotFound() {
        // Arrange
        Long orderId = 999L;
        when(dispatchService.prepareLabelData(orderId))
                .thenThrow(new RuntimeException("Order not found"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            dispatchController.getLabelData(orderId);
        });

        verify(dispatchService, times(1)).prepareLabelData(orderId);
    }

    @Test
    @DisplayName("Test GET /labels/{orderId}/data - Validates order status")
    void testGetLabelData_InvalidStatus() {
        // Arrange
        Long orderId = 1L;
        when(dispatchService.prepareLabelData(orderId))
                .thenThrow(new IllegalStateException("Order status must be PAID or later"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            dispatchController.getLabelData(orderId);
        });

        verify(dispatchService, times(1)).prepareLabelData(orderId);
    }

    @Test
    @DisplayName("Test label data contains all required fields")
    void testLabelData_ContainsAllFields() {
        // Arrange
        Long orderId = 1L;
        when(dispatchService.prepareLabelData(orderId)).thenReturn(mockLabelData);

        // Act
        ResponseEntity<DispatchLabelDTO> response = dispatchController.getLabelData(orderId);

        // Assert
        assertNotNull(response.getBody());
        DispatchLabelDTO labelData = response.getBody();
        
        // Verify order details (Requirement 12.2)
        assertNotNull(labelData.getOrderNumber());
        assertNotNull(labelData.getOrderDate());
        
        // Verify customer shipping information (Requirement 12.2)
        assertNotNull(labelData.getCustomerName());
        assertNotNull(labelData.getShippingAddress());
        assertNotNull(labelData.getCity());
        assertNotNull(labelData.getState());
        assertNotNull(labelData.getPincode());
        assertNotNull(labelData.getPhone());
        
        // Verify product information (Requirement 12.3)
        assertNotNull(labelData.getProducts());
        assertNotNull(labelData.getTotalItems());
        assertNotNull(labelData.getTotalWeight());
        
        // Verify barcode (Requirement 12.4)
        assertNotNull(labelData.getBarcode());
        
        // Verify vendor information (Requirement 12.5)
        assertNotNull(labelData.getVendorName());
        assertNotNull(labelData.getVendorAddress());
        assertNotNull(labelData.getVendorPhone());
    }
}
