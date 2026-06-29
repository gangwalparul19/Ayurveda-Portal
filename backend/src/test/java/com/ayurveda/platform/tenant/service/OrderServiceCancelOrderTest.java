package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.exception.InvalidStatusTransitionException;
import com.ayurveda.platform.master.service.AuditLogService;
import com.ayurveda.platform.tenant.entity.*;
import com.ayurveda.platform.tenant.repository.*;
import com.ayurveda.platform.util.OrderNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService#cancelOrder} covering order cancellation
 * with stock restoration (Requirement 27.1-27.4).
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Orders in cancellable states (NEW/CONFIRMED/PAID) are cancelled
 *       (status -> CANCELLED), the reason is captured in the status history as
 *       "CANCELLATION: &lt;reason&gt;", and an ORDER_CANCELLED audit entry is emitted.</li>
 *   <li>A null or blank cancellation reason is rejected with
 *       {@link IllegalArgumentException} and the order is never persisted.</li>
 *   <li>Orders in terminal/invalid states (DELIVERED/CANCELLED/RETURNED) cannot
 *       be cancelled and raise {@link InvalidStatusTransitionException}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service - Cancel Order Tests")
class OrderServiceCancelOrderTest {

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

    private static final Long USER_ID = 100L;
    private static final String REASON = "Customer requested cancellation";

    private Order testOrder;
    private Customer testCustomer;
    private Product testProduct;
    private OrderItem testItem;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .phone("9876543210")
                .city("Test City")
                .state("Test State")
                .build();

        testProduct = Product.builder()
                .id(1L)
                .sku("PROD-001")
                .name("Product 1")
                .salePrice(new BigDecimal("100.00"))
                .mrp(new BigDecimal("150.00"))
                .stockQuantity(100)
                .build();

        testItem = OrderItem.builder()
                .id(1L)
                .product(testProduct)
                .productNameSnapshot("Product 1")
                .skuSnapshot("PROD-001")
                .quantity(5)
                .unitPrice(new BigDecimal("100.00"))
                .mrpSnapshot(new BigDecimal("150.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("500.00"))
                .build();

        testOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20260627-0001")
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.NEW)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .paymentMode(Order.PaymentMode.UPI)
                .subtotal(new BigDecimal("500.00"))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("500.00"))
                .orderDate(LocalDate.now())
                .items(new ArrayList<>(List.of(testItem)))
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        testItem.setOrder(testOrder);
    }

    // --- Successful cancellation from cancellable states (Req 27.1, 27.3, 27.4) ---

    @Test
    @DisplayName("Cancel from NEW - status becomes CANCELLED, reason recorded, audit emitted")
    void cancelOrder_fromNew_succeeds() {
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(1L, REASON, USER_ID);

        assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);

        // Reason captured in status history as "CANCELLATION: <reason>"
        OrderStatusHistory history = testOrder.getStatusHistory()
                .get(testOrder.getStatusHistory().size() - 1);
        assertThat(history.getFromStatus()).isEqualTo("NEW");
        assertThat(history.getToStatus()).isEqualTo("CANCELLED");
        assertThat(history.getNotes())
                .isEqualTo("CANCELLATION: " + REASON);

        // ORDER_CANCELLED audit invoked
        verify(auditLogService).record(eq(USER_ID), eq(AuditLogService.ORDER_CANCELLED), anyMap());

        // No stock movement: stock was never reduced before PACKED
        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Cancel from CONFIRMED - status becomes CANCELLED, reason recorded, audit emitted")
    void cancelOrder_fromConfirmed_succeeds() {
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(1L, REASON, USER_ID);

        assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);

        OrderStatusHistory history = testOrder.getStatusHistory()
                .get(testOrder.getStatusHistory().size() - 1);
        assertThat(history.getFromStatus()).isEqualTo("CONFIRMED");
        assertThat(history.getToStatus()).isEqualTo("CANCELLED");
        assertThat(history.getNotes()).contains(REASON);

        verify(auditLogService).record(eq(USER_ID), eq(AuditLogService.ORDER_CANCELLED), anyMap());
        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Cancel from PAID - status becomes CANCELLED, reason recorded, audit emitted")
    void cancelOrder_fromPaid_succeeds() {
        testOrder.setStatus(Order.OrderStatus.PAID);
        testOrder.setPaymentStatus(Order.PaymentStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder(1L, REASON, USER_ID);

        assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);

        OrderStatusHistory history = testOrder.getStatusHistory()
                .get(testOrder.getStatusHistory().size() - 1);
        assertThat(history.getFromStatus()).isEqualTo("PAID");
        assertThat(history.getToStatus()).isEqualTo("CANCELLED");
        assertThat(history.getNotes()).contains(REASON);

        verify(auditLogService).record(eq(USER_ID), eq(AuditLogService.ORDER_CANCELLED), anyMap());
        verifyNoInteractions(productManagementService);
    }

    // --- Mandatory reason validation (Req 27.2) ---

    @Test
    @DisplayName("Cancel with null reason is rejected and nothing is persisted")
    void cancelOrder_withNullReason_isRejected() {
        assertThatThrownBy(() -> orderService.cancelOrder(1L, null, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Cancel with blank reason is rejected and nothing is persisted")
    void cancelOrder_withBlankReason_isRejected() {
        assertThatThrownBy(() -> orderService.cancelOrder(1L, "   ", USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Cancel with empty reason is rejected and nothing is persisted")
    void cancelOrder_withEmptyReason_isRejected() {
        assertThatThrownBy(() -> orderService.cancelOrder(1L, "", USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(auditLogService);
    }

    // --- Rejection from terminal/invalid states (Req 27.1) ---

    @Test
    @DisplayName("Cancel from DELIVERED is rejected with InvalidStatusTransitionException")
    void cancelOrder_fromDelivered_isRejected() {
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, REASON, USER_ID))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Cancel from CANCELLED (already terminal) is rejected with InvalidStatusTransitionException")
    void cancelOrder_fromCancelled_isRejected() {
        testOrder.setStatus(Order.OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, REASON, USER_ID))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Cancel from RETURNED (terminal) is rejected with InvalidStatusTransitionException")
    void cancelOrder_fromReturned_isRejected() {
        testOrder.setStatus(Order.OrderStatus.RETURNED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, REASON, USER_ID))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(productManagementService);
    }
}
