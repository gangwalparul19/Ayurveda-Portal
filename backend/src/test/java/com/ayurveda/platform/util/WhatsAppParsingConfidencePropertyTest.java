package com.ayurveda.platform.util;

import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property-Based Tests for WhatsApp Parsing Confidence using jqwik.
 * 
 * **Validates: Requirements 3.4, 3.5**
 * 
 * This test suite validates that the WhatsApp parser:
 * - Always returns a confidence score between 0.0 and 1.0
 * - Returns confidence > 0 only when some data is extracted (customer or products)
 */
class WhatsAppParsingConfidencePropertyTest {

    /**
     * Get parser instance for each test.
     * Creates a mock repository with test products.
     */
    private WhatsAppTextParser getParser() {
        // Create mock products
        List<Product> mockProducts = createMockProducts();
        
        // Create mock repository using Mockito
        ProductRepository mockRepository = Mockito.mock(ProductRepository.class);
        
        // Setup mock behavior
        when(mockRepository.findAll()).thenReturn(mockProducts);
        when(mockRepository.findBySku(anyString())).thenAnswer(invocation -> {
            String sku = invocation.getArgument(0);
            return mockProducts.stream()
                    .filter(p -> p.getSku().equalsIgnoreCase(sku))
                    .findFirst();
        });
        
        return new WhatsAppTextParser(mockRepository);
    }

    /**
     * **Property 8: WhatsApp Parsing Confidence**
     * **Validates: Requirements 3.4, 3.5**
     * 
     * Property: For all parsed WhatsApp messages, confidence score must be between 0.0 and 1.0
     * 
     * This property verifies:
     * - 0.0 ≤ confidenceScore ≤ 1.0 (Requirement 3.4)
     * - confidenceScore > 0.0 implies (customer ≠ null OR products.size() > 0) (Requirement 3.5)
     */
    @Property(tries = 1000)
    @Label("WhatsApp Parsing Confidence: 0.0 ≤ confidence ≤ 1.0 AND confidence > 0 implies data extracted")
    void whatsappParsingConfidenceBounds(
            @ForAll("whatsappMessages") String messageText
    ) {
        // Arrange: Get parser instance
        WhatsAppTextParser parser = getParser();
        
        // Act: Parse the WhatsApp message
        WhatsAppTextParser.ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(messageText);
        
        // Assert Property 1: Confidence score is within valid range [0.0, 1.0]
        Double confidenceScore = result.getConfidenceScore();
        assert confidenceScore != null : "Confidence score must not be null";
        assert confidenceScore >= 0.0 && confidenceScore <= 1.0 :
                String.format("Confidence score %.4f must be between 0.0 and 1.0", confidenceScore);
        
        // Assert Property 2: Minimum data requirement
        // If confidence > 0.0, then either customer info OR products must be present
        if (confidenceScore > 0.0) {
            boolean hasCustomerData = result.getCustomer() != null && 
                    ((result.getCustomer().getName() != null && !result.getCustomer().getName().isBlank()) || 
                     (result.getCustomer().getPhone() != null && !result.getCustomer().getPhone().isBlank()));
            boolean hasProducts = result.getItems() != null && !result.getItems().isEmpty();
            
            assert hasCustomerData || hasProducts :
                    String.format("Confidence score %.4f > 0.0 but no data extracted. Customer name: %s, phone: %s, Products: %d",
                            confidenceScore, 
                            result.getCustomer() != null ? result.getCustomer().getName() : "null",
                            result.getCustomer() != null ? result.getCustomer().getPhone() : "null",
                            result.getItems() != null ? result.getItems().size() : 0);
        }
        
        // Additional validation: If confidence is 0.0, it should be empty or unparseable
        if (confidenceScore == 0.0) {
            // Empty messages should have 0.0 confidence
            boolean isEmpty = messageText == null || messageText.isBlank();
            if (isEmpty) {
                assert result.getItems() == null || result.getItems().isEmpty() :
                        "Empty message should have no items";
            }
        }
    }

    /**
     * **Property 8: WhatsApp Parsing Confidence - Empty Messages**
     * **Validates: Requirements 3.4, 3.5**
     * 
     * Specific test case: Empty or blank messages should return 0.0 confidence
     */
    @Property(tries = 100)
    @Label("Empty or blank WhatsApp messages should have 0.0 confidence")
    void emptyMessagesHaveZeroConfidence(
            @ForAll("emptyOrBlankMessages") String messageText
    ) {
        // Arrange
        WhatsAppTextParser parser = getParser();
        
        // Act
        WhatsAppTextParser.ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(messageText);
        
        // Assert
        assert result.getConfidenceScore() != null : "Confidence score must not be null";
        assert result.getConfidenceScore() == 0.0 :
                String.format("Empty/blank message should have 0.0 confidence, got %.4f", 
                        result.getConfidenceScore());
    }

