package com.ayurveda.platform.integration;

import com.ayurveda.platform.config.TenantRoutingDataSource;
import com.ayurveda.platform.dto.request.ManualOrderRequest;
import com.ayurveda.platform.dto.request.WhatsAppOrderRequest;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.security.TenantContext;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.entity.Salesperson;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.repository.SalespersonRepository;
import com.ayurveda.platform.tenant.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the Ayurveda Order &amp; Dispatch Management
 * System (Task 34.1).
 *
 * <p>These tests exercise the full Spring stack (controller -&gt; service -&gt;
 * repository -&gt; H2) and stitch together the major features of the system into
 * realistic, multi-step business scenarios rather than isolated endpoint checks:
 *
 * <ul>
 *   <li><b>Complete order lifecycle</b> — NEW &rarr; CONFIRMED &rarr; PAID &rarr;
 *       PACKED &rarr; DISPATCHED &rarr; DELIVERED, including payment recording and
 *       stock movement at each relevant step.</li>
 *   <li><b>WhatsApp order processing</b> — raw message parsing through to a
 *       persisted order with an auto-created customer and matched products.</li>
 *   <li><b>Payment recording and tracking</b> — partial then full payments with
 *       payment-status transitions PENDING &rarr; PARTIAL &rarr; PAID.</li>
 *   <li><b>Stock management across the lifecycle</b> — stock reduced when an order
 *       is PACKED and restored when an order is later CANCELLED.</li>
 *   <li><b>Report generation with real data</b> — a delivered order is reflected in
 *       the daily sales report.</li>
 *   <li><b>Dispatch label generation</b> — a PDF label is produced for a PAID
 *       order.</li>
 *   <li><b>Vyapar export</b> — selected orders export to a Vyapar-compatible CSV.</li>
 *   <li><b>Multi-tenant isolation</b> — the routing decision that backs per-tenant
 *       data isolation resolves distinct datasource keys per tenant.</li>
 * </ul>
 *
 * <p>Runs against a real Spring context with an in-memory H2 database (the
 * {@code test} profile). Method-level security is satisfied with
 * {@link WithMockUser}. Each test is wrapped in a transaction and rolled back.
 *
 * <p>Validates: end-to-end coverage of Requirements 1.x, 3.x, 4.x, 5.x, 7.x, 9.x,
 * 12.x, 13.x, 16.x and 17.x.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalespersonRepository salespersonRepository;

    private Product ashwagandha;
    private Product triphala;
    private Customer customer;
    private Salesperson salesperson;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        salespersonRepository.deleteAll();

        ashwagandha = productRepository.save(Product.builder()
                .sku("ASHWA001")
                .name("Ashwagandha Capsules")
                .category("Supplements")
                .description("Premium Ashwagandha extract")
                .salePrice(BigDecimal.valueOf(500.00))
                .mrp(BigDecimal.valueOf(600.00))
                .weightGrams(BigDecimal.valueOf(250))
                .stockQuantity(100)
                .lowStockThreshold(10)
                .isActive(true)
                .build());

        triphala = productRepository.save(Product.builder()
                .sku("TRIPH001")
                .name("Triphala Powder")
                .category("Powders")
                .description("Organic Triphala powder")
                .salePrice(BigDecimal.valueOf(300.00))
                .mrp(BigDecimal.valueOf(350.00))
                .weightGrams(BigDecimal.valueOf(500))
                .stockQuantity(50)
                .lowStockThreshold(5)
                .isActive(true)
                .build());

        // Customer with a complete shipping address (required to reach DISPATCHED).
        customer = customerRepository.save(Customer.builder()
                .name("Rajesh Kumar")
                .phone("9876543210")
                .email("rajesh@example.com")
                .addressLine1("123 Main Street")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .build());

        salesperson = salespersonRepository.save(Salesperson.builder()
                .employeeCode("EMP001")
                .name("Jane Smith")
                .phone("9988776655")
                .email("jane@example.com")
                .status(Salesperson.SalespersonStatus.ACTIVE)
                .commissionRate(BigDecimal.valueOf(5.0))
                .platformUserId(1L)
                .build());
    }

    // ==================== 1. COMPLETE ORDER LIFECYCLE ====================

    /**
     * Drives a single order through the entire workflow from creation to delivery,
     * asserting state, payment status, stock movement and audit timestamps along
     * the way.
     *
     * <p>Flow: create (NEW) &rarr; CONFIRMED &rarr; PAID &rarr; record full payment
     * &rarr; PACKED (stock reduced) &rarr; DISPATCHED (requires PAID payment +
     * address) &rarr; DELIVERED, then confirms the order appears in the daily sales
     * report.
     *
     * <p>Validates Requirements 1.1, 4.1, 5.2-5.7, 5.9-5.11, 7.4, 9.2, 13.3.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Complete order lifecycle: creation -> confirmed -> paid -> packed -> dispatched -> delivered")
    void completeOrderLifecycle_creationToDelivery() throws Exception {
        int orderedQty = 3;
        int initialStock = ashwagandha.getStockQuantity();

        // --- Create order (NEW) ---
        Long orderId = createManualOrder(orderedQty);
        Order created = orderRepository.findById(orderId).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(Order.OrderStatus.NEW);
        assertThat(created.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));

        // --- NEW -> CONFIRMED ---
        transition(orderId, Order.OrderStatus.CONFIRMED, "Confirmed by staff");
        assertThat(reload(orderId).getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);

        // --- CONFIRMED -> PAID (order workflow status) ---
        transition(orderId, Order.OrderStatus.PAID, "Marked paid");
        assertThat(reload(orderId).getStatus()).isEqualTo(Order.OrderStatus.PAID);

        // --- Record full payment so the order can be dispatched (Req 7.4) ---
        orderService.recordPayment(orderId, BigDecimal.valueOf(1500.00),
                Order.PaymentMode.UPI, "TXN-LIFECYCLE-1", "Full payment", 1L);
        assertThat(reload(orderId).getPaymentStatus()).isEqualTo(Order.PaymentStatus.PAID);

        // --- PAID -> PACKED (stock is reduced, Req 9.2) ---
        transition(orderId, Order.OrderStatus.PACKED, "Packed for shipping");
        assertThat(reload(orderId).getStatus()).isEqualTo(Order.OrderStatus.PACKED);
        Product afterPack = productRepository.findById(ashwagandha.getId()).orElseThrow();
        assertThat(afterPack.getStockQuantity()).isEqualTo(initialStock - orderedQty);

        // --- PACKED -> DISPATCHED (sets dispatchedAt, Req 5.10) ---
        transition(orderId, Order.OrderStatus.DISPATCHED, "Handed to courier");
        Order dispatched = reload(orderId);
        assertThat(dispatched.getStatus()).isEqualTo(Order.OrderStatus.DISPATCHED);
        assertThat(dispatched.getDispatchedAt()).isNotNull();

        // --- DISPATCHED -> DELIVERED (sets deliveredAt, Req 5.11) ---
        transition(orderId, Order.OrderStatus.DELIVERED, "Delivered to customer");
        Order delivered = reload(orderId);
        assertThat(delivered.getStatus()).isEqualTo(Order.OrderStatus.DELIVERED);
        assertThat(delivered.getDeliveredAt()).isNotNull();

        // --- Delivered order shows up in the daily sales report (Req 13.3) ---
        mockMvc.perform(get("/reports/daily-sales")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredOrders").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalSalesAmount").value(greaterThanOrEqualTo(1500.0)));
    }

    // ==================== 2. WHATSAPP ORDER PROCESSING ====================

    /**
     * Processes a raw WhatsApp message end-to-end: the parser extracts the
     * customer, products and payment mode, a customer is auto-created, products are
     * matched, and a WHATSAPP-sourced order is persisted with correct line items
     * and totals.
     *
     * <p>Validates Requirements 1.2, 1.3, 3.1-3.6, 4.1.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("WhatsApp order processing parses message and creates a persisted order")
    void whatsAppOrderProcessing_withParsing() throws Exception {
        String whatsappText = """
                Name: Meena Iyer
                Phone: 9123456780
                Address: 45 Park Avenue, Pune, Maharashtra, 411001

                Order:
                2 x Ashwagandha Capsules
                1 x Triphala Powder

                Payment: COD
                """;

        WhatsAppOrderRequest request = WhatsAppOrderRequest.builder()
                .whatsappText(whatsappText)
                .salespersonId(salesperson.getId())
                .build();

        MvcResult result = mockMvc.perform(post("/orders/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderSource").value("WHATSAPP"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.paymentMode").value("COD"))
                .andExpect(jsonPath("$.customer.name").value("Meena Iyer"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

        Order saved = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(saved.getOrderSource()).isEqualTo(Order.OrderSource.WHATSAPP);
        assertThat(saved.getRawWhatsappText()).isEqualTo(whatsappText);
        // 2 x 500 + 1 x 300 = 1300
        assertThat(saved.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1300.00));

        // Customer auto-created from the parsed message (Req 1.2, 3.6)
        Customer parsedCustomer = customerRepository.findById(response.getCustomer().getId())
                .orElseThrow();
        assertThat(parsedCustomer.getPhone()).isEqualTo("9123456780");
        assertThat(parsedCustomer.getPincode()).isEqualTo("411001");
    }

    // ==================== 3. PAYMENT RECORDING AND TRACKING ====================

    /**
     * Records a partial payment followed by the remaining balance and verifies the
     * payment status transitions PENDING &rarr; PARTIAL &rarr; PAID and that the
     * payment records are tracked against the order.
     *
     * <p>Validates Requirements 7.1, 7.3, 7.4, 7.5.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Payment recording tracks partial then full payments and updates status")
    void paymentRecording_partialThenFull() throws Exception {
        Long orderId = createManualOrder(2); // 2 x 500 = 1000
        assertThat(reload(orderId).getPaymentStatus()).isEqualTo(Order.PaymentStatus.PENDING);

        // First partial payment
        orderService.recordPayment(orderId, BigDecimal.valueOf(400.00),
                Order.PaymentMode.UPI, "TXN-P1", "Advance", 1L);
        assertThat(reload(orderId).getPaymentStatus()).isEqualTo(Order.PaymentStatus.PARTIAL);

        // Remaining balance clears the order
        Order afterFull = orderService.recordPayment(orderId, BigDecimal.valueOf(600.00),
                Order.PaymentMode.COD, "TXN-P2", "Balance on delivery", 1L);
        assertThat(afterFull.getPaymentStatus()).isEqualTo(Order.PaymentStatus.PAID);
        assertThat(afterFull.getPaymentRecords()).hasSize(2);
    }

    /**
     * A payment that would exceed the order total is rejected, protecting the
     * payment-balance invariant.
     *
     * <p>Validates Requirements 7.2, 7.7.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Payment exceeding the order total is rejected")
    void paymentRecording_overpaymentRejected() throws Exception {
        Long orderId = createManualOrder(1); // 1 x 500 = 500

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                orderService.recordPayment(orderId, BigDecimal.valueOf(600.00),
                        Order.PaymentMode.UPI, "TXN-OVER", "Too much", 1L));
    }

    // ==================== 4. STOCK MANAGEMENT THROUGHOUT LIFECYCLE ====================

    /**
     * Verifies stock is reduced when an order is PACKED and restored when the order
     * is subsequently RETURNED after dispatch, keeping inventory consistent across
     * the lifecycle. (PACKED cannot transition directly to CANCELLED under the
     * workflow rules, so restoration is exercised through the RETURN path.)
     *
     * <p>Validates Requirements 9.2, 9.3.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Stock is reduced on PACKED and restored on RETURNED")
    void stockManagement_reducedOnPacked_restoredOnReturn() throws Exception {
        int orderedQty = 4;
        int initialStock = ashwagandha.getStockQuantity();

        Long orderId = createManualOrder(orderedQty);
        transition(orderId, Order.OrderStatus.CONFIRMED, null);
        transition(orderId, Order.OrderStatus.PAID, null);
        orderService.recordPayment(orderId, BigDecimal.valueOf(2000.00),
                Order.PaymentMode.UPI, "TXN-STOCK", "Paid", 1L);

        // PACKED reduces stock
        transition(orderId, Order.OrderStatus.PACKED, null);
        int packedStock = productRepository.findById(ashwagandha.getId()).orElseThrow()
                .getStockQuantity();
        assertThat(packedStock).isEqualTo(initialStock - orderedQty);

        // DISPATCHED then RETURNED restores stock
        transition(orderId, Order.OrderStatus.DISPATCHED, null);
        transition(orderId, Order.OrderStatus.RETURNED, "Returned by customer");
        Order returned = reload(orderId);
        assertThat(returned.getStatus()).isEqualTo(Order.OrderStatus.RETURNED);
        int restoredStock = productRepository.findById(ashwagandha.getId()).orElseThrow()
                .getStockQuantity();
        assertThat(restoredStock).isEqualTo(initialStock);
    }

    // ==================== 5. REPORT GENERATION WITH REAL DATA ====================

    /**
     * Generates a daily sales report after delivering a real order and asserts the
     * report reflects the delivered order and its sales amount.
     *
     * <p>Validates Requirements 13.1, 13.2, 13.3.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Daily sales report reflects a delivered order")
    void reportGeneration_withRealData() throws Exception {
        Long orderId = deliverOrder(2); // 2 x 500 = 1000

        assertThat(reload(orderId).getStatus()).isEqualTo(Order.OrderStatus.DELIVERED);

        mockMvc.perform(get("/reports/daily-sales")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.deliveredOrders").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalSalesAmount").value(greaterThanOrEqualTo(1000.0)));
    }

    // ==================== 6. DISPATCH LABEL GENERATION ====================

    /**
     * Generates a dispatch label for a PAID order and asserts both the label data
     * (customer + barcode) and the rendered PDF are returned.
     *
     * <p>Validates Requirements 12.1-12.5.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Dispatch label data and PDF are generated for a PAID order")
    void dispatchLabelGeneration_returnsPdf() throws Exception {
        Long orderId = createManualOrder(1);
        transition(orderId, Order.OrderStatus.CONFIRMED, null);
        transition(orderId, Order.OrderStatus.PAID, null);

        mockMvc.perform(get("/dispatch/labels/" + orderId + "/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Rajesh Kumar"))
                .andExpect(jsonPath("$.barcode").exists());

        mockMvc.perform(get("/dispatch/labels/" + orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    // ==================== 7. VYAPAR EXPORT ====================

    /**
     * Exports a selected order to a Vyapar-compatible CSV and asserts the CSV is
     * returned with the expected content type and is non-empty.
     *
     * <p>Validates Requirements 16.1, 16.2.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Selected orders export to a Vyapar-compatible CSV")
    void vyaparExport_returnsCsv() throws Exception {
        Long orderId = createManualOrder(2);

        MvcResult result = mockMvc.perform(post("/billing/export/vyapar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("orderIds", List.of(orderId)))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv).isNotBlank();
    }

    // ==================== 8. MULTI-TENANT ISOLATION ====================

    /**
     * Exercises the datasource routing decision that backs per-tenant data
     * isolation: two distinct tenants resolve to two distinct, non-null routing
     * keys, and no tenant context resolves to the default (master) datasource.
     * This is the mechanism that prevents one tenant's queries from reaching
     * another tenant's database.
     *
     * <p>Validates Requirements 17.1-17.5 (routing decision that backs isolation).
     */
    @Test
    @DisplayName("Multi-tenant isolation: routing keys differ per tenant and default with none")
    void multiTenantIsolation_routingKeysDiffer() {
        ExposedRoutingDataSource routing = new ExposedRoutingDataSource();

        TenantContext.clear();
        assertThat(routing.resolveLookupKey())
                .as("no tenant context -> default/master datasource")
                .isNull();

        TenantContext.setTenantKey("vendor_one");
        Object keyOne = routing.resolveLookupKey();

        TenantContext.setTenantKey("vendor_two");
        Object keyTwo = routing.resolveLookupKey();

        assertThat(keyOne).isEqualTo("vendor_one");
        assertThat(keyTwo).isEqualTo("vendor_two");
        assertThat(keyOne)
                .as("different tenants must route to different datasources")
                .isNotEqualTo(keyTwo);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    /** Exposes the protected routing-key resolution for assertions. */
    private static class ExposedRoutingDataSource extends TenantRoutingDataSource {
        Object resolveLookupKey() {
            return determineCurrentLookupKey();
        }
    }

    // ==================== HELPERS ====================

    private ManualOrderRequest buildManualOrderRequest(int quantity) {
        ManualOrderRequest.OrderItemRequest item = ManualOrderRequest.OrderItemRequest.builder()
                .productId(ashwagandha.getId())
                .quantity(quantity)
                .build();

        return ManualOrderRequest.builder()
                .customerId(customer.getId())
                .salespersonId(salesperson.getId())
                .items(List.of(item))
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .build();
    }

    private Long createManualOrder(int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/orders/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildManualOrderRequest(quantity))))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        return response.getId();
    }

    /** Creates an order and drives it all the way to DELIVERED. */
    private Long deliverOrder(int quantity) throws Exception {
        Long orderId = createManualOrder(quantity);
        transition(orderId, Order.OrderStatus.CONFIRMED, null);
        transition(orderId, Order.OrderStatus.PAID, null);
        orderService.recordPayment(orderId,
                ashwagandha.getSalePrice().multiply(BigDecimal.valueOf(quantity)),
                Order.PaymentMode.UPI, "TXN-DELIVER", "Paid", 1L);
        transition(orderId, Order.OrderStatus.PACKED, null);
        transition(orderId, Order.OrderStatus.DISPATCHED, null);
        transition(orderId, Order.OrderStatus.DELIVERED, null);
        return orderId;
    }

    private void transition(Long orderId, Order.OrderStatus newStatus, String notes) throws Exception {
        com.ayurveda.platform.dto.request.UpdateOrderStatusRequest statusRequest =
                new com.ayurveda.platform.dto.request.UpdateOrderStatusRequest();
        statusRequest.setNewStatus(newStatus);
        if (notes != null) {
            statusRequest.setNotes(notes);
        }

        mockMvc.perform(put("/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk());
    }

    private Order reload(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }
}
