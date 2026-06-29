package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.ManualOrderRequest;
import com.ayurveda.platform.master.service.AuditLogService;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying that {@link OrderService} triggers audit logging for
 * significant order and payment events with the correct action, user ID, and
 * contextual details (Requirement 32.3, 32.4).
 *
 * <p>Uses Mockito to verify the {@code auditLogService.record(...)} interactions.
 * The {@code AuditLogService} mock is injected so audit calls are observable
 * (otherwise the service's best-effort {@code safeAudit} swallows them).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Audit Logging Tests")
class OrderServiceAuditLoggingTest {

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
    private com.ayurveda.platform.util.WhatsAppTextParser whatsAppParser;
    @Mock
    private CustomerService customerService;
    @Mock
    private com.ayurveda.platform.master.service.ConfigurationService configurationService;
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    @InjectMocks
    private OrderService orderService;

    private Customer testCustomer;
    private Product testProduct;
    private final Long testUserId = 100L;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .city("Mumbai")
                .state("Maharashtra")
                .build();

        testProduct = Product.builder()
                .id(1L)
                .sku("PROD001")
                .name("Ashwagandha Capsules")
                .salePrice(BigDecimal.valueOf(500.00))
                .mrp(BigDecimal.valueOf(600.00))
                .stockQuantity(100)
                .build();
    }

    private Order baseOrder(Order.OrderStatus status) {
        return Order.builder()
                .id(1L)
                .orderNumber("ORD-20240101-0001")
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(status)
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureAuditDetails(String expectedAction, Long expectedUserId) {
        ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(eq(expectedUserId), eq(expectedAction), details.capture());
        return details.getValue();
    }

    @Test
    @DisplayName("Manual order creation records ORDER_CREATED with user ID and details")
    void createManualOrder_recordsOrderCreatedAudit() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-20240627-0001");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(7L);
            return order;
        });

        ManualOrderRequest request = ManualOrderRequest.builder()
                .customerId(1L)
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .items(Arrays.asList(
                        ManualOrderRequest.OrderItemRequest.builder()
                                .productId(1L)
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(500.00))
                                .build()))
                .build();

        orderService.createManualOrder(request, testUserId);

        Map<String, Object> details = captureAuditDetails(AuditLogService.ORDER_CREATED, testUserId);
        assertThat(details).containsEntry("orderNumber", "ORD-20240627-0001");
        assertThat(details).containsKey("orderId");
        assertThat(details).containsKey("totalAmount");
    }

    @Test
    @DisplayName("Recording a payment records PAYMENT_RECORDED with user ID and details")
    void recordPayment_recordsPaymentAudit() {
        Order order = baseOrder(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRecordRepository.calculateTotalPaidForOrder(1L)).thenReturn(BigDecimal.ZERO);
        when(paymentRecordRepository.save(any(PaymentRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.recordPayment(1L, BigDecimal.valueOf(500.00), Order.PaymentMode.UPI,
                "TXN-1", "first", testUserId);

        Map<String, Object> details = captureAuditDetails(AuditLogService.PAYMENT_RECORDED, testUserId);
        assertThat(details).containsEntry("orderNumber", "ORD-20240101-0001");
        assertThat(details).containsEntry("paymentMode", "UPI");
        assertThat(details).containsEntry("transactionReference", "TXN-1");
        assertThat(details).containsKey("amount");
    }

    @Test
    @DisplayName("Forward status change records ORDER_STATUS_CHANGED")
    void updateOrderStatus_recordsStatusChangedAudit() {
        Order order = baseOrder(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderStatus(1L, Order.OrderStatus.CONFIRMED, testUserId, "confirming");

        Map<String, Object> details =
                captureAuditDetails(AuditLogService.ORDER_STATUS_CHANGED, testUserId);
        assertThat(details).containsEntry("fromStatus", "NEW");
        assertThat(details).containsEntry("toStatus", "CONFIRMED");
    }

    @Test
    @DisplayName("Cancelling an order records ORDER_CANCELLED")
    void updateOrderStatus_recordsCancelledAudit() {
        Order order = baseOrder(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderStatus(1L, Order.OrderStatus.CANCELLED, testUserId, "customer request");

        Map<String, Object> details = captureAuditDetails(AuditLogService.ORDER_CANCELLED, testUserId);
        assertThat(details).containsEntry("toStatus", "CANCELLED");
        assertThat(details).containsEntry("notes", "customer request");
    }

    @Test
    @DisplayName("Returning a delivered order records ORDER_RETURNED")
    void updateOrderStatus_recordsReturnedAudit() {
        Order order = baseOrder(Order.OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderStatus(1L, Order.OrderStatus.RETURNED, testUserId, "damaged");

        Map<String, Object> details = captureAuditDetails(AuditLogService.ORDER_RETURNED, testUserId);
        assertThat(details).containsEntry("fromStatus", "DELIVERED");
        assertThat(details).containsEntry("toStatus", "RETURNED");
    }
}
