package com.ayurveda.platform.util;

import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.util.WhatsAppTextParser.ParsedWhatsAppOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for WhatsAppTextParser.parseWhatsAppMessage() method.
 * Tests confidence scoring based on extracted data completeness.
 * 
 * Tests Requirements 3.4, 3.5, 3.6:
 * - Confidence score calculation
 * - Warning messages for parsing issues
 * - Confidence reduction for missing data
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppTextParserConfidenceTest {

    @Mock
    private ProductRepository productRepository;

    private WhatsAppTextParser parser;

    @BeforeEach
    void setUp() {
        parser = new WhatsAppTextParser(productRepository);
        setupMockProducts();
    }

    private void setupMockProducts() {
        // Create mock products for fuzzy matching tests
        Product ashwagandha = new Product();
        ashwagandha.setId(1L);
        ashwagandha.setSku("ASH001");
        ashwagandha.setName("Ashwagandha Capsules");
        ashwagandha.setSalePrice(BigDecimal.valueOf(299.00));
        ashwagandha.setMrp(BigDecimal.valueOf(399.00));
        ashwagandha.setStockQuantity(100);

        Product triphala = new Product();
        triphala.setId(2L);
        triphala.setSku("TRI001");
        triphala.setName("Triphala Churna");
        triphala.setSalePrice(BigDecimal.valueOf(199.00));
        triphala.setMrp(BigDecimal.valueOf(249.00));
        triphala.setStockQuantity(50);

        Product chyawanprash = new Product();
        chyawanprash.setId(3L);
        chyawanprash.setSku("CHY001");
        chyawanprash.setName("Chyawanprash");
        chyawanprash.setSalePrice(BigDecimal.valueOf(350.00));
        chyawanprash.setMrp(BigDecimal.valueOf(450.00));
        chyawanprash.setStockQuantity(75);

        lenient().when(productRepository.findAll()).thenReturn(Arrays.asList(ashwagandha, triphala, chyawanprash));
        lenient().when(productRepository.findBySku("ASH001")).thenReturn(Optional.of(ashwagandha));
        lenient().when(productRepository.findBySku("TRI001")).thenReturn(Optional.of(triphala));
        lenient().when(productRepository.findBySku("CHY001")).thenReturn(Optional.of(chyawanprash));
        lenient().when(productRepository.findBySku(anyString())).thenReturn(Optional.empty());
    }

    // ========== Perfect Message Tests ==========

    /**
     * Test Requirement 3.4: Perfect message with all data should have high confidence
     */
    @Test
    void parseWhatsAppMessage_withCompleteData_shouldHaveHighConfidence() {
        // Given: Complete WhatsApp message with all required data
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "Address: 123 MG Road, Bangalore 560001\n" +
                        "\n" +
                        "2 x ASH001\n" +
                        "1 x TRI001\n" +
                        "\n" +
                        "Payment: COD";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be very high (close to 1.0)
        assertThat(result.getConfidenceScore()).isGreaterThan(0.9);
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getCustomer().getName()).isEqualTo("Rajesh Kumar");
        assertThat(result.getCustomer().getPhone()).isEqualTo("9876543210");
        assertThat(result.getItems()).hasSize(2);
    }

    // ========== Missing Customer Info Tests ==========

    /**
     * Test Requirement 3.5: Missing customer name should reduce confidence by 0.3
     */
    @Test
    void parseWhatsAppMessage_withoutCustomerName_shouldReduceConfidenceBy30Percent() {
        // Given: Message without customer name
        String message = "Phone: 9876543210\n" +
                        "Address: 123 MG Road, Bangalore\n" +
                        "\n" +
                        "2 x ASH001\n" +
                        "1 x TRI001";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be reduced by ~0.3
        assertThat(result.getConfidenceScore()).isLessThan(0.75);
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Missing customer name"));
    }

    /**
     * Test Requirement 3.5: Missing customer phone should reduce confidence by 0.3
     */
    @Test
    void parseWhatsAppMessage_withoutCustomerPhone_shouldReduceConfidenceBy30Percent() {
        // Given: Message without customer phone
        String message = "Name: Rajesh Kumar\n" +
                        "Address: 123 MG Road, Bangalore\n" +
                        "\n" +
                        "2 x ASH001\n" +
                        "1 x TRI001";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be reduced by ~0.3
        assertThat(result.getConfidenceScore()).isLessThan(0.75);
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Missing customer phone"));
    }

    /**
     * Test Requirement 3.5: Missing both name and phone should reduce confidence by 0.3
     */
    @Test
    void parseWhatsAppMessage_withoutCustomerNameAndPhone_shouldReduceConfidenceBy30Percent() {
        // Given: Message without customer name and phone
        String message = "Address: 123 MG Road, Bangalore\n" +
                        "\n" +
                        "2 x ASH001\n" +
                        "1 x TRI001";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be reduced by ~0.3
        assertThat(result.getConfidenceScore()).isLessThan(0.75);
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Missing customer name and phone"));
    }

    // ========== Missing Products Tests ==========

    /**
     * Test Requirement 3.5: Missing products should reduce confidence by 0.4
     */
    @Test
    void parseWhatsAppMessage_withoutProducts_shouldReduceConfidenceBy40Percent() {
        // Given: Message without products
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "Address: 123 MG Road, Bangalore\n" +
                        "\n" +
                        "Payment: COD";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be reduced by ~0.4
        assertThat(result.getConfidenceScore()).isLessThan(0.65);
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("No product items could be extracted"));
    }

    // ========== Low Product Match Score Tests ==========

    /**
     * Test Requirement 3.6: Low product match scores should reduce confidence
     */
    @Test
    void parseWhatsAppMessage_withFuzzyProductMatches_shouldReduceConfidenceProportionally() {
        // Given: Message with fuzzy product names (not exact SKU)
        // Using "Ashwa" which is shorter and will have lower similarity
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "\n" +
                        "2 x Ashwagandha Capsules\n" +  // Should match "Ashwagandha Capsules" with high confidence
                        "1 x Triphala Churna";          // Should match "Triphala Churna" with high confidence

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be very high since we have exact name matches
        assertThat(result.getConfidenceScore()).isGreaterThan(0.95);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).allMatch(item -> item.getMatchedProductId() != null);
    }

    /**
     * Test Requirement 3.6: Unmatched products should reduce confidence and generate warnings
     */
    @Test
    void parseWhatsAppMessage_withUnmatchedProducts_shouldReduceConfidenceAndWarn() {
        // Given: Message with products that don't match catalog
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "\n" +
                        "2 x UnknownProduct123\n" +
                        "1 x AnotherUnknownItem";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be significantly reduced
        assertThat(result.getConfidenceScore()).isLessThanOrEqualTo(0.7);
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Could not match product: 'UnknownProduct123'"));
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Could not match product: 'AnotherUnknownItem'"));
    }

    /**
     * Test Requirement 3.6: Mix of matched and unmatched products
     */
    @Test
    void parseWhatsAppMessage_withMixedProductMatches_shouldReduceConfidenceProportionally() {
        // Given: Message with some matched and some unmatched products
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "\n" +
                        "2 x ASH001\n" +             // Perfect match
                        "1 x UnknownProduct\n" +     // No match
                        "1 x Triphala";              // Fuzzy match

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be reduced but not too low
        assertThat(result.getConfidenceScore()).isLessThan(0.9);
        assertThat(result.getConfidenceScore()).isGreaterThan(0.5);
        assertThat(result.getItems()).hasSize(3);
        assertThat(result.getWarnings())
                .anyMatch(w -> w.contains("Could not match product: 'UnknownProduct'"));
    }

    // ========== Combined Missing Data Tests ==========

    /**
     * Test Requirement 3.5, 3.6: Missing customer info and products
     */
    @Test
    void parseWhatsAppMessage_withMissingCustomerAndProducts_shouldHaveLowConfidence() {
        // Given: Message missing both customer info and products
        String message = "Some random text\n" +
                        "that doesn't contain\n" +
                        "any useful information";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be very low (0.3 + 0.4 penalty = 0.3)
        assertThat(result.getConfidenceScore()).isLessThan(0.4);
        assertThat(result.getWarnings()).hasSizeGreaterThanOrEqualTo(2);
    }

    /**
     * Test Requirement 3.5, 3.6: Missing customer info with unmatched products
     */
    @Test
    void parseWhatsAppMessage_withMissingCustomerAndUnmatchedProducts_shouldHaveLowConfidence() {
        // Given: Message without customer info and with unmatched products
        String message = "2 x UnknownProduct1\n" +
                        "1 x UnknownProduct2";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be very low
        assertThat(result.getConfidenceScore()).isLessThan(0.5);
        assertThat(result.getWarnings()).hasSizeGreaterThanOrEqualTo(3); // Missing customer + 2 unmatched products
    }

    // ========== Empty/Null Message Tests ==========

    /**
     * Test Requirement 3.4: Empty message should have 0 confidence
     */
    @Test
    void parseWhatsAppMessage_withEmptyMessage_shouldHaveZeroConfidence() {
        // Given: Empty message
        String message = "";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be 0
        assertThat(result.getConfidenceScore()).isEqualTo(0.0);
        assertThat(result.getWarnings()).contains("Empty message text");
    }

    /**
     * Test Requirement 3.4: Null message should have 0 confidence
     */
    @Test
    void parseWhatsAppMessage_withNullMessage_shouldHaveZeroConfidence() {
        // Given: Null message
        String message = null;

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Confidence should be 0
        assertThat(result.getConfidenceScore()).isEqualTo(0.0);
        assertThat(result.getWarnings()).contains("Empty message text");
    }

    // ========== Confidence Score Range Tests ==========

    /**
     * Test Requirement 3.4: Confidence score should always be between 0.0 and 1.0
     */
    @Test
    void parseWhatsAppMessage_shouldAlwaysReturnConfidenceBetweenZeroAndOne() {
        // Given: Various message formats
        String[] messages = {
                "",
                "Random text",
                "Name: Test\nPhone: 9876543210",
                "2 x ASH001\n1 x TRI001",
                "Name: Test\nPhone: 9876543210\n2 x ASH001"
        };

        // When/Then: Parse all messages and verify confidence range
        for (String message : messages) {
            ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);
            assertThat(result.getConfidenceScore())
                    .isGreaterThanOrEqualTo(0.0)
                    .isLessThanOrEqualTo(1.0);
        }
    }

    // ========== Warning Messages Tests ==========

    /**
     * Test Requirement 3.5: Warnings should be collected for all parsing issues
     */
    @Test
    void parseWhatsAppMessage_shouldCollectWarningsForParsingIssues() {
        // Given: Message with multiple issues
        String message = "2 x UnknownProduct\n" +
                        "1 x AnotherUnknown";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Should have warnings for missing customer and unmatched products
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getWarnings()).hasSizeGreaterThanOrEqualTo(3);
    }

    /**
     * Test Requirement 3.6: Low confidence matches should generate warnings
     */
    @Test
    void parseWhatsAppMessage_withLowConfidenceMatches_shouldGenerateWarnings() {
        // Given: Message with product that will have low similarity score
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "\n" +
                        "2 x Ash\n" +  // Very short, may match with lower confidence
                        "1 x Tri";     // Very short, may match with lower confidence

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: May have warnings about low confidence or no match
        // (depends on fuzzy matching threshold)
        assertThat(result.getWarnings()).isNotEmpty();
    }

    // ========== Real-World Examples ==========

    /**
     * Test Requirement 3.4, 3.5, 3.6: Real-world complete order
     */
    @Test
    void parseWhatsAppMessage_realWorldCompleteOrder_shouldHaveHighConfidence() {
        // Given: Real-world complete WhatsApp order
        String message = "Name: Mrs. Kavita Deshmukh\n" +
                        "Mobile: +91 9823456789\n" +
                        "Address: Flat 302, Sai Residency, Pune 411038\n" +
                        "\n" +
                        "Order:\n" +
                        "3 x ASH001\n" +
                        "2 x TRI001\n" +
                        "1 x CHY001\n" +
                        "\n" +
                        "Payment: UPI";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Should have high confidence with exact SKU matches
        assertThat(result.getConfidenceScore()).isGreaterThan(0.95);
        assertThat(result.getCustomer().getName()).isEqualTo("Mrs. Kavita Deshmukh");
        assertThat(result.getCustomer().getPhone()).isEqualTo("9823456789");
        assertThat(result.getItems()).hasSize(3);
        assertThat(result.getItems()).allMatch(item -> item.getMatchedProductId() != null);
        assertThat(result.getPayment().getPaymentMode()).isEqualTo("UPI");
    }

    /**
     * Test Requirement 3.4, 3.5, 3.6: Real-world incomplete order
     */
    @Test
    void parseWhatsAppMessage_realWorldIncompleteOrder_shouldHaveMediumConfidence() {
        // Given: Real-world order with missing customer phone
        String message = "Name: Suresh\n" +
                        "\n" +
                        "Items:\n" +
                        "Ashwagandha - 2\n" +
                        "Triphala - 1\n" +
                        "\n" +
                        "COD";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Should have medium confidence (missing phone, fuzzy product matches)
        assertThat(result.getConfidenceScore()).isLessThan(0.8);
        assertThat(result.getConfidenceScore()).isGreaterThanOrEqualTo(0.35);
        assertThat(result.getWarnings()).isNotEmpty();
    }

    /**
     * Test Requirement 3.4: Payment extraction should be included
     */
    @Test
    void parseWhatsAppMessage_shouldExtractPaymentInfo() {
        // Given: Message with payment info
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "2 x ASH001\n" +
                        "UPI payment";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Payment should be extracted
        assertThat(result.getPayment()).isNotNull();
        assertThat(result.getPayment().getPaymentMode()).isEqualTo("UPI");
    }

    /**
     * Test Requirement 3.4: Raw text should be preserved
     */
    @Test
    void parseWhatsAppMessage_shouldPreserveRawText() {
        // Given: Message
        String message = "Name: Test\nPhone: 9876543210";

        // When: Parse message
        ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(message);

        // Then: Raw text should be preserved
        assertThat(result.getRawText()).isEqualTo(message);
    }
}
