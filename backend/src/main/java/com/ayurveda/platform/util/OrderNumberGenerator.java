package com.ayurveda.platform.util;

import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates human-readable, tenant-scoped order numbers.
 * Format: {PREFIX}-YYYYMMDD-XXXX (e.g., ORD-20260620-0001)
 *
 * The sequence resets daily. Thread-safe via AtomicInteger.
 * Uses ConfigurationService to get the order number prefix.
 * 
 * Requirements: 2.1, 2.2, 2.3
 */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private final OrderRepository orderRepository;
    private final ConfigurationService configurationService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile String lastDate = "";

    /**
     * Generate the next order number for today.
     * Format: {PREFIX}-YYYYMMDD-XXXX where:
     * - PREFIX is from configuration (default "ORD")
     * - YYYYMMDD is the current date
     * - XXXX is a 4-digit sequence number starting from 0001
     * 
     * The sequence number resets daily and is determined by querying
     * the database for the count of orders created today.
     * 
     * Thread-safe via synchronized method.
     * 
     * @return Generated order number (e.g., "ORD-20260620-0001")
     */
    public synchronized String generateOrderNumber() {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DATE_FORMAT);

        // Reset counter if the date has changed
        if (!dateStr.equals(lastDate)) {
            lastDate = dateStr;
            // Check DB for the last order number today to avoid collisions on restart
            // This ensures uniqueness (Requirement 2.2)
            int lastSequence = orderRepository.findLastOrderNumberByDate(today)
                    .map(this::extractSequence)
                    .orElse(0);
            counter.set(lastSequence);
        }

        // Increment sequence for the next order (Requirement 2.3)
        int sequence = counter.incrementAndGet();
        
        // Get prefix from configuration (default "ORD")
        String prefix = configurationService.getOrderNumberPrefix();
        
        // Format: PREFIX-YYYYMMDD-XXXX (Requirement 2.1)
        return String.format("%s-%s-%04d", prefix, dateStr, sequence);
    }

    /**
     * Extract the numeric sequence from an existing order number.
     * Handles both 3-digit (legacy) and 4-digit formats.
     * 
     * @param orderNumber The order number to parse (e.g., "ORD-20260620-0001")
     * @return The sequence number, or 0 if parsing fails
     */
    private int extractSequence(String orderNumber) {
        try {
            String[] parts = orderNumber.split("-");
            if (parts.length >= 3) {
                return Integer.parseInt(parts[parts.length - 1]);
            }
        } catch (NumberFormatException e) {
            // Fall back to 0 if unable to parse
        }
        return 0;
    }
}
