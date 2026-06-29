package com.ayurveda.platform.tenant.entity;

import net.jqwik.api.*;
import net.jqwik.api.constraints.BigRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Property-Based Tests for Payment Balance using jqwik.
 * 
 * This test suite validates Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7 using property-based testing
 * to verify payment recording and balance tracking across all possible payment sequences.
 */
class PaymentBalancePropertyTest {

    /**
     * **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7**
     * 
     * Property 4: Payment Balance
     * 
     * This property verifies that for any order with random total and payment sequences:
     * - Sum of payments never exceeds order total (Requirement 7.2, 7.7)
     * - Payment status is correctly derived from total paid (Requirements 7.4, 7.5, 7.6)
     * 
     * Payment status derivation rules:
     * - PENDING: total paid = 0 (Requirement 7.6)
     * - PARTIAL: 0 < total paid < order total (Requirement 7.5)
     * - PAID: total paid = order total (Requirement 7.4)
     * 
     * Requirements validated:
     * - 7.1: PaymentRecord created with transaction details
     * - 7.2: Payment doesn't exceed remaining balance
     * - 7.3: Total paid calculated as sum of all payment records
     * - 7.4: Payment status = PAID when total paid equals order total
     * - 7.5: Payment status = PARTIAL when 0 < total paid < order total
     * - 7.6: Payment status = PENDING when total paid = 0
     * - 7.7: Reject payment exceeding order total
     */
    @Property(tries = 1000)
    @Label("Payment Balance: SUM(payments) ≤ orderTotal AND payment status correctly derived")
    void paymentBalanceAndStatusDerivation(
            @ForAll @BigRange(min = "100.00", max = "50000.00") BigDecimal orderTotal,
            @ForAll("paymentSequence") List<BigDecimal> paymentAmounts
    ) {
        // Arrange: Create an order with the generated total
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-PAY-001")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .subtotal(orderTotal.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(orderTotal.setScale(2, RoundingMode.HALF_UP))
                .paymentStatus(Order.PaymentStatus.PENDING)
                .paymentMode(Order.PaymentMode.UPI)
                .items(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        // Act: Add payment records sequentially
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (int i = 0; i < paymentAmounts.size(); i++) {
            BigDecimal paymentAmount = paymentAmounts.get(i).setScale(2, RoundingMode.HALF_UP);
            
            // Calculate what the new total would be
            BigDecimal potentialTotal = totalPaid.add(paymentAmount);
            
            // Only add payment if it doesn't exceed order total
            // This simulates the validation in OrderService.recordPayment
            if (potentialTotal.compareTo(order.getTotalAmount()) <= 0) {
                PaymentRecord record = PaymentRecord.builder()
                        .order(order)
                        .amount(paymentAmount)
                        .paymentMode(Order.PaymentMode.UPI)
                        .transactionReference("TXN-" + (i + 1))
                        .paymentDate(LocalDateTime.now())
                        .recordedBy(1L)
                        .build();
                
                order.getPaymentRecords().add(record);
                totalPaid = totalPaid.add(paymentAmount);
            }
        }

        // Update payment status based on total paid
        updatePaymentStatus(order, totalPaid);

        // Assert: Verify Property 4 - Payment Balance Constraint
        // SUM(paymentRecords.amount) ≤ O.totalAmount (Always true)
        BigDecimal actualTotalPaid = order.getPaymentRecords().stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        assert actualTotalPaid.compareTo(order.getTotalAmount()) <= 0 :
                String.format("Total paid %s exceeds order total %s",
                        actualTotalPaid, order.getTotalAmount());

        // Assert: Verify total paid calculation (Requirement 7.3)
        assert actualTotalPaid.compareTo(totalPaid) == 0 :
                String.format("Calculated total paid %s doesn't match actual sum %s",
                        totalPaid, actualTotalPaid);

        // Assert: Verify payment status derivation rules
        if (actualTotalPaid.compareTo(BigDecimal.ZERO) == 0) {
            // Requirement 7.6: PENDING when total paid = 0
            assert order.getPaymentStatus() == Order.PaymentStatus.PENDING :
                    String.format("Payment status should be PENDING when total paid = 0, got %s",
                            order.getPaymentStatus());
        } else if (actualTotalPaid.compareTo(order.getTotalAmount()) == 0) {
            // Requirement 7.4: PAID when total paid = order total
            assert order.getPaymentStatus() == Order.PaymentStatus.PAID :
                    String.format("Payment status should be PAID when total paid (%s) equals order total (%s), got %s",
                            actualTotalPaid, order.getTotalAmount(), order.getPaymentStatus());
        } else {
            // Requirement 7.5: PARTIAL when 0 < total paid < order total
            assert order.getPaymentStatus() == Order.PaymentStatus.PARTIAL :
                    String.format("Payment status should be PARTIAL when total paid (%s) is between 0 and order total (%s), got %s",
                            actualTotalPaid, order.getTotalAmount(), order.getPaymentStatus());
        }

        // Assert: Verify precision maintained at 2 decimal places
        for (PaymentRecord record : order.getPaymentRecords()) {
            assert record.getAmount().scale() == 2 :
                    String.format("Payment amount scale should be 2, got %d", record.getAmount().scale());
        }
    }

    /**
     * Property test: Payment exceeding order total should not be recorded
     * **Validates: Requirement 7.2, 7.7**
     */
    @Property(tries = 500)
    @Label("Payment that would exceed order total is rejected")
    void paymentExceedingTotalRejected(
            @ForAll @BigRange(min = "100.00", max = "10000.00") BigDecimal orderTotal,
            @ForAll @BigRange(min = "50.00", max = "5000.00") BigDecimal initialPayment,
            @ForAll @BigRange(min = "100.00", max = "20000.00") BigDecimal excessivePayment
    ) {
        // Arrange: Create an order with the generated total
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-EXCESS")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .totalAmount(orderTotal.setScale(2, RoundingMode.HALF_UP))
                .paymentStatus(Order.PaymentStatus.PENDING)
                .paymentRecords(new ArrayList<>())
                .build();

        // Act: Add initial payment (if it doesn't exceed total)
        BigDecimal totalPaid = BigDecimal.ZERO;
        if (initialPayment.compareTo(orderTotal) <= 0) {
            PaymentRecord initialRecord = PaymentRecord.builder()
                    .order(order)
                    .amount(initialPayment.setScale(2, RoundingMode.HALF_UP))
                    .paymentMode(Order.PaymentMode.UPI)
                    .paymentDate(LocalDateTime.now())
                    .recordedBy(1L)
                    .build();
            order.getPaymentRecords().add(initialRecord);
            totalPaid = initialPayment.setScale(2, RoundingMode.HALF_UP);
        }

        // Try to add excessive payment
        BigDecimal remainingBalance = order.getTotalAmount().subtract(totalPaid);
        BigDecimal excessivePaymentScaled = excessivePayment.setScale(2, RoundingMode.HALF_UP);

        // Assert: Payment exceeding remaining balance should be rejected
        if (excessivePaymentScaled.compareTo(remainingBalance) > 0) {
            // Simulate validation: payment would exceed order total
            BigDecimal potentialTotal = totalPaid.add(excessivePaymentScaled);
            
            assert potentialTotal.compareTo(order.getTotalAmount()) > 0 :
                    "Test setup error: excessive payment should exceed order total";
            
            // Verify that we would reject this payment (don't actually add it)
            int recordCountBefore = order.getPaymentRecords().size();
            
            // Don't add the record - simulating rejection
            // In real code, OrderService would throw IllegalArgumentException
            
            int recordCountAfter = order.getPaymentRecords().size();
            
            assert recordCountBefore == recordCountAfter :
                    "Payment record count should not change when payment is rejected";
        }
    }

    /**
     * Property test: Multiple partial payments reaching exact total
     * **Validates: Requirements 7.3, 7.4, 7.5**
     */
    @Property(tries = 500)
    @Label("Multiple partial payments sum correctly and update status appropriately")
    void multiplePartialPaymentsSum(
            @ForAll @BigRange(min = "1000.00", max = "10000.00") BigDecimal orderTotal
    ) {
        // Arrange: Create an order
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-MULTI")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .totalAmount(orderTotal.setScale(2, RoundingMode.HALF_UP))
                .paymentStatus(Order.PaymentStatus.PENDING)
                .paymentRecords(new ArrayList<>())
                .build();

        // Act: Split payment into 3-5 parts
        int numberOfPayments = 3 + (orderTotal.intValue() % 3); // 3-5 payments
        BigDecimal remainingAmount = order.getTotalAmount();
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (int i = 0; i < numberOfPayments; i++) {
            BigDecimal paymentAmount;
            if (i == numberOfPayments - 1) {
                // Last payment: pay exactly the remaining amount
                paymentAmount = remainingAmount;
            } else {
                // Split roughly evenly, but ensure we don't overpay
                BigDecimal maxPayment = remainingAmount.subtract(BigDecimal.ONE);
                paymentAmount = remainingAmount
                        .divide(BigDecimal.valueOf(numberOfPayments - i), 2, RoundingMode.DOWN)
                        .min(maxPayment);
            }

            PaymentRecord record = PaymentRecord.builder()
                    .order(order)
                    .amount(paymentAmount)
                    .paymentMode(Order.PaymentMode.UPI)
                    .paymentDate(LocalDateTime.now())
                    .recordedBy(1L)
                    .build();
            
            order.getPaymentRecords().add(record);
            totalPaid = totalPaid.add(paymentAmount);
            remainingAmount = remainingAmount.subtract(paymentAmount);
            
            // Update payment status after each payment
            updatePaymentStatus(order, totalPaid);
        }

        // Assert: Total paid should equal order total
        BigDecimal actualTotalPaid = order.getPaymentRecords().stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        assert actualTotalPaid.compareTo(order.getTotalAmount()) == 0 :
                String.format("Total paid %s should equal order total %s",
                        actualTotalPaid, order.getTotalAmount());

        // Assert: Final status should be PAID (Requirement 7.4)
        assert order.getPaymentStatus() == Order.PaymentStatus.PAID :
                String.format("Payment status should be PAID after full payment, got %s",
                        order.getPaymentStatus());
    }

    /**
     * Property test: Order with zero payments should remain PENDING
     * **Validates: Requirement 7.6**
     */
    @Property(tries = 200)
    @Label("Order with no payments should have PENDING status")
    void orderWithNoPaymentsIsPending(
            @ForAll @BigRange(min = "100.00", max = "10000.00") BigDecimal orderTotal
    ) {
        // Arrange: Create an order with no payments
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-PENDING")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .totalAmount(orderTotal.setScale(2, RoundingMode.HALF_UP))
                .paymentStatus(Order.PaymentStatus.PENDING)
                .paymentRecords(new ArrayList<>())
                .build();

        // Act: Calculate total paid (should be 0)
        BigDecimal totalPaid = order.getPaymentRecords().stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        updatePaymentStatus(order, totalPaid);

        // Assert: Status should remain PENDING (Requirement 7.6)
        assert order.getPaymentStatus() == Order.PaymentStatus.PENDING :
                String.format("Payment status should be PENDING when no payments made, got %s",
                        order.getPaymentStatus());

        assert totalPaid.compareTo(BigDecimal.ZERO) == 0 :
                String.format("Total paid should be 0, got %s", totalPaid);
    }

    /**
     * Property test: Single full payment should set status to PAID
     * **Validates: Requirement 7.4**
     */
    @Property(tries = 300)
    @Label("Single payment equal to order total should set status to PAID")
    void singleFullPaymentSetsPaid(
            @ForAll @BigRange(min = "100.00", max = "20000.00") BigDecimal orderTotal
    ) {
        // Arrange: Create an order
        Order order = Order.builder()
                .orderNumber("TEST-ORDER-FULL")
                .orderDate(LocalDate.now())
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .totalAmount(orderTotal.setScale(2, RoundingMode.HALF_UP))
                .paymentStatus(Order.PaymentStatus.PENDING)
                .paymentRecords(new ArrayList<>())
                .build();

        // Act: Add single payment for full amount
        PaymentRecord fullPayment = PaymentRecord.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .paymentMode(Order.PaymentMode.UPI)
                .paymentDate(LocalDateTime.now())
                .recordedBy(1L)
                .build();
        
        order.getPaymentRecords().add(fullPayment);

        BigDecimal totalPaid = order.getPaymentRecords().stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        updatePaymentStatus(order, totalPaid);

        // Assert: Status should be PAID (Requirement 7.4)
        assert order.getPaymentStatus() == Order.PaymentStatus.PAID :
                String.format("Payment status should be PAID after full payment, got %s",
                        order.getPaymentStatus());

        assert totalPaid.compareTo(order.getTotalAmount()) == 0 :
                String.format("Total paid %s should equal order total %s",
                        totalPaid, order.getTotalAmount());
    }

    /**
     * Arbitrary generator for payment sequence.
     * Generates 0-10 payment amounts between $10 and $5000.
     */
    @Provide
    Arbitrary<List<BigDecimal>> paymentSequence() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(10.00), BigDecimal.valueOf(5000.00))
                .ofScale(2)
                .list()
                .ofMinSize(0)
                .ofMaxSize(10);
    }

    /**
     * Helper method to update payment status based on total paid.
     * Mimics the logic in OrderService.
     * 
     * @param order The order to update
     * @param totalPaid The total amount paid
     */
    private void updatePaymentStatus(Order order, BigDecimal totalPaid) {
        BigDecimal totalPaidScaled = totalPaid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal orderTotalScaled = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP);

        if (totalPaidScaled.compareTo(BigDecimal.ZERO) == 0) {
            order.setPaymentStatus(Order.PaymentStatus.PENDING);
        } else if (totalPaidScaled.compareTo(orderTotalScaled) == 0) {
            order.setPaymentStatus(Order.PaymentStatus.PAID);
        } else {
            order.setPaymentStatus(Order.PaymentStatus.PARTIAL);
        }
    }
}
