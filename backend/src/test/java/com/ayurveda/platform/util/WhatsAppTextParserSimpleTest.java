package com.ayurveda.platform.util;

import com.ayurveda.platform.util.WhatsAppTextParser.ParsedCustomer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple smoke test for WhatsAppTextParser.extractCustomerInfo
 */
class WhatsAppTextParserSimpleTest {

    @Test
    void extractCustomerInfo_basicTest() {
        // Given
        WhatsAppTextParser parser = new WhatsAppTextParser(null);
        String message = "Name: John Doe\nPhone: 9876543210\nAddress: Mumbai 400001";

        // When
        ParsedCustomer customer = parser.extractCustomerInfo(message);

        // Then
        assertThat(customer).isNotNull();
        assertThat(customer.getName()).isEqualTo("John Doe");
        assertThat(customer.getPhone()).isEqualTo("9876543210");
        assertThat(customer.getAddress()).isEqualTo("Mumbai 400001");
        assertThat(customer.getPincode()).isEqualTo("400001");
    }
}