    /**
     * **Property 8: WhatsApp Parsing Confidence - Complete Messages**
     * **Validates: Requirements 3.4, 3.5**
     * 
     * Messages with complete customer info and valid products should have high confidence (> 0.5)
     */
    @Property(tries = 200)
    @Label("Complete WhatsApp messages with customer and products should have high confidence")
    void completeMessagesHaveHighConfidence(
            @ForAll("completeMessages") String messageText
    ) {
        // Arrange
        WhatsAppTextParser parser = getParser();
        
        // Act
        WhatsAppTextParser.ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(messageText);
        
        // Assert
        assert result.getConfidenceScore() != null : "Confidence score must not be null";
        
        // Complete messages should have confidence > 0.5
        // This is a heuristic based on the parser's scoring algorithm:
        // - Start with 1.0
        // - Deduct 0.3 for missing customer info (we have complete info)
        // - Deduct 0.4 for no products (we have products)
        // - Deduct up to 0.3 for low match scores (we use exact SKUs, so high match)
        // Expected: ~0.9 or higher for complete messages
        assert result.getConfidenceScore() >= 0.5 :
                String.format("Complete message should have confidence >= 0.5, got %.4f", 
                        result.getConfidenceScore());
        
        // Verify data was actually extracted
        assert result.getCustomer() != null : "Customer should be extracted";
        assert result.getCustomer().getName() != null : "Customer name should be extracted";
        assert result.getCustomer().getPhone() != null : "Customer phone should be extracted";
        assert result.getItems() != null && !result.getItems().isEmpty() : 
                "Products should be extracted";
    }

    /**
     * **Property 8: WhatsApp Parsing Confidence - Partial Messages**
     * **Validates: Requirements 3.4, 3.5**
     * 
     * Messages with only customer info (no products) should have medium confidence
     */
    @Property(tries = 200)
    @Label("WhatsApp messages with only customer info should have medium confidence")
    void customerOnlyMessagesHaveMediumConfidence(
            @ForAll("customerOnlyMessages") String messageText
    ) {
        // Arrange
        WhatsAppTextParser parser = getParser();
        
        // Act
        WhatsAppTextParser.ParsedWhatsAppOrder result = parser.parseWhatsAppMessage(messageText);
        
        // Assert: Confidence should be between 0.0 and 1.0
        assert result.getConfidenceScore() != null : "Confidence score must not be null";
        assert result.getConfidenceScore() >= 0.0 && result.getConfidenceScore() <= 1.0 :
                String.format("Confidence score %.4f must be between 0.0 and 1.0", 
                        result.getConfidenceScore());
        
        // Customer info should be present
        if (result.getConfidenceScore() > 0.0) {
            assert result.getCustomer() != null : "Customer should be present for non-zero confidence";
        }
        
        // Scoring logic: Missing products reduces confidence by 0.4
        // So max confidence for customer-only is 0.6 (1.0 - 0.4)
        // But we might have incomplete customer info, so it could be lower
        assert result.getConfidenceScore() <= 0.7 :
                String.format("Customer-only message should have confidence <= 0.7, got %.4f", 
                        result.getConfidenceScore());
    }

    // ============== Arbitraries (Generators) ==============

    /**
     * Generator for various WhatsApp message formats
     */
    @Provide
    Arbitrary<String> whatsappMessages() {
        return Arbitraries.oneOf(
                emptyOrBlankMessages(),
                completeMessages(),
                customerOnlyMessages(),
                productsOnlyMessages(),
                malformedMessages(),
                randomTextMessages()
        );
    }

