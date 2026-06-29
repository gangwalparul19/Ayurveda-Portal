package com.ayurveda.platform.util;

import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OrderNumberGenerator.
 * 
 * Tests Requirements:
 * - 2.1: Order number format ORD-YYYYMMDD-XXXX
 * - 2.2: Uniqueness of order numbers
 * - 2.3: Daily sequence incrementation starting from 0001
 */
@ExtendWith(MockitoExtension.class)
class OrderNumberGeneratorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ConfigurationService configurationService;

    private OrderNumberGenerator orderNumberGenerator;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @BeforeEach
    void setUp() {
        orderNumberGenerator = new OrderNumberGenerator(orderRepository, configurationService);
        
        // Default configuration: prefix "ORD"
        when(configurationService.getOrderNumberPrefix()).thenReturn("ORD");
    }

    /**
     * Test Requirement 2.1: Order number format
     * Verifies that generated order numbers follow the format PREFIX-YYYYMMDD-XXXX
     */
    @Test
    void generateOrderNumber_shouldFollowCorrectFormat() {
        // Given: No existing orders today
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When: Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Then: Verify format PREFIX-YYYYMMDD-XXXX
        String today = LocalDate.now().format(DATE_FORMAT);
        assertThat(orderNumber).matches("ORD-\\d{8}-\\d{4}");
        assertThat(orderNumber).startsWith("ORD-" + today);
        assertThat(orderNumber).endsWith("-0001"); // First order of the day
    }

    /**
     * Test Requirement 2.3: Sequence starts from 0001
     * Verifies that the first order of the day gets sequence number 0001
     */
    @Test
    void generateOrderNumber_firstOrderOfDay_shouldStartWith0001() {
        // Given: No existing orders today
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When: Generate first order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Then: Verify sequence is 0001
        assertThat(orderNumber).endsWith("-0001");
    }

    /**
     * Test Requirement 2.3: Sequential incrementation
     * Verifies that multiple order numbers increment sequentially
     */
    @Test
    void generateOrderNumber_multipleOrders_shouldIncrementSequentially() {
        // Given: No existing orders today
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When: Generate multiple order numbers
        String order1 = orderNumberGenerator.generateOrderNumber();
        String order2 = orderNumberGenerator.generateOrderNumber();
        String order3 = orderNumberGenerator.generateOrderNumber();

        // Then: Verify sequential incrementation
        assertThat(order1).endsWith("-0001");
        assertThat(order2).endsWith("-0002");
        assertThat(order3).endsWith("-0003");
    }

    /**
     * Test Requirement 2.2: Uniqueness with database query
     * Verifies that the generator queries the database to determine the next sequence
     * This ensures uniqueness even after application restart
     */
    @Test
    void generateOrderNumber_withExistingOrders_shouldContinueSequence() {
        // Given: 5 orders already exist today (last order number ends with 0005)
        String today = LocalDate.now().format(DATE_FORMAT);
        String lastOrderNumber = "ORD-" + today + "-0005";
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.of(lastOrderNumber));

        // When: Generate next order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Then: Verify sequence continues from 0006
        assertThat(orderNumber).endsWith("-0006");
    }

    /**
     * Test custom prefix from configuration
     * Verifies that the generator uses the prefix from ConfigurationService
     */
    @Test
    void generateOrderNumber_withCustomPrefix_shouldUseConfiguredPrefix() {
        // Given: Custom prefix "INV"
        when(configurationService.getOrderNumberPrefix()).thenReturn("INV");
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When: Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Then: Verify custom prefix is used
        assertThat(orderNumber).startsWith("INV-");
        assertThat(orderNumber).endsWith("-0001");
    }

    /**
     * Test sequence extraction from existing order numbers
     * Verifies that the generator can parse different order number formats
     */
    @Test
    void generateOrderNumber_withLegacyFormat_shouldExtractSequenceCorrectly() {
        // Given: Legacy 3-digit format order number
        String today = LocalDate.now().format(DATE_FORMAT);
        String lastOrderNumber = "ORD-" + today + "-042";
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.of(lastOrderNumber));

        // When: Generate next order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Then: Verify sequence continues from 43 (not 042+1=043, but 42+1=43)
        assertThat(orderNumber).endsWith("-0043");
    }

    /**
     * Test Requirement 2.2: Thread safety
     * Verifies that concurrent order number generation produces unique numbers
     */
    @Test
    void generateOrderNumber_concurrentGeneration_shouldProduceUniqueNumbers() throws InterruptedException {
        // Given: No existing orders today
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When: Generate order numbers concurrently
        int threadCount = 10;
        String[] orderNumbers = new String[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                orderNumbers[index] = orderNumberGenerator.generateOrderNumber();
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Verify all order numbers are unique
        long uniqueCount = java.util.Arrays.stream(orderNumbers).distinct().count();
        assertThat(uniqueCount).isEqualTo(threadCount);
    }

    /**
     * Test handling of malformed order numbers
     * Verifies graceful handling when existing order number is malformed
     */
    @Test
    void generateOrderNumber_withMalformedLastOrderNumber_shouldStartFromOne() {
        // Given: Malformed last order number (cannot extract sequence)
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.of("INVALID-FORMAT"));

        // When: Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Then: Verify sequence starts from 0001 (fallback behavior)
        assertThat(orderNumber).endsWith("-0001");
    }

    /**
     * Test 4-digit sequence padding
     * Verifies that sequence numbers are always padded to 4 digits
     */
    @Test
    void generateOrderNumber_shouldPadSequenceToFourDigits() {
        // Given: No existing orders today
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When: Generate order numbers
        String order1 = orderNumberGenerator.generateOrderNumber();
        String order10 = generateMultipleOrders(10);
        String order100 = generateMultipleOrders(100);
        String order1000 = generateMultipleOrders(1000);

        // Then: Verify padding
        assertThat(order1).endsWith("-0001");
        assertThat(order10).matches(".*-00\\d{2}$");
        assertThat(order100).matches(".*-0\\d{3}$");
        assertThat(order1000).matches(".*-\\d{4}$");
    }

    /**
     * Test date format in order number
     * Verifies that the date portion is in YYYYMMDD format
     */
    @Test
    void generateOrderNumber_shouldContainTodayDateInCorrectFormat() {
        // Given: No existing orders today
        when(orderRepository.findLastOrderNumberByDate(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        // When: Generate order number
        String orderNumber = orderNumberGenerator.generateOrderNumber();

        // Then: Verify date format
        String today = LocalDate.now().format(DATE_FORMAT);
        assertThat(orderNumber).contains(today);
        
        // Extract date part and verify it's exactly 8 digits
        String[] parts = orderNumber.split("-");
        assertThat(parts).hasSize(3);
        assertThat(parts[1]).hasSize(8);
        assertThat(parts[1]).matches("\\d{8}");
    }

    // Helper method to generate multiple orders and return the last one
    private String generateMultipleOrders(int count) {
        String lastOrderNumber = null;
        for (int i = 0; i < count; i++) {
            lastOrderNumber = orderNumberGenerator.generateOrderNumber();
        }
        return lastOrderNumber;
    }
}
