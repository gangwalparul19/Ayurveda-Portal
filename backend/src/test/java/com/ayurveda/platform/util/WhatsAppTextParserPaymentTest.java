package com.ayurveda.platform.util;

import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.util.WhatsAppTextParser.ParsedPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WhatsAppTextParser payment information extraction.
 * 
 * Tests Requirement 3.3:
 * - Payment mode detection (COD, UPI, BANK_TRANSFER, ONLINE, CREDIT)
 * - Payment amount extraction if present
 * - Default to COD if not detected
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppTextParserPaymentTest {

    @Mock
    private ProductRepository productRepository;

    private WhatsAppTextParser parser;

    @BeforeEach
    void setUp() {
        parser = new WhatsAppTextParser(productRepository);
    }

    /**
     * Test Requirement 3.3: COD detection
     * Verifies that COD (Cash on Delivery) keyword is correctly detected
     */
    @Test
    void extractPaymentInfo_withCOD_shouldDetectCOD() {
        // Given: WhatsApp text with COD keyword
        String text = "Customer Name: John Doe\n" +
                      "2 x Product A\n" +
                      "Payment: COD";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify COD is detected
        assertThat(payment.getPaymentMode()).isEqualTo("COD");
    }

    /**
     * Test Requirement 3.3: COD detection with variations
     */
    @Test
    void extractPaymentInfo_withCODVariations_shouldDetectCOD() {
        assertThat(parser.extractPaymentInfo("payment cod").getPaymentMode()).isEqualTo("COD");
        assertThat(parser.extractPaymentInfo("COD payment").getPaymentMode()).isEqualTo("COD");
        assertThat(parser.extractPaymentInfo("Cash on Delivery").getPaymentMode()).isEqualTo("COD");
        assertThat(parser.extractPaymentInfo("cash on delivery").getPaymentMode()).isEqualTo("COD");
        assertThat(parser.extractPaymentInfo("Pay by cash").getPaymentMode()).isEqualTo("COD");
    }

    /**
     * Test Requirement 3.3: UPI detection
     * Verifies that UPI payment keywords are correctly detected
     */
    @Test
    void extractPaymentInfo_withUPI_shouldDetectUPI() {
        // Given: WhatsApp text with UPI keyword
        String text = "Customer Name: Jane Doe\n" +
                      "3 x Product B\n" +
                      "Payment: UPI";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify UPI is detected
        assertThat(payment.getPaymentMode()).isEqualTo("UPI");
    }

    /**
     * Test Requirement 3.3: UPI detection with various payment apps
     */
    @Test
    void extractPaymentInfo_withUPIVariations_shouldDetectUPI() {
        assertThat(parser.extractPaymentInfo("payment via upi").getPaymentMode()).isEqualTo("UPI");
        assertThat(parser.extractPaymentInfo("GPay payment").getPaymentMode()).isEqualTo("UPI");
        assertThat(parser.extractPaymentInfo("Google Pay").getPaymentMode()).isEqualTo("UPI");
        assertThat(parser.extractPaymentInfo("PhonePe").getPaymentMode()).isEqualTo("UPI");
        assertThat(parser.extractPaymentInfo("Paytm").getPaymentMode()).isEqualTo("UPI");
        assertThat(parser.extractPaymentInfo("BHIM UPI").getPaymentMode()).isEqualTo("UPI");
    }

    /**
     * Test Requirement 3.3: Bank Transfer detection
     * Verifies that bank transfer keywords are correctly detected
     */
    @Test
    void extractPaymentInfo_withBankTransfer_shouldDetectBankTransfer() {
        // Given: WhatsApp text with bank transfer keyword
        String text = "Order details:\n" +
                      "1 x Product C\n" +
                      "Payment mode: Bank Transfer";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify BANK_TRANSFER is detected
        assertThat(payment.getPaymentMode()).isEqualTo("BANK_TRANSFER");
    }

    /**
     * Test Requirement 3.3: Bank Transfer detection with various keywords
     */
    @Test
    void extractPaymentInfo_withBankTransferVariations_shouldDetectBankTransfer() {
        assertThat(parser.extractPaymentInfo("bank transfer").getPaymentMode()).isEqualTo("BANK_TRANSFER");
        assertThat(parser.extractPaymentInfo("NEFT payment").getPaymentMode()).isEqualTo("BANK_TRANSFER");
        assertThat(parser.extractPaymentInfo("RTGS").getPaymentMode()).isEqualTo("BANK_TRANSFER");
        assertThat(parser.extractPaymentInfo("IMPS transfer").getPaymentMode()).isEqualTo("BANK_TRANSFER");
        assertThat(parser.extractPaymentInfo("netbanking").getPaymentMode()).isEqualTo("BANK_TRANSFER");
        assertThat(parser.extractPaymentInfo("Net Banking").getPaymentMode()).isEqualTo("BANK_TRANSFER");
    }

    /**
     * Test Requirement 3.3: Online payment detection
     * Verifies that online payment keywords are correctly detected
     */
    @Test
    void extractPaymentInfo_withOnline_shouldDetectOnline() {
        // Given: WhatsApp text with online keyword
        String text = "Order:\n" +
                      "2 x Product D\n" +
                      "Payment: Online";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify ONLINE is detected
        assertThat(payment.getPaymentMode()).isEqualTo("ONLINE");
    }

    /**
     * Test Requirement 3.3: Online payment detection with variations
     */
    @Test
    void extractPaymentInfo_withOnlineVariations_shouldDetectOnline() {
        assertThat(parser.extractPaymentInfo("online payment").getPaymentMode()).isEqualTo("ONLINE");
        assertThat(parser.extractPaymentInfo("Digital payment").getPaymentMode()).isEqualTo("ONLINE");
        assertThat(parser.extractPaymentInfo("card payment").getPaymentMode()).isEqualTo("ONLINE");
    }

    /**
     * Test Requirement 3.3: Credit payment detection
     * Verifies that credit payment keywords are correctly detected
     */
    @Test
    void extractPaymentInfo_withCredit_shouldDetectCredit() {
        // Given: WhatsApp text with credit keyword
        String text = "Order:\n" +
                      "5 x Product E\n" +
                      "Payment: On Credit";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify CREDIT is detected
        assertThat(payment.getPaymentMode()).isEqualTo("CREDIT");
    }

    /**
     * Test Requirement 3.3: Credit payment detection with variations
     */
    @Test
    void extractPaymentInfo_withCreditVariations_shouldDetectCredit() {
        assertThat(parser.extractPaymentInfo("credit").getPaymentMode()).isEqualTo("CREDIT");
        assertThat(parser.extractPaymentInfo("on credit").getPaymentMode()).isEqualTo("CREDIT");
        assertThat(parser.extractPaymentInfo("pay later").getPaymentMode()).isEqualTo("CREDIT");
        assertThat(parser.extractPaymentInfo("payment due").getPaymentMode()).isEqualTo("CREDIT");
    }

    /**
     * Test Requirement 3.3: Default to COD
     * Verifies that payment mode defaults to COD when no keyword is detected
     */
    @Test
    void extractPaymentInfo_withNoPaymentKeyword_shouldDefaultToCOD() {
        // Given: WhatsApp text without payment information
        String text = "Customer Name: John Doe\n" +
                      "2 x Product A\n" +
                      "Address: 123 Main Street";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify defaults to COD
        assertThat(payment.getPaymentMode()).isEqualTo("COD");
        assertThat(payment.getAmount()).isNull();
    }

    /**
     * Test Requirement 3.3: Default to COD with empty text
     * Verifies that payment mode defaults to COD for empty/null text
     */
    @Test
    void extractPaymentInfo_withEmptyText_shouldDefaultToCOD() {
        assertThat(parser.extractPaymentInfo(null).getPaymentMode()).isEqualTo("COD");
        assertThat(parser.extractPaymentInfo("").getPaymentMode()).isEqualTo("COD");
        assertThat(parser.extractPaymentInfo("   ").getPaymentMode()).isEqualTo("COD");
    }

    /**
     * Test Requirement 3.3: Payment amount extraction
     * Verifies that payment amounts are correctly extracted
     */
    @Test
    void extractPaymentInfo_withAmount_shouldExtractAmount() {
        // Given: WhatsApp text with payment amount
        String text = "Order:\n" +
                      "2 x Product A\n" +
                      "Amount: 1500\n" +
                      "Payment: UPI";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify amount is extracted
        assertThat(payment.getPaymentMode()).isEqualTo("UPI");
        assertThat(payment.getAmount()).isEqualTo("1500");
    }

    /**
     * Test Requirement 3.3: Payment amount extraction with variations
     */
    @Test
    void extractPaymentInfo_withAmountVariations_shouldExtractAmount() {
        assertThat(parser.extractPaymentInfo("Amount: 2500").getAmount()).isEqualTo("2500");
        assertThat(parser.extractPaymentInfo("Payment: Rs. 1200").getAmount()).isEqualTo("1200");
        assertThat(parser.extractPaymentInfo("Paid: 3500.50").getAmount()).isEqualTo("3500.50");
        assertThat(parser.extractPaymentInfo("Rupees 4500").getAmount()).isEqualTo("4500");
        assertThat(parser.extractPaymentInfo("₹ 5000").getAmount()).isEqualTo("5000");
    }

    /**
     * Test Requirement 3.3: Payment amount with commas
     * Verifies that amounts with thousand separators are handled correctly
     */
    @Test
    void extractPaymentInfo_withCommasInAmount_shouldExtractCleanAmount() {
        // Given: WhatsApp text with comma-separated amount
        String text = "Total Amount: Rs. 12,500\n" +
                      "Payment: Bank Transfer";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify commas are removed
        assertThat(payment.getAmount()).isEqualTo("12500");
    }

    /**
     * Test Requirement 3.3: Payment amount with decimals
     * Verifies that decimal amounts are preserved
     */
    @Test
    void extractPaymentInfo_withDecimalAmount_shouldExtractWithDecimals() {
        // Given: WhatsApp text with decimal amount
        String text = "Amount: 1250.75\n" +
                      "Payment: COD";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify decimal is preserved
        assertThat(payment.getAmount()).isEqualTo("1250.75");
    }

    /**
     * Test Requirement 3.3: Priority order of payment modes
     * Verifies that when multiple keywords are present, correct priority is applied
     * Priority: UPI > BANK_TRANSFER > ONLINE > CREDIT > COD
     */
    @Test
    void extractPaymentInfo_withMultipleKeywords_shouldUsePriorityOrder() {
        // UPI takes priority over COD
        String text1 = "Payment: COD or UPI available";
        assertThat(parser.extractPaymentInfo(text1).getPaymentMode()).isEqualTo("UPI");

        // BANK_TRANSFER takes priority over ONLINE
        String text2 = "Online bank transfer";
        assertThat(parser.extractPaymentInfo(text2).getPaymentMode()).isEqualTo("BANK_TRANSFER");

        // ONLINE takes priority over CREDIT
        String text3 = "Credit card online payment";
        assertThat(parser.extractPaymentInfo(text3).getPaymentMode()).isEqualTo("ONLINE");
    }

    /**
     * Test Requirement 3.3: Case insensitivity
     * Verifies that payment detection is case-insensitive
     */
    @Test
    void extractPaymentInfo_withMixedCase_shouldDetectCorrectly() {
        assertThat(parser.extractPaymentInfo("payment: upi").getPaymentMode()).isEqualTo("UPI");
        assertThat(parser.extractPaymentInfo("Payment: Cod").getPaymentMode()).isEqualTo("COD");
        assertThat(parser.extractPaymentInfo("BANK TRANSFER").getPaymentMode()).isEqualTo("BANK_TRANSFER");
        assertThat(parser.extractPaymentInfo("Online Payment").getPaymentMode()).isEqualTo("ONLINE");
        assertThat(parser.extractPaymentInfo("ON CREDIT").getPaymentMode()).isEqualTo("CREDIT");
    }

    /**
     * Test Requirement 3.3: No amount when not present
     * Verifies that amount is null when no amount keyword is found
     */
    @Test
    void extractPaymentInfo_withNoAmount_shouldReturnNullAmount() {
        // Given: WhatsApp text without amount
        String text = "Customer: John\n" +
                      "2 x Product A\n" +
                      "Payment: UPI";

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify amount is null
        assertThat(payment.getPaymentMode()).isEqualTo("UPI");
        assertThat(payment.getAmount()).isNull();
    }

    /**
     * Test Requirement 3.3: Complete order message
     * Verifies payment extraction from a realistic WhatsApp order message
     */
    @Test
    void extractPaymentInfo_withCompleteOrderMessage_shouldExtractCorrectly() {
        // Given: Complete WhatsApp order message
        String text = """
                Name: Rajesh Kumar
                Phone: 9876543210
                Address: 123, MG Road, Bangalore, Karnataka 560001
                
                Order:
                2 x Ashwagandha Powder
                1 x Triphala Tablets
                
                Total Amount: Rs. 1,250
                Payment Mode: UPI (Google Pay)
                """;

        // When: Extract payment info
        ParsedPayment payment = parser.extractPaymentInfo(text);

        // Then: Verify correct extraction
        assertThat(payment.getPaymentMode()).isEqualTo("UPI");
        assertThat(payment.getAmount()).isEqualTo("1250");
    }
}