    /**
     * Generator for empty or blank messages
     */
    @Provide
    Arbitrary<String> emptyOrBlankMessages() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\n\n"),
                Arbitraries.just("\t  \n  "),
                Arbitraries.just(null)
        );
    }

    /**
     * Generator for complete WhatsApp messages with customer info and products
     */
    @Provide
    Arbitrary<String> completeMessages() {
        Arbitrary<String> customerNames = Arbitraries.of(
                "Rajesh Kumar", "Priya Sharma", "Amit Patel", "Sunita Singh", "Vijay Mehta"
        );
        
        Arbitrary<String> phones = Arbitraries.of(
                "9876543210", "+919876543210", "8765432109", "+918765432109", "7654321098"
        );
        
        Arbitrary<String> addresses = Arbitraries.of(
                "123 MG Road, Bangalore",
                "45 Park Street, Kolkata",
                "78 Marine Drive, Mumbai",
                "12 Connaught Place, Delhi",
                "56 Anna Salai, Chennai"
        );
        
        Arbitrary<String> products = Arbitraries.of(
                "CHYW-001 x 2", "TULS-001 x 1", "ASHW-001 x 3",
                "CHYW-001", "TULS-001", "ASHW-001"
        );
        
        return Combinators.combine(customerNames, phones, addresses, products)
                .as((name, phone, address, product) ->
                        String.format("Name: %s\nPhone: %s\nAddress: %s\n%s",
                                name, phone, address, product)
                );
    }

    /**
     * Generator for messages with only customer information
     */
    @Provide
    Arbitrary<String> customerOnlyMessages() {
        Arbitrary<String> customerNames = Arbitraries.of(
                "Rajesh Kumar", "Priya Sharma", "Amit Patel"
        );
        
        Arbitrary<String> phones = Arbitraries.of(
                "9876543210", "+919876543210", "8765432109"
        );
        
        return Combinators.combine(customerNames, phones)
                .as((name, phone) ->
                        String.format("Name: %s\nPhone: %s", name, phone)
                );
    }

    /**
     * Generator for messages with only product information
     */
    @Provide
    Arbitrary<String> productsOnlyMessages() {
        Arbitrary<Integer> quantity = Arbitraries.integers().between(1, 10);
        Arbitrary<String> productSku = Arbitraries.of("CHYW-001", "TULS-001", "ASHW-001");
        
        return Combinators.combine(productSku, quantity)
                .as((sku, qty) -> String.format("%s x %d", sku, qty));
    }

    /**
     * Generator for malformed messages with partial or incorrect data
     */
    @Provide
    Arbitrary<String> malformedMessages() {
        return Arbitraries.of(
                "Phone: invalid-phone-number",
                "Random product: XYZ-999 x 5",
                "Address: 123 Some Street\nNo name or phone",
                "Mixed: Some random text Name: Amit Phone:",
                "Special chars: @#$%^&*() Name: Test",
                "Numbers only: 123456789012345",
                "Product without quantity: CHYW-001",
                "Quantity without product: x 5"
        );
    }

    /**
     * Generator for random text messages
     */
    @Provide
    Arbitrary<String> randomTextMessages() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '\n', ',', '.', ':', '-')
                .ofMinLength(0)
                .ofMaxLength(500);
    }

    // ============== Helper Methods ==============

    /**
     * Create mock products for fuzzy matching tests
     */
    private List<Product> createMockProducts() {
        List<Product> products = new ArrayList<>();
        
        products.add(Product.builder()
                .id(1L)
                .sku("CHYW-001")
                .name("Chyawanprash 500g")
                .mrp(new BigDecimal("299.00"))
                .salePrice(new BigDecimal("249.00"))
                .category("Health Supplements")
                .stockQuantity(100)
                .build());
        
        products.add(Product.builder()
                .id(2L)
                .sku("TULS-001")
                .name("Tulsi Drops 30ml")
                .mrp(new BigDecimal("150.00"))
                .salePrice(new BigDecimal("120.00"))
                .category("Immunity")
                .stockQuantity(50)
                .build());
        
        products.add(Product.builder()
                .id(3L)
                .sku("ASHW-001")
                .name("Ashwagandha Capsules 60s")
                .mrp(new BigDecimal("499.00"))
                .salePrice(new BigDecimal("399.00"))
                .category("Stress Relief")
                .stockQuantity(75)
                .build());
        
        products.add(Product.builder()
                .id(4L)
                .sku("TRIK-001")
                .name("Triphala Churna 100g")
                .mrp(new BigDecimal("180.00"))
                .salePrice(new BigDecimal("150.00"))
                .category("Digestive")
                .stockQuantity(120)
                .build());
        
        products.add(Product.builder()
                .id(5L)
                .sku("GILOY-001")
                .name("Giloy Tablets 60s")
                .mrp(new BigDecimal("250.00"))
                .salePrice(new BigDecimal("200.00"))
                .category("Immunity")
                .stockQuantity(80)
                .build());
        
        return products;
    }
}
