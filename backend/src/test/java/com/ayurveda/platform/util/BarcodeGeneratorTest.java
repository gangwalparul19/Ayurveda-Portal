package com.ayurveda.platform.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BarcodeGenerator utility class.
 * Tests Code128 barcode generation for order numbers.
 */
class BarcodeGeneratorTest {

    @Test
    @DisplayName("Should generate barcode with default dimensions for valid order number")
    void shouldGenerateBarcodeWithDefaultDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";

        // When
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode image should not be null");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should generate barcode with custom dimensions")
    void shouldGenerateBarcodeWithCustomDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";
        int customWidth = 400;
        int customHeight = 150;

        // When
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber, customWidth, customHeight);

        // Then
        assertNotNull(barcode, "Barcode image should not be null");
        assertEquals(customWidth, barcode.getWidth(), "Barcode width should match requested width");
        assertEquals(customHeight, barcode.getHeight(), "Barcode height should match requested height");
    }

    @Test
    @DisplayName("Should throw exception for null order number")
    void shouldThrowExceptionForNullOrderNumber() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            BarcodeGenerator.generateCode128Barcode(null),
            "Should throw IllegalArgumentException for null order number"
        );
    }

    @Test
    @DisplayName("Should throw exception for empty order number")
    void shouldThrowExceptionForEmptyOrderNumber() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            BarcodeGenerator.generateCode128Barcode(""),
            "Should throw IllegalArgumentException for empty order number"
        );
    }

    @Test
    @DisplayName("Should throw exception for whitespace-only order number")
    void shouldThrowExceptionForWhitespaceOnlyOrderNumber() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            BarcodeGenerator.generateCode128Barcode("   "),
            "Should throw IllegalArgumentException for whitespace-only order number"
        );
    }

    @Test
    @DisplayName("Should throw exception for zero width")
    void shouldThrowExceptionForZeroWidth() {
        // Given
        String orderNumber = "ORD-20250107-0001";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            BarcodeGenerator.generateCode128Barcode(orderNumber, 0, 100),
            "Should throw IllegalArgumentException for zero width"
        );
    }

    @Test
    @DisplayName("Should throw exception for negative width")
    void shouldThrowExceptionForNegativeWidth() {
        // Given
        String orderNumber = "ORD-20250107-0001";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            BarcodeGenerator.generateCode128Barcode(orderNumber, -100, 100),
            "Should throw IllegalArgumentException for negative width"
        );
    }

    @Test
    @DisplayName("Should throw exception for zero height")
    void shouldThrowExceptionForZeroHeight() {
        // Given
        String orderNumber = "ORD-20250107-0001";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            BarcodeGenerator.generateCode128Barcode(orderNumber, 300, 0),
            "Should throw IllegalArgumentException for zero height"
        );
    }

    @Test
    @DisplayName("Should throw exception for negative height")
    void shouldThrowExceptionForNegativeHeight() {
        // Given
        String orderNumber = "ORD-20250107-0001";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            BarcodeGenerator.generateCode128Barcode(orderNumber, 300, -100),
            "Should throw IllegalArgumentException for negative height"
        );
    }

    @Test
    @DisplayName("Should generate barcode for order number with special characters")
    void shouldGenerateBarcodeWithSpecialCharacters() {
        // Given
        String orderNumber = "ORD-2025-01-07-0001";

        // When
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber);

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
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should be generated for alphanumeric order number");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should generate barcode for numeric-only order number")
    void shouldGenerateBarcodeForNumericOrderNumber() {
        // Given
        String orderNumber = "123456789";

        // When
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should be generated for numeric order number");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should generate barcode for short order number")
    void shouldGenerateBarcodeForShortOrderNumber() {
        // Given
        String orderNumber = "ORD1";

        // When
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber);

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
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber);

        // Then
        assertNotNull(barcode, "Barcode should be generated for long order number");
        assertTrue(barcode.getWidth() > 0, "Barcode width should be positive");
        assertTrue(barcode.getHeight() > 0, "Barcode height should be positive");
    }

    @Test
    @DisplayName("Should validate valid Code128 text")
    void shouldValidateValidCode128Text() {
        // Given
        String validText = "ORD-20250107-0001";

        // When
        boolean isValid = BarcodeGenerator.isValidCode128Text(validText);

        // Then
        assertTrue(isValid, "Should return true for valid Code128 text");
    }

    @Test
    @DisplayName("Should not validate null text")
    void shouldNotValidateNullText() {
        // When
        boolean isValid = BarcodeGenerator.isValidCode128Text(null);

        // Then
        assertFalse(isValid, "Should return false for null text");
    }

    @Test
    @DisplayName("Should not validate empty text")
    void shouldNotValidateEmptyText() {
        // When
        boolean isValid = BarcodeGenerator.isValidCode128Text("");

        // Then
        assertFalse(isValid, "Should return false for empty text");
    }

    @Test
    @DisplayName("Should generate different barcodes for different order numbers")
    void shouldGenerateDifferentBarcodesForDifferentOrderNumbers() {
        // Given
        String orderNumber1 = "ORD-20250107-0001";
        String orderNumber2 = "ORD-20250107-0002";

        // When
        BufferedImage barcode1 = BarcodeGenerator.generateCode128Barcode(orderNumber1);
        BufferedImage barcode2 = BarcodeGenerator.generateCode128Barcode(orderNumber2);

        // Then
        assertNotNull(barcode1, "First barcode should not be null");
        assertNotNull(barcode2, "Second barcode should not be null");
        assertNotEquals(barcode1, barcode2, "Barcodes for different order numbers should be different objects");
    }

    @Test
    @DisplayName("Should generate barcode with minimum dimensions")
    void shouldGenerateBarcodeWithMinimumDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";
        int minWidth = 1;
        int minHeight = 1;

        // When
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber, minWidth, minHeight);

        // Then
        assertNotNull(barcode, "Barcode should be generated with minimum dimensions");
        assertTrue(barcode.getWidth() >= minWidth, "Barcode width should be at least minimum width");
        assertTrue(barcode.getHeight() >= minHeight, "Barcode height should be at least minimum height");
    }

    @Test
    @DisplayName("Should generate barcode with large dimensions")
    void shouldGenerateBarcodeWithLargeDimensions() {
        // Given
        String orderNumber = "ORD-20250107-0001";
        int largeWidth = 1000;
        int largeHeight = 500;

        // When
        BufferedImage barcode = BarcodeGenerator.generateCode128Barcode(orderNumber, largeWidth, largeHeight);

        // Then
        assertNotNull(barcode, "Barcode should be generated with large dimensions");
        assertEquals(largeWidth, barcode.getWidth(), "Barcode width should match requested width");
        assertEquals(largeHeight, barcode.getHeight(), "Barcode height should match requested height");
    }
}
