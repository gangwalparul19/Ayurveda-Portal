package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for payment recording in OrderService.
 * Tests Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7
 */
@ExtendWith(MockitoExtension.class)
class OrderServicePaymentRecordingTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SalespersonRepository salespersonRepository;

    @Mock
    private OrderNumberGenerator orderNumberGenerator;

    @Mock
    private ProductManagementService productManagementService;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;
    private Customer testCustomer;
    private Long testUserId = 100L;

    @BeforeEach
    void setUp() {
        // Setup test customer
        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .city("Mumbai")
                .state("Maharashtra")
                .build();

        // Setup test order with total amount of 1000.00
        testOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20240101-0001")
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .paymentMode(Order.PaymentMode.UPI)
                .subtotal(BigDecimal.valueOf(1000.00))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(BigDecimal.valueOf(1000.00))
                .orderDate(LocalDate.now())
                .items(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();
    }

    /**
     * Test successful payment recording.
     * Requirement 7.1: Create PaymentRecord entity with transaction details
     */
    @Test
    void testRecordPayment_Successful() {
        // Given
        BigDecimal paymentAmount = BigDecimal.valueOf(500.00);
        Order.PaymentMode paymentMode = Order.PaymentMode.UPI;
        String transactionRef = "TXN123456";
        String notes = "First installment";

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> {
                    PaymentRecord record = invocation.getArgument(0);
                    record.setId(1L);
                    return record;
                });
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        Order result = orderService.recordPayment(1L, paymentAmount, paymentMode, 
                transactionRef, notes, testUserId);

        // Then
        assertNotNull(result);
        verify(paymentRecordRepository).save(argThat(record ->
                record.getAmount().compareTo(paymentAmount) == 0 &&
                record.getPaymentMode() == paymentMode &&
                record.getTransactionReference().equals(transactionRef) &&
                record.getNotes().equals(notes) &&
                record.getRecordedBy().equals(testUserId)
        ));
        verify(orderRepository).save(any(Order.class));
    }

    /**
     * Test payment amount validation.
     * Requirement 7.2: Validate payment doesn't exceed remaining balance
     * Requirement 7.7: Reject payment exceeding order total
     */
    @Test
    void testRecordPayment_ExceedsRemainingBalance() {
        // Given
        BigDecimal currentPaid = BigDecimal.valueOf(600.00);
        BigDecimal paymentAmount = BigDecimal.valueOf(500.00); // Would total 1100, exceeds 1000

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(currentPaid);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.recordPayment(1L, paymentAmount, Order.PaymentMode.UPI, 
                        null, null, testUserId)
        );

        assertTrue(exception.getMessage().contains("exceeds remaining balance"));
        verify(paymentRecordRepository, never()).save(any(PaymentRecord.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    /**
     * Test payment status update to PAID.
     * Requirement 7.4: When total paid equals order total, set status to PAID
     */
    @Test
    void testRecordPayment_UpdatesStatusToPaid() {
        // Given
        BigDecimal paymentAmount = BigDecimal.valueOf(1000.00);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            assertEquals(Order.PaymentStatus.PAID, order.getPaymentStatus());
            return order;
        });

        // When
        Order result = orderService.recordPayment(1L, paymentAmount, Order.PaymentMode.UPI,
                null, null, testUserId);

        // Then
        assertNotNull(result);
        verify(orderRepository).save(argThat(order ->
                order.getPaymentStatus() == Order.PaymentStatus.PAID
        ));
    }

    /**
     * Test payment status update to PARTIAL.
     * Requirement 7.5: When 0 < total paid < order total, set status to PARTIAL
     */
    @Test
    void testRecordPayment_UpdatesStatusToPartial() {
        // Given
        BigDecimal paymentAmount = BigDecimal.valueOf(500.00);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            assertEquals(Order.PaymentStatus.PARTIAL, order.getPaymentStatus());
            return order;
        });

        // When
        Order result = orderService.recordPayment(1L, paymentAmount, Order.PaymentMode.UPI,
                null, null, testUserId);

        // Then
        assertNotNull(result);
        verify(orderRepository).save(argThat(order ->
                order.getPaymentStatus() == Order.PaymentStatus.PARTIAL
        ));
    }

    /**
     * Test multiple partial payments reaching full payment.
     * Requirement 7.3: Calculate total paid amount
     * Requirement 7.4: When total equals order total, set status to PAID
     */
    @Test
    void testRecordPayment_MultiplePartialPayments() {
        // Given - First payment of 300
        BigDecimal firstPayment = BigDecimal.valueOf(300.00);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(BigDecimal.ZERO);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // First payment
        orderService.recordPayment(1L, firstPayment, Order.PaymentMode.UPI, 
                null, null, testUserId);

        // Given - Second payment of 300
        BigDecimal secondPayment = BigDecimal.valueOf(300.00);
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(BigDecimal.valueOf(300.00));

        // Second payment
        orderService.recordPayment(1L, secondPayment, Order.PaymentMode.UPI,
                null, null, testUserId);

        // Given - Final payment of 400 to complete
        BigDecimal finalPayment = BigDecimal.valueOf(400.00);
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(BigDecimal.valueOf(600.00));

        // Final payment
        Order result = orderService.recordPayment(1L, finalPayment, Order.PaymentMode.UPI,
                null, null, testUserId);

        // Then
        assertNotNull(result);
        verify(paymentRecordRepository, times(3)).save(any(PaymentRecord.class));
        verify(orderRepository, times(3)).save(any(Order.class));
    }

    /**
     * Test negative payment amount validation.
     */
    @Test
    void testRecordPayment_NegativeAmount() {
        // Given
        BigDecimal negativeAmount = BigDecimal.valueOf(-100.00);

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.recordPayment(1L, negativeAmount, Order.PaymentMode.UPI,
                        null, null, testUserId)
        );

        assertTrue(exception.getMessage().contains("must be positive"));
        verify(orderRepository, never()).findById(anyLong());
    }

    /**
     * Test zero payment amount validation.
     */
    @Test
    void testRecordPayment_ZeroAmount() {
        // Given
        BigDecimal zeroAmount = BigDecimal.ZERO;

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.recordPayment(1L, zeroAmount, Order.PaymentMode.UPI,
                        null, null, testUserId)
        );

        assertTrue(exception.getMessage().contains("must be positive"));
        verify(orderRepository, never()).findById(anyLong());
    }

    /**
     * Test null payment amount validation.
     */
    @Test
    void testRecordPayment_NullAmount() {
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.recordPayment(1L, null, Order.PaymentMode.UPI,
                        null, null, testUserId)
        );

        assertTrue(exception.getMessage().contains("must be positive"));
        verify(orderRepository, never()).findById(anyLong());
    }

    /**
     * Test order not found scenario.
     */
    @Test
    void testRecordPayment_OrderNotFound() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                orderService.recordPayment(999L, BigDecimal.valueOf(100.00), 
                        Order.PaymentMode.UPI, null, null, testUserId)
        );

        verify(paymentRecordRepository, never()).save(any(PaymentRecord.class));
    }

    /**
     * Test exact payment to complete order.
     * Requirement 7.2: Payment should not exceed remaining balance
     * Requirement 7.4: Full payment sets status to PAID
     */
    @Test
    void testRecordPayment_ExactRemainingBalance() {
        // Given
        BigDecimal currentPaid = BigDecimal.valueOf(700.00);
        BigDecimal remainingBalance = BigDecimal.valueOf(300.00);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L))
                .thenReturn(currentPaid);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            assertEquals(Order.PaymentStatus.PAID, order.getPaymentStatus());
            return order;
        });

        // When
        Order result = orderService.recordPayment(1L, remainingBalance, Order.PaymentMode.BANK_TRANSFER,
                "FINAL-TXN", "Final payment", testUserId);

        // Then
        assertNotNull(result);
        verify(orderRepository).save(argThat(order ->
                order.getPaymentStatus() == Order.PaymentStatus.PAID
        ));
    }
}
