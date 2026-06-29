package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.ReturnOrderRequest;
import com.ayurveda.platform.dto.response.OrderResponse;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService#returnOrder} covering order return
 * processing (Requirement 28.1-28.4).
 *
 * <p>Verifies that:
 * <ul>
 *   <li>DELIVERED and DISPATCHED orders can be returned (status -> RETURNED),
 *       restoring stock for every item and emitting an ORDER_RETURNED audit entry.</li>
 *   <li>Orders in non-returnable states (NEW/CONFIRMED/PAID/PACKED) are rejected.</li>
 *   <li>A future return date is rejected.</li>
 *   <li>The return reason and customer comments are captured in the status history.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service - Return Order Processing Tests")
class OrderServiceReturnOrderTest {

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

    private Order testOrder;
    private Customer testCustomer;
    private Product testProduct1;
    private Product testProduct2;
    private OrderItem testItem1;
    private OrderItem testItem2;

    @BeforeEach
    void setUp() {
        testCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .phone("9876543210")
                .city("Test City")
                .state("Test State")
                .build();

        testProduct1 = Product.builder()
                .id(1L)
                .sku("PROD-001")
                .name("Product 1")
                .salePrice(new BigDecimal("100.00"))
                .mrp(new BigDecimal("150.00"))
                .stockQuantity(100)
                .build();

        testProduct2 = Product.builder()
                .id(2L)
                .sku("PROD-002")
                .name("Product 2")
                .salePrice(new BigDecimal("200.00"))
                .mrp(new BigDecimal("250.00"))
                .stockQuantity(50)
                .build();

        testItem1 = OrderItem.builder()
                .id(1L)
                .product(testProduct1)
                .productNameSnapshot("Product 1")
                .skuSnapshot("PROD-001")
                .quantity(5)
                .unitPrice(new BigDecimal("100.00"))
                .mrpSnapshot(new BigDecimal("150.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("500.00"))
                .build();

        testItem2 = OrderItem.builder()
                .id(2L)
                .product(testProduct2)
                .productNameSnapshot("Product 2")
                .skuSnapshot("PROD-002")
                .quantity(3)
                .unitPrice(new BigDecimal("200.00"))
                .mrpSnapshot(new BigDecimal("250.00"))
                .discount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("600.00"))
                .build();

        testOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20260627-0001")
                .customer(testCustomer)
                .orderSource(Order.OrderSource.MANUAL)
                .status(Order.OrderStatus.DELIVERED)
                .paymentStatus(Order.PaymentStatus.PAID)
                .paymentMode(Order.PaymentMode.UPI)
                .subtotal(new BigDecimal("1100.00"))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .shippingCharge(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("1100.00"))
                .orderDate(LocalDate.now().minusDays(3))
                .deliveredAt(LocalDateTime.now().minusDays(1))
                .items(new ArrayList<>(List.of(testItem1, testItem2)))
                .statusHistory(new ArrayList<>())
                .paymentRecords(new ArrayList<>())
                .build();

        testItem1.setOrder(testOrder);
        testItem2.setOrder(testOrder);
    }

    private ReturnOrderRequest buildReturnRequest() {
        ReturnOrderRequest request = new ReturnOrderRequest();
        request.setReturnReason("Damaged on arrival");
        request.setReturnDate(LocalDate.now());
        request.setCustomerComments("Box was crushed");
        request.setRefundRequested(true);
        request.setRefundMode("UPI");
        return request;
    }

    @Test
    @DisplayName("Return from DELIVERED - status becomes RETURNED, stock restored, audit recorded")
    void returnOrder_fromDelivered_succeeds() {
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.returnOrder(1L, buildReturnRequest(), USER_ID);

        // Status transitioned to RETURNED
        assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);
        assertThat(testOrder.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);

        // Stock restored for both items (Req 28.2) using RETURN operation
        verify(productManagementService).updateStock(
                eq(1L), eq(5), eq(StockHistory.StockOperation.RETURN),
                eq("ORDER"), eq(1L), anyString(), eq(USER_ID));
        verify(productManagementService).updateStock(
                eq(2L), eq(3), eq(StockHistory.StockOperation.RETURN),
                eq("ORDER"), eq(1L), anyString(), eq(USER_ID));

        // Audit entry recorded (Req 28.x audit trail)
        verify(auditLogService).record(eq(USER_ID), eq(AuditLogService.ORDER_RETURNED), anyMap());
    }

