package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.tenant.repository.DispatchLabelRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for barcode generation methods in DispatchService.
 * Tests the integration with BarcodeGenerator utility.
 */
@ExtendWith(MockitoExtension.class)
class DispatchServiceBarcodeTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DispatchLabelRepository dispatchLabelRepository;

    @InjectMocks
    private DispatchService dispatchService;

    @Test
    @DisplayName("Should generate barcode with default dimensions")
    void shouldGenerateBarcodeWithDefaultDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should not be null");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should generate barcode with custom dimensions")
    void shouldGenerateBarcodeWithCustomDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";
        int width = 400;
        int height = 150;

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber, width, height);

        // Then
        assertNotNull(barcode, "Barcode should not be null");
        assertEquals(width, barcode.getWidth(), "Barcode width should match requested width");
        assertEquals(height, barcode.getHeight(), "Barcode height should match requested height");
    }

    @Test
    @DisplayName("Should throw exception for null order number")
    void shouldThrowExceptionForNullOrderNumber() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            dispatchService.generateOrderBarcode(null),
            "Should throw IllegalArgumentException for null order number"
        );
    }

    @Test
    @DisplayName("Should throw exception for empty order number")
    void shouldThrowExceptionForEmptyOrderNumber() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            dispatchService.generateOrderBarcode(""),
            "Should throw IllegalArgumentException for empty order number"
        );
    }

    @Test
    @DisplayName("Should generate barcode for order number with hyphens")
    void shouldGenerateBarcodeForOrderNumberWithHyphens() {
        // Given
        String orderNumber = "ORD-2025-01-07-0001";

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should be generated for order number with hyphens");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should generate barcode for alphanumeric order number")
    void shouldGenerateBarcodeForAlphanumericOrderNumber() {
        // Given
        String orderNumber = "ABC123XYZ789";

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should be generated for alphanumeric order number");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should throw exception for invalid custom dimensions")
    void shouldThrowExceptionForInvalidCustomDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            dispatchService.generateOrderBarcode(orderNumber, 0, 100),
            "Should throw exception for zero width"
        );

        assertThrows(IllegalArgumentException.class, () ->
            dispatchService.generateOrderBarcode(orderNumber, 100, 0),
            "Should throw exception for zero height"
        );

        assertThrows(IllegalArgumentException.class, () ->
            dispatchService.generateOrderBarcode(orderNumber, -100, 100),
            "Should throw exception for negative width"
        );

        assertThrows(IllegalArgumentException.class, () ->
            dispatchService.generateOrderBarcode(orderNumber, 100, -100),
            "Should throw exception for negative height"
        );
    }

    @Test
    @DisplayName("Should generate barcode for short order number")
    void shouldGenerateBarcodeForShortOrderNumber() {
        // Given
        String orderNumber = "ORD1";

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should be generated for short order number");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should generate barcode for long order number")
    void shouldGenerateBarcodeForLongOrderNumber() {
        // Given
        String orderNumber = "ORD-20250107-0001-WAREHOUSE-A-BATCH-123";

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should be generated for long order number");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should generate multiple barcodes independently")
    void shouldGenerateMultipleBarcodesIndependently() {
        // Given
        String orderNumber1 = "ORD-20250107-0001";
        String orderNumber2 = "ORD-20250107-0002";

        // When
        BufferedImage barcode1 = dispatchService.generateOrderBarcode(orderNumber1);
        BufferedImage barcode2 = dispatchService.generateOrderBarcode(orderNumber2);

        // Then
        assertNotNull(barcode1, "First barcode should not be null");
        assertNotNull(barcode2, "Second barcode should not be null");
        assertNotEquals(barcode1, barcode2, "Barcodes should be different objects");
    }

    @Test
    @DisplayName("Should generate barcode with minimum valid dimensions")
    void shouldGenerateBarcodeWithMinimumValidDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";
        int minWidth = 1;
        int minHeight = 1;

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber, minWidth, minHeight);

        // Then
        assertNotNull(barcode, "Barcode should be generated with minimum dimensions");
        assertTrue(barcode.getWidth() >= minWidth, "Barcode width should be at least minimum");
        assertTrue(barcode.getHeight() >= minHeight, "Barcode height should be at least minimum");
    }

    @Test
    @DisplayName("Should generate barcode with large dimensions")
    void shouldGenerateBarcodeWithLargeDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";
        int largeWidth = 1000;
        int largeHeight = 500;

        // When
        BufferedImage barcode = dispatchService.generateOrderBarcode(orderNumber, largeWidth, largeHeight);

        // Then
        assertNotNull(barcode, "Barcode should be generated with large dimensions");
        assertEquals(largeWidth, barcode.getWidth(), "Barcode width should match requested");
        assertEquals(largeHeight, barcode.getHeight(), "Barcode height should match requested");
    }
}
