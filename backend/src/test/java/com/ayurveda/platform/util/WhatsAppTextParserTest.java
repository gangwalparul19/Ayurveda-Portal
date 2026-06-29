package com.ayurveda.platform.util;

import com.ayurveda.platform.util.WhatsAppTextParser.ParsedCustomer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WhatsAppTextParser, specifically for customer information extraction.
 * 
 * Tests Requirement 3.1: WhatsApp message parsing for customer information
 * - Extract customer name using "Name:" or "Customer:" patterns
 * - Extract Indian phone numbers (10 digits with optional +91/0 prefix)
 * - Extract address and pincode
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppTextParserTest {

    private WhatsAppTextParser parser;

    @BeforeEach
    void setUp() {
        // Create parser with null repository (not needed for customer info extraction tests)
        parser = new WhatsAppTextParser(null);
    }

    // ========== Name Extraction Tests ==========

    /**
     * Test Requirement 3.1: Extract name using "Name:" pattern
     */
    @Test
    void extractCustomerInfo_withNamePrefix_shouldExtractName() {
        // Given: WhatsApp message with "Name:" prefix
        String message = "Name: Rajesh Kumar\n" +
                        "Phone: 9876543210\n" +
                        "2 x Ashwagandha";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Name should be extracted
        assertThat(customer.getName()).isEqualTo("Rajesh Kumar");
    }

    /**
     * Test Requirement 3.1: Extract name using "Customer:" pattern
     */
    @Test
    void extractCustomerInfo_withCustomerPrefix_shouldExtractName() {
        // Given: WhatsApp message with "Customer:" prefix
        String message = "Customer: Priya Sharma\n" +
                        "Mobile: 8765432109\n" +
                        "1 x Triphala Churna";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Name should be extracted
        assertThat(customer.getName()).isEqualTo("Priya Sharma");
    }

    /**
     * Test Requirement 3.1: Case insensitive name extraction
     */
    @Test
    void extractCustomerInfo_withLowercaseNamePrefix_shouldExtractName() {
        // Given: WhatsApp message with lowercase "name:" prefix
        String message = "name: Amit Patel\n" +
                        "phone: 7654321098";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Name should be extracted (case insensitive)
        assertThat(customer.getName()).isEqualTo("Amit Patel");
    }

    /**
     * Test Requirement 3.1: Extract name with "Deliver to:" pattern
     */
    @Test
    void extractCustomerInfo_withDeliverToPrefix_shouldExtractName() {
        // Given: WhatsApp message with "Deliver to:" prefix
        String message = "Deliver to: Sunita Reddy\n" +
                        "Contact: 6543210987";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Name should be extracted
        assertThat(customer.getName()).isEqualTo("Sunita Reddy");
    }

    // ========== Phone Number Extraction Tests ==========

    /**
     * Test Requirement 3.1: Extract 10-digit Indian phone number
     */
    @Test
    void extractCustomerInfo_with10DigitPhone_shouldExtractPhone() {
        // Given: WhatsApp message with 10-digit phone number
        String message = "Name: Vikram Singh\n" +
                        "Phone: 9876543210\n" +
                        "Address: Delhi";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should be extracted
        assertThat(customer.getPhone()).isEqualTo("9876543210");
    }

    /**
     * Test Requirement 3.1: Extract phone with +91 prefix
     */
    @Test
    void extractCustomerInfo_withPlus91Prefix_shouldExtractPhone() {
        // Given: WhatsApp message with +91 prefix
        String message = "Name: Meena Gupta\n" +
                        "Mobile: +91 8765432109\n" +
                        "City: Mumbai";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should be extracted without prefix
        assertThat(customer.getPhone()).isEqualTo("8765432109");
    }

    /**
     * Test Requirement 3.1: Extract phone with 0 prefix
     */
    @Test
    void extractCustomerInfo_withZeroPrefix_shouldExtractPhone() {
        // Given: WhatsApp message with 0 prefix
        String message = "Name: Karan Malhotra\n" +
                        "Contact: 09876543210";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should be extracted without prefix
        assertThat(customer.getPhone()).isEqualTo("9876543210");
    }

    /**
     * Test Requirement 3.1: Extract standalone phone number without label
     */
    @Test
    void extractCustomerInfo_withStandalonePhone_shouldExtractPhone() {
        // Given: WhatsApp message with standalone phone (no "Phone:" label)
        String message = "Name: Anjali Verma\n" +
                        "9876543210\n" +
                        "Bangalore";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should be extracted
        assertThat(customer.getPhone()).isEqualTo("9876543210");
    }

    /**
     * Test Requirement 3.1: Validate Indian phone number format (starts with 6-9)
     */
    @Test
    void extractCustomerInfo_withInvalidPhoneStart_shouldNotExtract() {
        // Given: WhatsApp message with phone starting with invalid digit
        String message = "Name: Test User\n" +
                        "Phone: 1234567890\n" +
                        "Address: Test";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should not be extracted (invalid format)
        assertThat(customer.getPhone()).isNull();
    }

    /**
     * Test Requirement 3.1: Extract phone with "Mobile:" prefix
     */
    @Test
    void extractCustomerInfo_withMobilePrefix_shouldExtractPhone() {
        // Given: WhatsApp message with "Mobile:" prefix
        String message = "Customer: Rahul Desai\n" +
                        "Mobile: 7654321098";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should be extracted
        assertThat(customer.getPhone()).isEqualTo("7654321098");
    }

    /**
     * Test Requirement 3.1: Extract phone with "Contact:" prefix
     */
    @Test
    void extractCustomerInfo_withContactPrefix_shouldExtractPhone() {
        // Given: WhatsApp message with "Contact:" prefix
        String message = "Name: Neha Kapoor\n" +
                        "Contact: 6543210987";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should be extracted
        assertThat(customer.getPhone()).isEqualTo("6543210987");
    }

    // ========== Address Extraction Tests ==========

    /**
     * Test Requirement 3.1: Extract address with "Address:" prefix
     */
    @Test
    void extractCustomerInfo_withAddressPrefix_shouldExtractAddress() {
        // Given: WhatsApp message with "Address:" prefix
        String message = "Name: Ravi Kumar\n" +
                        "Phone: 9876543210\n" +
                        "Address: 123 MG Road, Bangalore 560001";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Address should be extracted
        assertThat(customer.getAddress()).isEqualTo("123 MG Road, Bangalore 560001");
    }

    /**
     * Test Requirement 3.1: Extract address with "Delivery:" prefix
     */
    @Test
    void extractCustomerInfo_withDeliveryPrefix_shouldExtractAddress() {
        // Given: WhatsApp message with "Delivery:" prefix
        String message = "Name: Pooja Shah\n" +
                        "Delivery: 45 Park Street, Kolkata";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Address should be extracted
        assertThat(customer.getAddress()).isEqualTo("45 Park Street, Kolkata");
    }

    /**
     * Test Requirement 3.1: Extract address with "Addr:" prefix
     */
    @Test
    void extractCustomerInfo_withAddrPrefix_shouldExtractAddress() {
        // Given: WhatsApp message with "Addr:" prefix
        String message = "Customer: Suresh Iyer\n" +
                        "Addr: 789 Beach Road, Chennai 600001";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Address should be extracted
        assertThat(customer.getAddress()).isEqualTo("789 Beach Road, Chennai 600001");
    }

    // ========== Pincode Extraction Tests ==========

    /**
     * Test Requirement 3.1: Extract 6-digit pincode from address
     */
    @Test
    void extractCustomerInfo_withPincodeInAddress_shouldExtractPincode() {
        // Given: WhatsApp message with pincode in address
        String message = "Name: Arjun Nair\n" +
                        "Phone: 9876543210\n" +
                        "Address: 456 Lake View, Hyderabad 500001";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Pincode should be extracted from address
        assertThat(customer.getPincode()).isEqualTo("500001");
    }

    /**
     * Test Requirement 3.1: Extract pincode from standalone line
     */
    @Test
    void extractCustomerInfo_withStandalonePincode_shouldExtractPincode() {
        // Given: WhatsApp message with pincode on separate line
        String message = "Name: Deepa Rao\n" +
                        "Phone: 8765432109\n" +
                        "Address: 78 Hill Road, Pune\n" +
                        "411001";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Pincode should be extracted
        assertThat(customer.getPincode()).isEqualTo("411001");
    }

    /**
     * Test Requirement 3.1: Pincode must be exactly 6 digits
     */
    @Test
    void extractCustomerInfo_withInvalidPincode_shouldNotExtract() {
        // Given: WhatsApp message with 5-digit number (not a valid pincode)
        String message = "Name: Test User\n" +
                        "Address: 123 Street, City 12345";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Pincode should not be extracted (invalid length)
        assertThat(customer.getPincode()).isNull();
    }

    // ========== Complete Message Parsing Tests ==========

    /**
     * Test Requirement 3.1: Extract all customer information from complete message
     */
    @Test
    void extractCustomerInfo_withCompleteMessage_shouldExtractAllFields() {
        // Given: Complete WhatsApp message with all customer info
        String message = "Name: Lakshmi Narayanan\n" +
                        "Phone: +91 9876543210\n" +
                        "Address: 234 Gandhi Street, Madurai 625001\n" +
                        "\n" +
                        "2 x Ashwagandha Capsules\n" +
                        "1 x Triphala Churna\n" +
                        "COD";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: All customer fields should be extracted
        assertThat(customer.getName()).isEqualTo("Lakshmi Narayanan");
        assertThat(customer.getPhone()).isEqualTo("9876543210");
        assertThat(customer.getAddress()).isEqualTo("234 Gandhi Street, Madurai 625001");
        assertThat(customer.getPincode()).isEqualTo("625001");
    }

    /**
     * Test Requirement 3.1: Handle message with mixed case labels
     */
    @Test
    void extractCustomerInfo_withMixedCaseLabels_shouldExtractAllFields() {
        // Given: WhatsApp message with mixed case labels
        String message = "CUSTOMER: Anil Mehta\n" +
                        "mobile: 8765432109\n" +
                        "Delivery Address: 567 Market Road, Ahmedabad 380001";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: All fields should be extracted (case insensitive)
        assertThat(customer.getName()).isEqualTo("Anil Mehta");
        assertThat(customer.getPhone()).isEqualTo("8765432109");
        assertThat(customer.getAddress()).isEqualTo("567 Market Road, Ahmedabad 380001");
        assertThat(customer.getPincode()).isEqualTo("380001");
    }

    /**
     * Test Requirement 3.1: Handle message with minimal information
     */
    @Test
    void extractCustomerInfo_withMinimalInfo_shouldExtractAvailableFields() {
        // Given: WhatsApp message with only name and phone
        String message = "Name: Sanjay Joshi\n" +
                        "9876543210";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Only available fields should be extracted
        assertThat(customer.getName()).isEqualTo("Sanjay Joshi");
        assertThat(customer.getPhone()).isEqualTo("9876543210");
        assertThat(customer.getAddress()).isNull();
        assertThat(customer.getPincode()).isNull();
    }

    /**
     * Test Requirement 3.1: Handle empty message
     */
    @Test
    void extractCustomerInfo_withEmptyMessage_shouldReturnEmptyCustomer() {
        // Given: Empty message
        String message = "";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: All fields should be null
        assertThat(customer.getName()).isNull();
        assertThat(customer.getPhone()).isNull();
        assertThat(customer.getAddress()).isNull();
        assertThat(customer.getPincode()).isNull();
    }

    /**
     * Test Requirement 3.1: Handle null message
     */
    @Test
    void extractCustomerInfo_withNullMessage_shouldReturnEmptyCustomer() {
        // Given: Null message
        String message = null;

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: All fields should be null
        assertThat(customer.getName()).isNull();
        assertThat(customer.getPhone()).isNull();
        assertThat(customer.getAddress()).isNull();
        assertThat(customer.getPincode()).isNull();
    }

    /**
     * Test Requirement 3.1: Handle message with no customer info
     */
    @Test
    void extractCustomerInfo_withNoCustomerInfo_shouldReturnEmptyCustomer() {
        // Given: Message with only product info
        String message = "2 x Ashwagandha\n" +
                        "1 x Triphala\n" +
                        "COD";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: All fields should be null
        assertThat(customer.getName()).isNull();
        assertThat(customer.getPhone()).isNull();
        assertThat(customer.getAddress()).isNull();
        assertThat(customer.getPincode()).isNull();
    }

    /**
     * Test Requirement 3.1: Handle message with phone number in middle of text
     */
    @Test
    void extractCustomerInfo_withPhoneInMiddleOfText_shouldExtractPhone() {
        // Given: Message with phone in middle of unstructured text
        String message = "Hi please deliver to\n" +
                        "Ravi 9876543210\n" +
                        "Bangalore address";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: Phone should be extracted from unstructured text
        assertThat(customer.getPhone()).isEqualTo("9876543210");
    }

    /**
     * Test Requirement 3.1: Handle multiple phone numbers (should extract first)
     */
    @Test
    void extractCustomerInfo_withMultiplePhones_shouldExtractFirstOccurrence() {
        // Given: Message with multiple phone numbers
        String message = "Name: Geeta Sharma\n" +
                        "Phone: 9876543210\n" +
                        "Alternate: 8765432109\n" +
                        "Address: Mumbai";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: First phone number should be extracted
        assertThat(customer.getPhone()).isEqualTo("9876543210");
    }

    /**
     * Test Requirement 3.1: Handle address with multiple lines
     */
    @Test
    void extractCustomerInfo_withMultiLineAddress_shouldExtractFirstLine() {
        // Given: Message with multi-line address (only first line after prefix)
        String message = "Name: Rahul Verma\n" +
                        "Phone: 9876543210\n" +
                        "Address: 123 Main Street\n" +
                        "Near City Mall\n" +
                        "Mumbai 400001";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: First address line should be extracted
        assertThat(customer.getAddress()).isEqualTo("123 Main Street");
        // Pincode should still be extracted from full text
        assertThat(customer.getPincode()).isEqualTo("400001");
    }

    /**
     * Test Requirement 3.1: Real-world WhatsApp message example 1
     */
    @Test
    void extractCustomerInfo_realWorldExample1_shouldExtractAllFields() {
        // Given: Real-world WhatsApp order message
        String message = "Name: Mrs. Kavita Deshmukh\n" +
                        "Mobile: +91 9823456789\n" +
                        "Address: Flat 302, Sai Residency, Pune 411038\n" +
                        "\n" +
                        "Order:\n" +
                        "3 x Ashwagandha 60 caps\n" +
                        "2 x Triphala Powder 100g\n" +
                        "1 x Chyawanprash 500g\n" +
                        "\n" +
                        "Payment: UPI";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: All fields should be extracted correctly
        assertThat(customer.getName()).isEqualTo("Mrs. Kavita Deshmukh");
        assertThat(customer.getPhone()).isEqualTo("9823456789");
        assertThat(customer.getAddress()).isEqualTo("Flat 302, Sai Residency, Pune 411038");
        assertThat(customer.getPincode()).isEqualTo("411038");
    }

    /**
     * Test Requirement 3.1: Real-world WhatsApp message example 2
     */
    @Test
    void extractCustomerInfo_realWorldExample2_shouldExtractAllFields() {
        // Given: Real-world WhatsApp order message with different format
        String message = "Customer: Dr. Suresh Patil\n" +
                        "Contact: 08765432190\n" +
                        "Delivery to: 45/B, Gandhi Nagar, Near Bus Stand, Nagpur - 440001\n" +
                        "\n" +
                        "Items needed:\n" +
                        "Ashwagandha - 2 bottles\n" +
                        "Brahmi Ghrita - 1\n" +
                        "\n" +
                        "COD please";

        // When: Extract customer info
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then: All fields should be extracted correctly
        assertThat(customer.getName()).isEqualTo("Dr. Suresh Patil");
        assertThat(customer.getPhone()).isEqualTo("8765432190");
        assertThat(customer.getAddress()).isEqualTo("45/B, Gandhi Nagar, Near Bus Stand, Nagpur - 440001");
        assertThat(customer.getPincode()).isEqualTo("440001");
    }
}
