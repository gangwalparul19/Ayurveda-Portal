package com.ayurveda.platform.integration;

import com.ayurveda.platform.dto.request.CreateCustomerRequest;
import com.ayurveda.platform.dto.request.CreateProductRequest;
import com.ayurveda.platform.dto.request.LoginRequest;
import com.ayurveda.platform.dto.request.ManualOrderRequest;
import com.ayurveda.platform.dto.request.StockUpdateRequest;
import com.ayurveda.platform.dto.request.UpdateOrderStatusRequest;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.repository.PlatformUserRepository;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.entity.Salesperson;
import com.ayurveda.platform.tenant.entity.StockHistory;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.repository.SalespersonRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Integration tests for the REST API controllers (Task 18.9).
 *
 * Exercises the full Spring MVC stack (controller -> service -> repository -> H2)
 * for the core REST controllers built in task 18:
 * - OrderController       (create, status transition, retrieval, listing)
 * - ProductController     (create, retrieval, listing, stock update, low-stock)
 * - CustomerController    (create, retrieval, search)
 * - ReportController      (daily sales report generation)
 * - DispatchController    (label data + PDF label generation)
 * - AuthController        (login flow, token issuance)
 * - Security              (unauthenticated access is rejected)
 *
 * Uses a real Spring context with an in-memory H2 database. Method-level
 * security is exercised via {@link WithMockUser}; the auth flow test seeds a
 * real {@link PlatformUser} and authenticates through the login endpoint.
 *
 * Validates: All API-related requirements for task 18 (1.1-1.3, 5.x, 8.x, 9.x,
 * 10.x, 12.x, 13.x, 18.x).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SalespersonRepository salespersonRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Product testProduct;
    private Customer testCustomer;
    private Salesperson testSalesperson;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        salespersonRepository.deleteAll();

        testProduct = productRepository.save(Product.builder()
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

        // Customer with a complete shipping address (required to reach DISPATCHED,
        // and used by the dispatch label tests).
        testCustomer = customerRepository.save(Customer.builder()
                .name("Rajesh Kumar")
                .phone("9876543210")
                .email("rajesh@example.com")
                .addressLine1("123 Main Street")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .build());

        testSalesperson = salespersonRepository.save(Salesperson.builder()
                .employeeCode("EMP001")
                .name("Jane Smith")
                .phone("9988776655")
                .email("jane@example.com")
                .status(Salesperson.SalespersonStatus.ACTIVE)
                .commissionRate(BigDecimal.valueOf(5.0))
                .platformUserId(1L)
                .build());
    }

    // ==================== ORDER CONTROLLER ====================

    /**
     * POST /orders/manual creates an order with NEW status and persists it.
     * Validates Requirements 1.1, 1.2, 4.1.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("POST /orders/manual creates an order and returns 201")
    void createManualOrder_returnsCreated() throws Exception {
        ManualOrderRequest request = buildManualOrderRequest(2);

        MvcResult result = mockMvc.perform(post("/orders/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.orderSource").value("MANUAL"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalAmount").value(1000.00))
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

        Order saved = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(Order.OrderStatus.NEW);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getCustomer().getId()).isEqualTo(testCustomer.getId());
    }

    /**
     * PUT /orders/{id}/status transitions NEW -> CONFIRMED.
     * Validates Requirements 5.2, 5.9.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("PUT /orders/{id}/status transitions order status")
    void updateOrderStatus_transitionsToConfirmed() throws Exception {
        Long orderId = createOrderViaApi(1);

        UpdateOrderStatusRequest statusRequest = new UpdateOrderStatusRequest();
        statusRequest.setNewStatus(Order.OrderStatus.CONFIRMED);
        statusRequest.setNotes("Confirmed by integration test");

        mockMvc.perform(put("/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    /**
     * PUT /orders/{id}/status rejects an invalid transition (NEW -> DELIVERED).
     * Validates Requirement 5.8.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("PUT /orders/{id}/status rejects invalid transition")
    void updateOrderStatus_invalidTransitionRejected() throws Exception {
        Long orderId = createOrderViaApi(1);

        UpdateOrderStatusRequest statusRequest = new UpdateOrderStatusRequest();
        statusRequest.setNewStatus(Order.OrderStatus.DELIVERED);

        mockMvc.perform(put("/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().is4xxClientError());
    }

    /**
     * GET /orders/{id} returns a persisted order, and GET /orders lists orders.
     * Validates Requirements 22.1, 22.2.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("GET /orders and GET /orders/{id} return orders")
    void getOrders_returnsOrders() throws Exception {
        Long orderId = createOrderViaApi(1);

        mockMvc.perform(get("/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(greaterThanOrEqualTo(1)));
    }

    // ==================== PRODUCT CONTROLLER ====================

    /**
     * POST /products creates a product, GET retrieves it, and listing returns it.
     * Validates Requirements 8.1, 8.2, 8.3.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Product CRUD endpoints create, fetch and list products")
    void productEndpoints_createFetchList() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("TRIPH001")
                .name("Triphala Powder")
                .category("Powders")
                .mrp(BigDecimal.valueOf(350.00))
                .salePrice(BigDecimal.valueOf(300.00))
                .stockQuantity(40)
                .lowStockThreshold(5)
                .build();

        MvcResult created = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("TRIPH001"))
                .andExpect(jsonPath("$.name").value("Triphala Powder"))
                .andReturn();

        Long productId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TRIPH001"));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    /**
     * POST /products/{id}/stock records a stock movement and updates quantity.
     * Validates Requirements 9.1, 9.2, 9.6.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("POST /products/{id}/stock updates stock quantity")
    void updateStock_increasesQuantity() throws Exception {
        StockUpdateRequest stockRequest = StockUpdateRequest.builder()
                .quantity(25)
                .operation(StockHistory.StockOperation.STOCK_IN)
                .referenceType("ADJUSTMENT")
                .notes("Restock")
                .build();

        mockMvc.perform(post("/products/" + testProduct.getId() + "/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stockRequest)))
                .andExpect(status().isNoContent());

        Product updated = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(125);
    }

    /**
     * GET /products/low-stock returns low-stock products.
     * Validates Requirement 9.5.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("GET /products/low-stock returns low stock products")
    void lowStockEndpoint_returnsOk() throws Exception {
        // Drive the product below its low-stock threshold.
        testProduct.setStockQuantity(2);
        productRepository.save(testProduct);

        mockMvc.perform(get("/products/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ==================== CUSTOMER CONTROLLER ====================

    /**
     * POST /customers creates a customer, GET fetches it, search finds it.
     * Validates Requirements 10.1, 10.3.
     */
    @Test
    // CustomerController authorizes on ADMIN/MANAGER/SALESPERSON. The RBAC role
    // naming is being adjusted concurrently (task 19.1), so grant the full set of
    // admin/manager roles to keep this endpoint test stable regardless of naming.
    @WithMockUser(username = "1", roles = {"ADMIN", "TENANT_ADMIN", "MANAGER"})
    @DisplayName("Customer endpoints create, fetch and search customers")
    void customerEndpoints_createFetchSearch() throws Exception {
        CreateCustomerRequest request = CreateCustomerRequest.builder()
                .name("Anita Sharma")
                .phone("9123456780")
                .email("anita@example.com")
                .addressLine1("45 Park Avenue")
                .city("Pune")
                .state("Maharashtra")
                .pincode("411001")
                .build();

        MvcResult created = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Anita Sharma"))
                .andExpect(jsonPath("$.phone").value("9123456780"))
                .andReturn();

        Long customerId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/customers/" + customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId));

        mockMvc.perform(get("/customers/search").param("q", "Anita"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));
    }

    // ==================== REPORT CONTROLLER ====================

    /**
     * GET /reports/daily-sales returns a report for the given date.
     * Validates Requirements 13.1-13.4.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("GET /reports/daily-sales returns a report")
    void dailySalesReport_returnsOk() throws Exception {
        mockMvc.perform(get("/reports/daily-sales")
                        .param("date", LocalDate.now().toString()))
                .andExpect(status().isOk());
    }

    // ==================== DISPATCH CONTROLLER ====================

    /**
     * Dispatch label endpoints: prepare label data and generate a PDF label for a
     * PAID order. Validates Requirements 12.1-12.5.
     */
    @Test
    @WithMockUser(username = "1", roles = {"TENANT_ADMIN"})
    @DisplayName("Dispatch label endpoints return data and a PDF for a PAID order")
    void dispatchLabelEndpoints_forPaidOrder() throws Exception {
        Long orderId = createOrderViaApi(1);
        transitionStatus(orderId, Order.OrderStatus.CONFIRMED);
        transitionStatus(orderId, Order.OrderStatus.PAID);

        // Label data (no PDF generation)
        mockMvc.perform(get("/dispatch/labels/" + orderId + "/data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Rajesh Kumar"))
                .andExpect(jsonPath("$.barcode").exists());

        // Generated PDF label
        mockMvc.perform(get("/dispatch/labels/" + orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    // ==================== AUTH CONTROLLER ====================

    /**
     * POST /auth/login authenticates a seeded user and returns JWT tokens.
     * Validates Requirements 18.1, 18.2, 18.3.
     */
    @Test
    @DisplayName("POST /auth/login authenticates a user and returns tokens")
    void login_returnsTokens() throws Exception {
        platformUserRepository.save(PlatformUser.builder()
                .username("admin_user")
                .email("admin_user@example.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(PlatformUser.UserRole.TENANT_ADMIN)
                .fullName("Admin User")
                .isActive(true)
                .build());

        LoginRequest loginRequest = new LoginRequest("admin_user", "Secret123!");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("admin_user"))
                .andExpect(jsonPath("$.role").value("TENANT_ADMIN"));
    }

    /**
     * POST /auth/login rejects invalid credentials.
     * Validates Requirement 18.2.
     */
    @Test
    @DisplayName("POST /auth/login rejects invalid credentials")
    void login_invalidCredentials_rejected() throws Exception {
        platformUserRepository.save(PlatformUser.builder()
                .username("admin_user2")
                .email("admin_user2@example.com")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(PlatformUser.UserRole.TENANT_ADMIN)
                .fullName("Admin User 2")
                .isActive(true)
                .build());

        LoginRequest loginRequest = new LoginRequest("admin_user2", "WrongPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().is4xxClientError());
    }

    // ==================== SECURITY ====================

    /**
     * Protected endpoints reject unauthenticated requests.
     * Validates Requirement 18.4.
     */
    @Test
    @DisplayName("Unauthenticated access to a protected endpoint is rejected")
    void unauthenticatedAccess_rejected() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== HELPERS ====================

    private ManualOrderRequest buildManualOrderRequest(int quantity) {
        ManualOrderRequest.OrderItemRequest item = ManualOrderRequest.OrderItemRequest.builder()
                .productId(testProduct.getId())
                .quantity(quantity)
                .build();

        return ManualOrderRequest.builder()
                .customerId(testCustomer.getId())
                .salespersonId(testSalesperson.getId())
                .items(List.of(item))
                .paymentMode(Order.PaymentMode.COD)
                .orderDate(LocalDate.now())
                .build();
    }

    private Long createOrderViaApi(int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/orders/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildManualOrderRequest(quantity))))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        return response.getId();
    }

    private void transitionStatus(Long orderId, Order.OrderStatus newStatus) throws Exception {
        UpdateOrderStatusRequest statusRequest = new UpdateOrderStatusRequest();
        statusRequest.setNewStatus(newStatus);

        mockMvc.perform(put("/orders/" + orderId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk());
    }
}
