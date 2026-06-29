package com.ayurveda.platform.dto.response;

import lombok.*;

import java.util.List;

/**
 * Result of duplicate order check containing potential duplicate orders with similarity scores.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCheckResult {

    /**
     * Indicates whether any potential duplicates were found.
     */
    private boolean hasDuplicates;

    /**
     * List of potential duplicate orders with their similarity scores.
     */
    private List<DuplicateOrderInfo> potentialDuplicates;

    /**
     * The phone number used for customer matching.
     */
    private String customerPhone;

    /**
     * Information about a potential duplicate order.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateOrderInfo {

        /**
         * Order ID of the potential duplicate.
         */
        private Long orderId;

        /**
         * Order number of the potential duplicate.
         */
        private String orderNumber;

        /**
         * Date when the order was placed.
         */
        private java.time.LocalDate orderDate;

        /**
         * Jaccard similarity score between product sets (0.0 to 1.0).
         */
        private double similarityScore;

        /**
         * Number of days between the original order date and this order.
         */
        private long daysDifference;

        /**
         * List of common product IDs between the orders.
         */
        private List<Long> commonProductIds;

        /**
         * Current status of the order.
         */
        private String orderStatus;
    }
}
