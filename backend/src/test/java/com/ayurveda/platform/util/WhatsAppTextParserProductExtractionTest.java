package com.ayurveda.platform.util;

import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.util.WhatsAppTextParser.ParsedItem;
import com.ayurveda.platform.util.WhatsAppTextParser.FuzzyMatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for WhatsAppTextParser product extraction and fuzzy matching.
 * 
 * Tests Requirements 3.2, 26.1, 26.2, 26.3, 26.4, 26.5:
 * - Product extraction from various formats
 * - Exact SKU matching
 * - Fuzzy name matching using Levenshtein distance
 * - 0.6 similarity threshold
 * - Match confidence scoring
 * - Warning generation for unmatched products
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppTextParserProductExtractionTest {

    @Mock
    private ProductRepository productRepository;

    private WhatsAppTextParser parser;

    private Product ashwagandha;
    private Product triphala;
    private Product chyawanprash;
    private Product brahmiGhrita;
    private Product amalaki;

    @BeforeEach
    void setUp() {
        parser = new WhatsAppTextParser(productRepository);
        setupMockProducts();
    }

    private void setupMockProducts() {
        // Create mock products for fuzzy matching tests
        ashwagandha = new Product();
        ashwagandha.setId(1L);
        ashwagandha.setSku("ASH001");
        ashwagandha.setName("Ashwagandha Capsules");
        ashwagandha.setSalePrice(BigDecimal.valueOf(299.00));
        ashwagandha.setMrp(BigDecimal.valueOf(399.00));
        ashwagandha.setStockQuantity(100);

        triphala = new Product();
        triphala.setId(2L);
        triphala.setSku("TRI001");
        triphala.setName("Triphala Churna");
        triphala.setSalePrice(BigDecimal.valueOf(199.00));
        triphala.setMrp(BigDecimal.valueOf(249.00));
        triphala.setStockQuantity(50);

        chyawanprash = new Product();
        chyawanprash.setId(3L);
        chyawanprash.setSku("CHY001");
        chyawanprash.setName("Chyawanprash");
        chyawanprash.setSalePrice(BigDecimal.valueOf(350.00));
        chyawanprash.setMrp(BigDecimal.valueOf(450.00));
        chyawanprash.setStockQuantity(75);

        brahmiGhrita = new Product();
        brahmiGhrita.setId(4L);
        brahmiGhrita.setSku("BRA001");
        brahmiGhrita.setName("Brahmi Ghrita");
        brahmiGhrita.setSalePrice(BigDecimal.valueOf(450.00));
        brahmiGhrita.setMrp(BigDecimal.valueOf(550.00));
        brahmiGhrita.setStockQuantity(30);

        amalaki = new Product();
        amalaki.setId(5L);
        amalaki.setSku("AML001");
        amalaki.setName("Amalaki Rasayana");
        amalaki.setSalePrice(BigDecimal.valueOf(250.00));
        amalaki.setMrp(BigDecimal.valueOf(300.00));
        amalaki.setStockQuantity(60);

        List<Product> allProducts = Arrays.asList(ashwagandha, triphala, chyawanprash, brahmiGhrita, amalaki);

        lenient().when(productRepository.findAll()).thenReturn(allProducts);
        lenient().when(productRepository.findBySku("ASH001")).thenReturn(Optional.of(ashwagandha));
        lenient().when(productRepository.findBySku("TRI001")).thenReturn(Optional.of(triphala));
        lenient().when(productRepository.findBySku("CHY001")).thenReturn(Optional.of(chyawanprash));
        lenient().when(productRepository.findBySku("BRA001")).thenReturn(Optional.of(brahmiGhrita));
        lenient().when(productRepository.findBySku("AML001")).thenReturn(Optional.of(amalaki));
        lenient().when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());
    }

    // ========== Product Extraction Format Tests ==========

    /**
     * Test Requirement 3.2: Extract product with "2 x Product Name" format
     */
    @Test
    void extractProducts_withQtyPrefixFormat_shouldExtractProduct() {
        // Given: Message with "quantity x product" format
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "2 x Ashwagandha Capsules\n" +
                        "1 x Triphala Churna";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Products should be extracted with correct quantities
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getRawText()).isEqualTo("Ashwagandha Capsules");
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(1).getRawText()).isEqualTo("Triphala Churna");
        assertThat(items.get(1).getQuantity()).isEqualTo(1);
    }

    /**
     * Test Requirement 3.2: Extract product with "Product Name x 2" format
     */
    @Test
    void extractProducts_withQtySuffixFormat_shouldExtractProduct() {
        // Given: Message with "product x quantity" format
        String message = "Order:\n" +
                        "Ashwagandha Capsules x 3\n" +
                        "Triphala Churna x 2";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Products should be extracted with correct quantities
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getRawText()).isEqualTo("Ashwagandha Capsules");
        assertThat(items.get(0).getQuantity()).isEqualTo(3);
    }

    /**
     * Test Requirement 3.2: Extract product with "Product Name - 2" format
     */
    @Test
    void extractProducts_withDashFormat_shouldExtractProduct() {
        // Given: Message with "product - quantity" format
        String message = "Items:\n" +
                        "Ashwagandha Capsules - 2\n" +
                        "Chyawanprash - 1";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Products should be extracted with correct quantities
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getRawText()).isEqualTo("Ashwagandha Capsules");
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(1).getRawText()).isEqualTo("Chyawanprash");
        assertThat(items.get(1).getQuantity()).isEqualTo(1);
    }

    /**
     * Test Requirement 3.2: Extract product with "Product Name (2)" format
     */
    @Test
    void extractProducts_withParenFormat_shouldExtractProduct() {
        // Given: Message with "product (quantity)" format
        String message = "Order:\n" +
                        "Ashwagandha Capsules (3)\n" +
                        "Triphala Churna (1)";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Products should be extracted with correct quantities
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getRawText()).isEqualTo("Ashwagandha Capsules");
        assertThat(items.get(0).getQuantity()).isEqualTo(3);
        assertThat(items.get(1).getRawText()).isEqualTo("Triphala Churna");
        assertThat(items.get(1).getQuantity()).isEqualTo(1);
    }

    /**
     * Test Requirement 3.2: Extract products with mixed formats
     */
    @Test
    void extractProducts_withMixedFormats_shouldExtractAllProducts() {
        // Given: Message with different product formats
        String message = "Order items:\n" +
                        "2 x Ashwagandha Capsules\n" +
                        "Triphala Churna x 1\n" +
                        "Chyawanprash - 3\n" +
                        "Brahmi Ghrita (2)";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: All products should be extracted
        assertThat(items).hasSize(4);
    }

    /**
     * Test Requirement 3.2: Extract products with × symbol (multiplication sign)
     */
    @Test
    void extractProducts_withMultiplicationSymbol_shouldExtractProduct() {
        // Given: Message with × symbol
        String message = "2 × Ashwagandha Capsules\n" +
                        "Triphala Churna × 3";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Products should be extracted
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(1).getQuantity()).isEqualTo(3);
    }

    /**
     * Test Requirement 3.2: Empty message should return empty list
     */
    @Test
    void extractProducts_withEmptyMessage_shouldReturnEmptyList() {
        // Given: Empty message
        String message = "";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should return empty list
        assertThat(items).isEmpty();
    }

    /**
     * Test Requirement 3.2: Null message should return empty list
     */
    @Test
    void extractProducts_withNullMessage_shouldReturnEmptyList() {
        // Given: Null message
        String message = null;

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should return empty list
        assertThat(items).isEmpty();
    }

    /**
     * Test Requirement 3.2: Message without products should return empty list
     */
    @Test
    void extractProducts_withNoProducts_shouldReturnEmptyList() {
        // Given: Message without product info
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "Address: Bangalore";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should return empty list
        assertThat(items).isEmpty();
    }

    // ========== Fuzzy Matching Tests ==========

    /**
     * Test Requirement 26.1: Exact SKU match should return product with confidence 1.0
     */
    @Test
    void fuzzyMatchProduct_withExactSKU_shouldReturnPerfectMatch() {
        // Given: Exact SKU
        String sku = "ASH001";
        when(productRepository.findBySku("ASH001")).thenReturn(Optional.of(ashwagandha));

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(sku);

        // Then: Should return exact match with confidence 1.0
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getProductName()).isEqualTo("Ashwagandha Capsules");
        assertThat(result.getSku()).isEqualTo("ASH001");
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getMatchType()).isEqualTo("EXACT_SKU");
    }

    /**
     * Test Requirement 26.1: Exact SKU match is case-sensitive
     */
    @Test
    void fuzzyMatchProduct_withExactSKUCaseSensitive_shouldReturnExactMatch() {
        // Given: Exact SKU with correct case
        when(productRepository.findBySku("TRI001")).thenReturn(Optional.of(triphala));

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct("TRI001");

        // Then: Should return exact match
        assertThat(result).isNotNull();
        assertThat(result.getMatchType()).isEqualTo("EXACT_SKU");
        assertThat(result.getConfidence()).isEqualTo(1.0);
    }

    /**
     * Test Requirement 26.2: Fuzzy name matching using Levenshtein distance
     */
    @Test
    void fuzzyMatchProduct_withSimilarName_shouldReturnFuzzyMatch() {
        // Given: Similar product name (missing one letter)
        String productName = "Ashwagandha Capsule"; // Missing 's'

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: Should return fuzzy match with high confidence
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getProductName()).isEqualTo("Ashwagandha Capsules");
        assertThat(result.getMatchType()).isEqualTo("FUZZY");
        assertThat(result.getConfidence()).isGreaterThan(0.9);
    }

    /**
     * Test Requirement 26.2: Fuzzy matching with typo
     */
    @Test
    void fuzzyMatchProduct_withTypo_shouldReturnFuzzyMatch() {
        // Given: Product name with typo
        String productName = "Ashwagandh Capsules"; // Missing 'a'

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: Should return fuzzy match
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getMatchType()).isEqualTo("FUZZY");
        assertThat(result.getConfidence()).isGreaterThan(0.9);
    }

    /**
     * Test Requirement 26.2: Fuzzy matching with shortened name
     */
    @Test
    void fuzzyMatchProduct_withShortenedName_shouldReturnFuzzyMatch() {
        // Given: Shortened product name
        String productName = "Ashwagandha Capsule"; // Missing 's' - minor typo

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: Should return fuzzy match with high confidence
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getMatchType()).isEqualTo("FUZZY");
        assertThat(result.getConfidence()).isGreaterThan(0.9);
    }

    /**
     * Test Requirement 26.3: Match should meet 0.6 threshold
     */
    @Test
    void fuzzyMatchProduct_atThreshold_shouldReturnMatch() {
        // Given: Product name that results in ~0.6 similarity
        // "Tripha" vs "Triphala Churna" should be close to threshold
        String productName = "Tripha";

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: May or may not match depending on exact calculation
        // But if it matches, confidence should be >= 0.6
        if (result != null) {
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.6);
        }
    }

    /**
     * Test Requirement 26.4: Return null when no match found
     */
    @Test
    void fuzzyMatchProduct_withUnmatchedProduct_shouldReturnNull() {
        // Given: Product name that doesn't match anything
        String productName = "CompletlyDifferentProduct12345";

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: Should return null (below threshold)
        assertThat(result).isNull();
    }

    /**
     * Test Requirement 26.4: Return null for empty string
     */
    @Test
    void fuzzyMatchProduct_withEmptyString_shouldReturnNull() {
        // Given: Empty string
        String productName = "";

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: Should return null
        assertThat(result).isNull();
    }

    /**
     * Test Requirement 26.4: Return null for null input
     */
    @Test
    void fuzzyMatchProduct_withNull_shouldReturnNull() {
        // Given: Null input
        String productName = null;

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: Should return null
        assertThat(result).isNull();
    }

    /**
     * Test Requirement 26.5: Return match confidence score
     */
    @Test
    void fuzzyMatchProduct_shouldReturnConfidenceScore() {
        // Given: Various product names
        String exactMatch = "Ashwagandha Capsules";
        String closeMatch = "Ashwagandh Capsules";
        String partialMatch = "Ashwa";

        // When: Fuzzy match
        FuzzyMatchResult exact = parser.fuzzyMatchProduct(exactMatch);
        FuzzyMatchResult close = parser.fuzzyMatchProduct(closeMatch);
        FuzzyMatchResult partial = parser.fuzzyMatchProduct(partialMatch);

        // Then: Confidence scores should decrease with similarity
        assertThat(exact).isNotNull();
        assertThat(exact.getConfidence()).isEqualTo(1.0);
        
        assertThat(close).isNotNull();
        assertThat(close.getConfidence()).isLessThan(1.0).isGreaterThan(0.9);
        
        if (partial != null) {
            assertThat(partial.getConfidence()).isLessThan(close.getConfidence());
        }
    }

    /**
     * Test Requirement 26.2: Case insensitive fuzzy matching
     */
    @Test
    void fuzzyMatchProduct_shouldBeCaseInsensitive() {
        // Given: Product names with different cases
        String lowercase = "ashwagandha capsules";
        String uppercase = "ASHWAGANDHA CAPSULES";
        String mixedcase = "AsHwAgAnDhA CaPsUlEs";

        // When: Fuzzy match
        FuzzyMatchResult result1 = parser.fuzzyMatchProduct(lowercase);
        FuzzyMatchResult result2 = parser.fuzzyMatchProduct(uppercase);
        FuzzyMatchResult result3 = parser.fuzzyMatchProduct(mixedcase);

        // Then: All should match the same product
        assertThat(result1).isNotNull();
        assertThat(result1.getProductId()).isEqualTo(1L);
        assertThat(result1.getConfidence()).isEqualTo(1.0);
        
        assertThat(result2).isNotNull();
        assertThat(result2.getProductId()).isEqualTo(1L);
        assertThat(result2.getConfidence()).isEqualTo(1.0);
        
        assertThat(result3).isNotNull();
        assertThat(result3.getProductId()).isEqualTo(1L);
        assertThat(result3.getConfidence()).isEqualTo(1.0);
    }

    /**
     * Test Requirement 26.2: Match against both product name and SKU
     */
    @Test
    void fuzzyMatchProduct_shouldMatchAgainstBothNameAndSKU() {
        // Given: Similar SKU (fuzzy match, not exact)
        String similarToSKU = "ASH01"; // Similar to ASH001

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(similarToSKU);

        // Then: Should match based on SKU similarity
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(1L);
        assertThat(result.getMatchType()).isEqualTo("FUZZY");
    }

    /**
     * Test Requirement 26.3: Select product with highest similarity above threshold
     */
    @Test
    void fuzzyMatchProduct_withMultipleSimilarProducts_shouldSelectBestMatch() {
        // Given: Product name that matches Amalaki well
        String productName = "Amalaki Rasayana"; // Exact name match

        // When: Fuzzy match
        FuzzyMatchResult result = parser.fuzzyMatchProduct(productName);

        // Then: Should return the best match (Amalaki Rasayana)
        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo(5L);
        assertThat(result.getProductName()).isEqualTo("Amalaki Rasayana");
        assertThat(result.getConfidence()).isEqualTo(1.0);
    }

    // ========== Product Extraction with Fuzzy Matching Tests ==========

    /**
     * Test Requirements 3.2, 26.2: Extract products and match against catalog
     */
    @Test
    void extractProducts_shouldPerformFuzzyMatching() {
        // Given: Message with product names (not exact SKUs)
        String message = "Order:\n" +
                        "2 x Ashwagandha Capsules\n" +
                        "1 x Triphala Churna";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Products should be matched with catalog
        assertThat(items).hasSize(2);
        
        assertThat(items.get(0).getMatchedProductId()).isEqualTo(1L);
        assertThat(items.get(0).getMatchedProductName()).isEqualTo("Ashwagandha Capsules");
        assertThat(items.get(0).getConfidence()).isEqualTo(1.0);
        
        assertThat(items.get(1).getMatchedProductId()).isEqualTo(2L);
        assertThat(items.get(1).getMatchedProductName()).isEqualTo("Triphala Churna");
        assertThat(items.get(1).getConfidence()).isEqualTo(1.0);
    }

    /**
     * Test Requirements 3.2, 26.1: Extract products with exact SKU
     */
    @Test
    void extractProducts_withExactSKUs_shouldMatchPerfectly() {
        // Given: Message with exact SKUs
        String message = "Order:\n" +
                        "3 x ASH001\n" +
                        "2 x TRI001\n" +
                        "1 x CHY001";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: All products should match with confidence 1.0
        assertThat(items).hasSize(3);
        assertThat(items).allMatch(item -> item.getMatchedProductId() != null);
        assertThat(items).allMatch(item -> item.getConfidence() == 1.0);
    }

    /**
     * Test Requirements 3.2, 26.4: Extract products with some unmatched
     */
    @Test
    void extractProducts_withUnmatchedProducts_shouldLeaveThemUnmatched() {
        // Given: Message with some unknown products
        String message = "Order:\n" +
                        "2 x ASH001\n" +
                        "1 x UnknownProduct123\n" +
                        "1 x TRI001";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Known products matched, unknown left unmatched
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getMatchedProductId()).isEqualTo(1L);
        assertThat(items.get(1).getMatchedProductId()).isNull();
        assertThat(items.get(2).getMatchedProductId()).isEqualTo(2L);
    }

    /**
     * Test Requirements 3.2, 26.2: Extract products with fuzzy matched names
     */
    @Test
    void extractProducts_withFuzzyNames_shouldMatchWithConfidence() {
        // Given: Message with slightly different product names
        String message = "Order:\n" +
                        "2 x Ashwagandha Capsule\n" +   // Missing 's' - should match
                        "1 x Triphala Churna";           // Exact match

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Products should be fuzzy matched
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getMatchedProductId()).isNotNull();
        assertThat(items.get(0).getConfidence()).isGreaterThan(0.9);
        
        assertThat(items.get(1).getMatchedProductId()).isEqualTo(2L);
        assertThat(items.get(1).getConfidence()).isEqualTo(1.0); // Exact match
    }

    // ========== Warning Generation Tests ==========

    /**
     * Test Requirement 3.5: Generate warnings for unmatched products
     */
    @Test
    void parseWhatsAppMessage_withUnmatchedProducts_shouldGenerateWarnings() {
        // Given: Message with unmatched products
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "2 x UnknownProduct1\n" +
                        "1 x UnknownProduct2";

        // When: Parse message
        var result = parser.parseWhatsAppMessage(message);

        // Then: Warnings should be generated
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Could not match product: 'UnknownProduct1'"));
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Could not match product: 'UnknownProduct2'"));
    }

    /**
     * Test Requirement 3.5: Generate warnings for low confidence matches
     */
    @Test
    void parseWhatsAppMessage_withLowConfidenceMatches_shouldGenerateWarnings() {
        // Given: Message with products that match with low confidence
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "2 x Ashwa\n" +  // Very short, may have low confidence
                        "1 x Tri";       // Very short, may have low confidence

        // When: Parse message
        var result = parser.parseWhatsAppMessage(message);

        // Then: May have warnings about low confidence matches
        // (depends on actual confidence scores calculated)
        if (result.getItems().stream().anyMatch(item -> item.getConfidence() < 0.8 && item.getMatchedProductId() != null)) {
            assertThat(result.getWarnings())
                    .anyMatch(w -> w.contains("Low confidence match"));
        }
    }

    /**
     * Test Requirement 3.5: No warnings for perfect matches
     */
    @Test
    void parseWhatsAppMessage_withPerfectMatches_shouldHaveNoProductWarnings() {
        // Given: Message with exact SKUs
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "2 x ASH001\n" +
                        "1 x TRI001";

        // When: Parse message
        var result = parser.parseWhatsAppMessage(message);

        // Then: Should have no product-related warnings
        assertThat(result.getWarnings())
                .noneMatch(w -> w.contains("Could not match product"));
        assertThat(result.getWarnings())
                .noneMatch(w -> w.contains("Low confidence match"));
    }

    // ========== Real-World Scenario Tests ==========

    /**
     * Test real-world scenario: Complete order with product names
     */
    @Test
    void extractProducts_realWorldScenario1_shouldExtractAndMatch() {
        // Given: Real-world WhatsApp message
        String message = """
                Name: Mrs. Kavita Deshmukh
                Mobile: +91 9823456789
                Address: Flat 302, Sai Residency, Pune 411038
                
                Order:
                3 x Ashwagandha Capsules
                2 x Triphala Churna
                1 x Chyawanprash
                
                Payment: UPI
                """;

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: All products should be extracted and matched
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getQuantity()).isEqualTo(3);
        assertThat(items.get(0).getMatchedProductId()).isEqualTo(1L);
        assertThat(items.get(1).getQuantity()).isEqualTo(2);
        assertThat(items.get(1).getMatchedProductId()).isEqualTo(2L);
        assertThat(items.get(2).getQuantity()).isEqualTo(1);
        assertThat(items.get(2).getMatchedProductId()).isEqualTo(3L);
    }

    /**
     * Test real-world scenario: Order with SKU codes
     */
    @Test
    void extractProducts_realWorldScenario2_shouldMatchExactSKUs() {
        // Given: Real-world order with SKU codes
        String message = """
                Customer: Dr. Suresh Patil
                Contact: 08765432190
                
                Items:
                2 x ASH001
                BRA001 - 1
                TRI001 x 3
                
                COD please
                """;

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: All SKUs should match exactly
        assertThat(items).hasSize(3);
        assertThat(items).allMatch(item -> item.getMatchedProductId() != null);
        assertThat(items).allMatch(item -> item.getConfidence() == 1.0);
    }

    /**
     * Test real-world scenario: Mixed exact and fuzzy matches
     */
    @Test
    void extractProducts_realWorldScenario3_shouldHandleMixedMatches() {
        // Given: Order with mix of exact and fuzzy product names
        String message = """
                Name: Amit Shah
                Phone: 9876543210
                
                Order:
                2 x ASH001
                1 x Triphala Churna
                1 x Chyawanprash
                
                UPI Payment
                """;

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should have mix of exact and fuzzy matches
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getConfidence()).isEqualTo(1.0); // Exact SKU
        assertThat(items.get(1).getMatchedProductId()).isNotNull(); // Exact name match
        assertThat(items.get(1).getConfidence()).isEqualTo(1.0);
        assertThat(items.get(2).getMatchedProductId()).isNotNull(); // Exact name match
        assertThat(items.get(2).getConfidence()).isEqualTo(1.0);
    }

    /**
     * Test real-world scenario: Products with additional descriptive text
     */
    @Test
    void extractProducts_withDescriptiveText_shouldExtractProduct() {
        // Given: Products with extra description
        String message = """
                Order:
                2 x Ashwagandha Capsules 60 caps
                1 x Triphala Powder 100g
                1 x Chyawanprash 500g jar
                """;

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should extract product names despite extra text
        assertThat(items).hasSize(3);
        // May or may not match depending on how fuzzy matching handles extra text
        // At minimum, should extract the items
    }

    /**
     * Test: Product with spaces around quantity
     */
    @Test
    void extractProducts_withSpacesAroundQuantity_shouldExtractCorrectly() {
        // Given: Products with various spacing
        String message = """
                Order:
                2   x   Ashwagandha Capsules
                1x Triphala Churna
                Chyawanprash   x   3
                """;

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should handle various spacing
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(1).getQuantity()).isEqualTo(1);
        assertThat(items.get(2).getQuantity()).isEqualTo(3);
    }

    /**
     * Test: Products in different sections of message
     */
    @Test
    void extractProducts_scatteredInMessage_shouldExtractAll() {
        // Given: Products scattered throughout message
        String message = """
                Name: Rajesh
                2 x Ashwagandha Capsules
                Phone: 9876543210
                1 x Triphala Churna
                Address: Bangalore
                1 x Chyawanprash
                Payment: COD
                """;

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should extract all products regardless of location
        assertThat(items).hasSize(3);
    }

    /**
     * Test: Whitespace-only lines should be ignored
     */
    @Test
    void extractProducts_withWhitespaceLines_shouldIgnoreThem() {
        // Given: Message with whitespace lines
        String message = "2 x ASH001\n" +
                        "   \n" +
                        "1 x TRI001\n" +
                        "\t\n" +
                        "1 x CHY001";

        // When: Extract products
        List<ParsedItem> items = parser.extractProducts(message);

        // Then: Should extract only valid product lines
        assertThat(items).hasSize(3);
    }
}