    @Test
    @DisplayName("Return from DISPATCHED - status becomes RETURNED, stock restored, audit recorded")
    void returnOrder_fromDispatched_succeeds() {
        testOrder.setStatus(Order.OrderStatus.DISPATCHED);
        testOrder.setDispatchedAt(LocalDateTime.now().minusDays(1));
        testOrder.setDeliveredAt(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.returnOrder(1L, buildReturnRequest(), USER_ID);

        assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);

        // Stock restored for both items
        verify(productManagementService, times(2)).updateStock(
                anyLong(), anyInt(), eq(StockHistory.StockOperation.RETURN),
                eq("ORDER"), eq(1L), anyString(), eq(USER_ID));

        verify(auditLogService).record(eq(USER_ID), eq(AuditLogService.ORDER_RETURNED), anyMap());
    }

    @Test
    @DisplayName("Return from NEW is rejected with IllegalArgumentException")
    void returnOrder_fromNew_isRejected() {
        testOrder.setStatus(Order.OrderStatus.NEW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.returnOrder(1L, buildReturnRequest(), USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DELIVERED or DISPATCHED");

        verifyNoInteractions(productManagementService);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Return from CONFIRMED is rejected with IllegalArgumentException")
    void returnOrder_fromConfirmed_isRejected() {
        testOrder.setStatus(Order.OrderStatus.CONFIRMED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.returnOrder(1L, buildReturnRequest(), USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Return from PAID is rejected with IllegalArgumentException")
    void returnOrder_fromPaid_isRejected() {
        testOrder.setStatus(Order.OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.returnOrder(1L, buildReturnRequest(), USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Return from PACKED is rejected with IllegalArgumentException")
    void returnOrder_fromPacked_isRejected() {
        testOrder.setStatus(Order.OrderStatus.PACKED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        assertThatThrownBy(() -> orderService.returnOrder(1L, buildReturnRequest(), USER_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(productManagementService);
    }

    @Test
    @DisplayName("Return with a future return date is rejected")
    void returnOrder_withFutureReturnDate_isRejected() {
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        ReturnOrderRequest request = buildReturnRequest();
        request.setReturnDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> orderService.returnOrder(1L, request, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");

        verifyNoInteractions(productManagementService);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Return reason and customer comments are captured in status history")
    void returnOrder_capturesReasonAndComments() {
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnOrderRequest request = buildReturnRequest();
        request.setReturnReason("Wrong item delivered");
        request.setCustomerComments("Expected green tea, got black tea");

        orderService.returnOrder(1L, request, USER_ID);

        assertThat(testOrder.getStatusHistory()).isNotEmpty();
        OrderStatusHistory history = testOrder.getStatusHistory()
                .get(testOrder.getStatusHistory().size() - 1);
        assertThat(history.getToStatus()).isEqualTo("RETURNED");
        assertThat(history.getFromStatus()).isEqualTo("DELIVERED");
        assertThat(history.getNotes())
                .contains("Wrong item delivered")
                .contains("Expected green tea, got black tea");
    }

    @Test
    @DisplayName("Return with no return date still succeeds (date is optional at service level)")
    void returnOrder_withNullReturnDate_succeeds() {
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnOrderRequest request = buildReturnRequest();
        request.setReturnDate(null);

        OrderResponse response = orderService.returnOrder(1L, request, USER_ID);

        assertThat(response.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);
    }
}
