package com.ayurveda.platform.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;

/**
 * Utility class for generating barcodes using ZXing library.
 * Supports Code128 format for order number encoding.
 */
@Slf4j
public class BarcodeGenerator {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 100;

    /**
     * Generates a Code128 barcode image for the given order number.
     *
     * @param orderNumber the order number to encode
     * @return BufferedImage containing the barcode
     * @throws IllegalArgumentException if orderNumber is null or empty
     */
    public static BufferedImage generateCode128Barcode(String orderNumber) {
        return generateCode128Barcode(orderNumber, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Generates a Code128 barcode image with custom dimensions.
     *
     * @param orderNumber the order number to encode
     * @param width the width of the barcode image
     * @param height the height of the barcode image
     * @return BufferedImage containing the barcode
     * @throws IllegalArgumentException if orderNumber is null or empty, or if dimensions are invalid
     */
    public static BufferedImage generateCode128Barcode(String orderNumber, int width, int height) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Order number cannot be null or empty");
        }

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Barcode dimensions must be positive");
        }

        try {
            Code128Writer barcodeWriter = new Code128Writer();
            BitMatrix bitMatrix = barcodeWriter.encode(
                orderNumber,
                BarcodeFormat.CODE_128,
                width,
                height
            );

            BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            log.debug("Generated Code128 barcode for order number: {}", orderNumber);
            return barcodeImage;

        } catch (Exception e) {
            log.error("Failed to generate barcode for order number: {}", orderNumber, e);
            throw new RuntimeException("Failed to generate barcode", e);
        }
    }

    /**
     * Validates if the given text can be encoded as Code128 barcode.
     *
     * @param text the text to validate
     * @return true if the text can be encoded, false otherwise
     */
    public static boolean isValidCode128Text(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        try {
            Code128Writer writer = new Code128Writer();
            writer.encode(text, BarcodeFormat.CODE_128, 1, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
