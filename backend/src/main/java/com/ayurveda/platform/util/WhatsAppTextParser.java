package com.ayurveda.platform.util;

import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw WhatsApp order text into structured order data.
 *
 * Supports common patterns:
 * - "2 x Product Name" or "2x Product Name"
 * - "Product Name x 2" or "Product Name x2"
 * - "Product Name - 2" or "Product Name (2)"
 * - Customer details: name, phone, address
 *
 * Returns parsed items with raw text for fuzzy matching against the product catalog.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WhatsAppTextParser {

    private static final double FUZZY_MATCH_THRESHOLD = 0.6;
    
    private final ProductRepository productRepository;

    // Patterns for quantity-product extraction
    private static final Pattern QTY_PREFIX_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\s*[xX×]\\s*(.+)$");
    private static final Pattern QTY_SUFFIX_PATTERN =
            Pattern.compile("^\\s*(.+?)\\s*[xX×]\\s*(\\d+)\\s*$");
    private static final Pattern QTY_DASH_PATTERN =
            Pattern.compile("^\\s*(.+?)\\s*[-–]\\s*(\\d+)\\s*$");
    private static final Pattern QTY_PAREN_PATTERN =
            Pattern.compile("^\\s*(.+?)\\s*\\(\\s*(\\d+)\\s*\\)\\s*$");

    // Customer info patterns
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:phone|mob|mobile|contact|ph)[:\\s]*([\\d\\s+()-]{10,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?:name|deliver\\s*to|customer)[:\\s]*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_PATTERN =
            Pattern.compile("(?:address|addr|deliver(?:y)?\\s*(?:to|at|address)?)[:\\s]*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PINCODE_PATTERN =
            Pattern.compile("\\b(\\d{6})\\b");

    // Payment info patterns
    private static final Pattern PAYMENT_AMOUNT_PATTERN =
            Pattern.compile("(?:amount|payment|paid|rs\\.?|rupees?|₹)[:\\s]*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COD_PATTERN =
            Pattern.compile("\\b(?:cod|cash\\s*on\\s*delivery|cash)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPI_PATTERN =
            Pattern.compile("\\b(?:upi|gpay|google\\s*pay|phonepe|paytm|bhim)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_TRANSFER_PATTERN =
            Pattern.compile("\\b(?:bank\\s*transfer|neft|rtgs|imps|netbanking|net\\s*banking)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONLINE_PATTERN =
            Pattern.compile("\\b(?:online|digital|card\\s*payment)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT_PATTERN =
            Pattern.compile("\\b(?:credit|on\\s*credit|later|due)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Main WhatsApp parsing method that orchestrates all extraction and calculates confidence scoring.
     * This is the primary entry point for parsing WhatsApp messages.
     *
     * Confidence Scoring Rules:
     * - Start with 1.0 (perfect confidence)
     * - Reduce by 0.3 if customer info is missing or incomplete (no name or phone)
     * - Reduce by 0.4 if no products could be extracted
     * - Reduce proportionally for low product match scores (average match confidence)
     *
     * Implements Requirements 3.4, 3.5, 3.6
     *
     * @param messageText the raw WhatsApp message text
     * @return ParsedWhatsAppOrder with confidence score and warnings
     */
    public ParsedWhatsAppOrder parseWhatsAppMessage(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return ParsedWhatsAppOrder.builder()
                    .items(List.of())
                    .warnings(List.of("Empty message text"))
                    .confidenceScore(0.0)
                    .rawText(messageText)
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        double confidenceScore = 1.0;

        // Step 1: Extract customer information
        ParsedCustomer customer = extractCustomerInfo(messageText);
        
        // Check customer info completeness
        boolean hasCustomerName = customer.getName() != null && !customer.getName().isBlank();
        boolean hasCustomerPhone = customer.getPhone() != null && !customer.getPhone().isBlank();
        
        if (!hasCustomerName || !hasCustomerPhone) {
            confidenceScore -= 0.3;
            if (!hasCustomerName && !hasCustomerPhone) {
                warnings.add("Missing customer name and phone number");
            } else if (!hasCustomerName) {
                warnings.add("Missing customer name");
            } else {
                warnings.add("Missing customer phone number");
            }
        }

        // Step 2: Extract products with fuzzy matching
        List<ParsedItem> items = extractProducts(messageText);
        
        if (items.isEmpty()) {
            confidenceScore -= 0.4;
            warnings.add("No product items could be extracted from the message");
        } else {
            // Calculate average product match confidence
            double totalMatchConfidence = 0.0;
            int matchedItemsCount = 0;
            int unmatchedItemsCount = 0;

            for (ParsedItem item : items) {
                if (item.getMatchedProductId() != null) {
                    totalMatchConfidence += item.getConfidence();
                    matchedItemsCount++;
                    
                    // Warn about low-confidence matches
                    if (item.getConfidence() < 0.8) {
                        warnings.add(String.format("Low confidence match for '%s' -> '%s' (%.2f)", 
                                item.getRawText(), item.getMatchedProductName(), item.getConfidence()));
                    }
                } else {
                    unmatchedItemsCount++;
                    warnings.add(String.format("Could not match product: '%s'", item.getRawText()));
                }
            }

            // Reduce confidence for low product match scores
            if (matchedItemsCount > 0) {
                double averageMatchConfidence = totalMatchConfidence / matchedItemsCount;
                // Only penalize if average match is below 1.0
                // Perfect matches (1.0) get no penalty, fuzzy matches get scaled penalty
                if (averageMatchConfidence < 1.0) {
                    // Scale penalty: fuzzy matches at 0.6-0.9 get proportional penalty
                    // Maximum penalty is 0.3 for matches just at threshold (0.6)
                    double matchPenalty = (1.0 - averageMatchConfidence) * 0.3;
                    confidenceScore -= matchPenalty;
                }
            }

            // Further reduce confidence if we have unmatched items
            if (unmatchedItemsCount > 0) {
                double unmatchedRatio = (double) unmatchedItemsCount / items.size();
                double unmatchedPenalty = unmatchedRatio * 0.3; // Max penalty of 0.3 for all unmatched
                confidenceScore -= unmatchedPenalty;
            }
        }

        // Step 3: Extract payment information
        ParsedPayment payment = extractPaymentInfo(messageText);

        // Confidence > 0 must imply some data was extracted (Requirements 3.4, 3.5).
        // If no customer data (no name AND no phone) and no products were extracted,
        // there is nothing to be confident about, so force confidence to 0.0.
        boolean noCustomerData = !hasCustomerName && !hasCustomerPhone;
        if (noCustomerData && items.isEmpty()) {
            confidenceScore = 0.0;
        }

        // Ensure confidence score is in valid range [0.0, 1.0]
        confidenceScore = Math.max(0.0, Math.min(1.0, confidenceScore));

        return ParsedWhatsAppOrder.builder()
                .customer(customer)
                .items(items)
                .payment(payment)
                .warnings(warnings)
                .confidenceScore(confidenceScore)
                .rawText(messageText)
                .build();
    }

    /**
     * Parse raw WhatsApp text into structured order data.
     * This is a legacy method that uses the main parseWhatsAppMessage method.
     *
     * @param rawText the raw WhatsApp message text
     * @return parsed result with items and customer info
     * @deprecated Use parseWhatsAppMessage() instead for confidence scoring
     */
    @Deprecated
    public ParsedWhatsAppOrder parse(String rawText) {
        return parseWhatsAppMessage(rawText);
    }

    /**
     * Extract customer information from WhatsApp message text.
     * Extracts name using "Name:" or "Customer:" patterns,
     * Indian phone numbers (10 digits with optional +91/0 prefix),
     * address and pincode.
     *
     * Implements Requirement 3.1: Customer information extraction
     *
     * @param messageText the raw WhatsApp message text
     * @return parsed customer information
     */
    public ParsedCustomer extractCustomerInfo(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return new ParsedCustomer();
        }

        ParsedCustomer customer = new ParsedCustomer();
        String[] lines = messageText.split("[\\r\\n]+");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Extract name using "Name:" or "Customer:" patterns
            if (customer.getName() == null) {
                Matcher nameMatcher = NAME_PATTERN.matcher(trimmed);
                if (nameMatcher.find()) {
                    customer.setName(nameMatcher.group(1).trim());
                    continue;
                }
            }

            // Extract phone with "Phone:", "Mobile:", etc. prefix
            if (customer.getPhone() == null) {
                Matcher phoneMatcher = PHONE_PATTERN.matcher(trimmed);
                if (phoneMatcher.find()) {
                    String rawPhone = phoneMatcher.group(1).replaceAll("[\\s()-]", "").trim();
                    // Clean +91 or 0 prefix
                    if (rawPhone.startsWith("+91")) {
                        rawPhone = rawPhone.substring(3);
                    } else if (rawPhone.startsWith("0") && rawPhone.length() == 11) {
                        rawPhone = rawPhone.substring(1);
                    }
                    // Validate Indian phone number format (10 digits starting with 6-9)
                    if (rawPhone.matches("[6-9]\\d{9}")) {
                        customer.setPhone(rawPhone);
                    }
                    continue;
                }
            }

            // Extract address
            if (customer.getAddress() == null) {
                Matcher addressMatcher = ADDRESS_PATTERN.matcher(trimmed);
                if (addressMatcher.find()) {
                    customer.setAddress(addressMatcher.group(1).trim());
                    continue;
                }
            }
        }

        // If no explicit phone found, scan full text for standalone phone numbers
        // This handles cases where phone is in format: +91 9876543210 or 09876543210
        if (customer.getPhone() == null) {
            // Try with +91 or 0 prefix first
            Pattern phoneWithPrefix = Pattern.compile("(?:\\+91|0)?\\s*([6-9]\\d{9})\\b");
            Matcher phoneMatcher = phoneWithPrefix.matcher(messageText);
            if (phoneMatcher.find()) {
                customer.setPhone(phoneMatcher.group(1).trim());
            }
        }

        // Extract pincode from address if present
        if (customer.getAddress() != null && customer.getPincode() == null) {
            Matcher pinMatcher = PINCODE_PATTERN.matcher(customer.getAddress());
            if (pinMatcher.find()) {
                customer.setPincode(pinMatcher.group(1));
            }
        }

        // Also scan full text for pincode if not found in address
        if (customer.getPincode() == null) {
            Matcher pinMatcher = PINCODE_PATTERN.matcher(messageText);
            if (pinMatcher.find()) {
                customer.setPincode(pinMatcher.group(1));
            }
        }

        return customer;
    }

    /**
     * Extract products from WhatsApp message text and match against catalog.
     * This method parses product lines and uses fuzzy matching to identify products.
     *
     * Implements Requirements 3.2, 26.1, 26.2, 26.3, 26.4, 26.5
     *
     * @param messageText the raw WhatsApp message text
     * @return list of parsed items with matched products
     */
    public List<ParsedItem> extractProducts(String messageText) {
        if (messageText == null || messageText.isBlank()) {
            return List.of();
        }

        String[] lines = messageText.split("[\\r\\n]+");
        List<ParsedItem> items = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Try to extract product/quantity
            ParsedItem item = tryExtractItem(trimmed);
            if (item != null) {
                // Perform fuzzy matching against product catalog
                FuzzyMatchResult matchResult = fuzzyMatchProduct(item.getRawText());
                if (matchResult != null) {
                    item.setMatchedProductId(matchResult.getProductId());
                    item.setMatchedProductName(matchResult.getProductName());
                    item.setConfidence(matchResult.getConfidence());
                }
                items.add(item);
            }
        }

        return items;
    }

    /**
     * Fuzzy match a product identifier against the product catalog.
     * Tries exact SKU match first, then fuzzy name matching using Levenshtein distance.
     * Uses 0.6 threshold for fuzzy matches.
     *
     * Implements Requirements 26.1, 26.2, 26.3, 26.4, 26.5
     *
     * @param productIdentifier the product text to match (SKU or product name)
     * @return match result with product details and confidence score, or null if no match found
     */
    public FuzzyMatchResult fuzzyMatchProduct(String productIdentifier) {
        if (productIdentifier == null || productIdentifier.isBlank()) {
            return null;
        }

        String normalized = productIdentifier.trim().toLowerCase();

        // Step 1: Try exact SKU match first (Requirement 26.1)
        Optional<Product> exactSkuMatch = productRepository.findBySku(productIdentifier.trim());
        if (exactSkuMatch.isPresent()) {
            Product product = exactSkuMatch.get();
            log.debug("Exact SKU match found: {} -> {}", productIdentifier, product.getName());
            return FuzzyMatchResult.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .sku(product.getSku())
                    .confidence(1.0)
                    .matchType("EXACT_SKU")
                    .build();
        }

        // Step 2: Try fuzzy name matching using Levenshtein distance (Requirement 26.2)
        List<Product> allProducts = productRepository.findAll();
        Product bestMatch = null;
        double bestSimilarity = 0.0;

        for (Product product : allProducts) {
            // Calculate similarity against product name
            double nameSimilarity = calculateSimilarity(normalized, product.getName().toLowerCase());
            
            // Also check similarity against SKU (case-insensitive)
            double skuSimilarity = calculateSimilarity(normalized, product.getSku().toLowerCase());
            
            // Take the best similarity score
            double similarity = Math.max(nameSimilarity, skuSimilarity);

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = product;
            }
        }

        // Return match if above threshold (Requirement 26.3)
        if (bestMatch != null && bestSimilarity >= FUZZY_MATCH_THRESHOLD) {
            log.debug("Fuzzy match found: {} -> {} (similarity: {:.2f})", 
                    productIdentifier, bestMatch.getName(), bestSimilarity);
            return FuzzyMatchResult.builder()
                    .productId(bestMatch.getId())
                    .productName(bestMatch.getName())
                    .sku(bestMatch.getSku())
                    .confidence(bestSimilarity)
                    .matchType("FUZZY")
                    .build();
        }

        // Requirement 26.4: Return null if no match found
        log.debug("No match found for: {} (best similarity: {:.2f})", 
                productIdentifier, bestSimilarity);
        return null;
    }

    /**
     * Calculate similarity score using Levenshtein distance.
     * Returns a score between 0.0 (completely different) and 1.0 (identical).
     *
     * @param s1 first string
     * @param s2 second string
     * @return similarity score (0.0 to 1.0)
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        
        if (s1.equals(s2)) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        
        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Calculate Levenshtein distance between two strings.
     * This is the minimum number of single-character edits (insertions, deletions, substitutions)
     * required to change one string into the other.
     *
     * @param s1 first string
     * @param s2 second string
     * @return Levenshtein distance
     */
    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        // Create a 2D array to store distances
        int[][] dp = new int[len1 + 1][len2 + 1];

        // Initialize first row and column
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill the dp array
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1]; // No operation needed
                } else {
                    dp[i][j] = 1 + Math.min(
                            Math.min(dp[i - 1][j], dp[i][j - 1]),  // Insert or delete
                            dp[i - 1][j - 1]  // Replace
                    );
                }
            }
        }

        return dp[len1][len2];
    }

    /**
     * Try to extract a product item (name + quantity) from a line.
     */
    private ParsedItem tryExtractItem(String line) {
        // Pattern: "2 x Product Name"
        Matcher m1 = QTY_PREFIX_PATTERN.matcher(line);
        if (m1.matches()) {
            return ParsedItem.builder()
                    .rawText(m1.group(2).trim())
                    .quantity(Integer.parseInt(m1.group(1)))
                    .build();
        }

        // Pattern: "Product Name x 2"
        Matcher m2 = QTY_SUFFIX_PATTERN.matcher(line);
        if (m2.matches()) {
            return ParsedItem.builder()
                    .rawText(m2.group(1).trim())
                    .quantity(Integer.parseInt(m2.group(2)))
                    .build();
        }

        // Pattern: "Product Name - 2"
        Matcher m3 = QTY_DASH_PATTERN.matcher(line);
        if (m3.matches()) {
            try {
                int qty = Integer.parseInt(m3.group(2));
                return ParsedItem.builder()
                        .rawText(m3.group(1).trim())
                        .quantity(qty)
                        .build();
            } catch (NumberFormatException e) {
                // Not a quantity, skip
            }
        }

        // Pattern: "Product Name (2)"
        Matcher m4 = QTY_PAREN_PATTERN.matcher(line);
        if (m4.matches()) {
            return ParsedItem.builder()
                    .rawText(m4.group(1).trim())
                    .quantity(Integer.parseInt(m4.group(2)))
                    .build();
        }

        return null;
    }

    /**
     * Extract payment information from WhatsApp text.
     * Detects payment mode and amount if present.
     * Defaults to COD if no payment mode detected.
     *
     * Implements Requirement 3.3: Payment information detection
     *
     * @param text the raw WhatsApp message text
     * @return ParsedPayment with payment mode and optional amount
     */
    public ParsedPayment extractPaymentInfo(String text) {
        if (text == null || text.isBlank()) {
            return ParsedPayment.builder()
                    .paymentMode("COD")
                    .build();
        }

        String paymentMode = "COD"; // Default to COD
        String amount = null;

        // Check for payment mode keywords in priority order
        if (UPI_PATTERN.matcher(text).find()) {
            paymentMode = "UPI";
        } else if (BANK_TRANSFER_PATTERN.matcher(text).find()) {
            paymentMode = "BANK_TRANSFER";
        } else if (ONLINE_PATTERN.matcher(text).find()) {
            paymentMode = "ONLINE";
        } else if (CREDIT_PATTERN.matcher(text).find()) {
            paymentMode = "CREDIT";
        } else if (COD_PATTERN.matcher(text).find()) {
            paymentMode = "COD";
        }
        // If no payment keyword found, default to COD

        // Extract payment amount if present
        Matcher amountMatcher = PAYMENT_AMOUNT_PATTERN.matcher(text);
        if (amountMatcher.find()) {
            String extractedAmount = amountMatcher.group(1).replaceAll(",", "");
            amount = extractedAmount;
        }

        return ParsedPayment.builder()
                .paymentMode(paymentMode)
                .amount(amount)
                .build();
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedWhatsAppOrder {
        private ParsedCustomer customer;
        private List<ParsedItem> items;
        private ParsedPayment payment;
        private List<String> warnings;
        private Double confidenceScore;  // 0.0 to 1.0, indicates parsing confidence
        private String rawText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedItem {
        private String rawText;           // Raw product text for fuzzy matching
        private int quantity;
        private Long matchedProductId;    // Set after fuzzy matching
        private String matchedProductName;
        private double confidence;        // 0.0 - 1.0, set after matching
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedCustomer {
        private String name;
        private String phone;
        private String address;
        private String pincode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedPayment {
        private String paymentMode;  // COD, UPI, BANK_TRANSFER, ONLINE, CREDIT
        private String amount;       // Extracted amount as string (to be parsed later)
    }

    /**
     * DTO representing a fuzzy match result.
     * Requirement 26.5: Return match confidence score
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FuzzyMatchResult {
        private Long productId;
        private String productName;
        private String sku;
        private double confidence;      // 0.0 to 1.0
        private String matchType;       // "EXACT_SKU" or "FUZZY"
    }
}
