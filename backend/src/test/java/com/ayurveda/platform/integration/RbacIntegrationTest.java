package com.ayurveda.platform.integration;

import com.ayurveda.platform.dto.request.ManualOrderRequest;
import com.ayurveda.platform.dto.request.UpdateOrderStatusRequest;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.entity.Salesperson;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role-Based Access Control (RBAC) integration tests (Task 19.2).
 *
 * <p>Exercises the full Spring Security + Spring MVC stack against an in-memory H2
 * database to verify the role-based authorization rules declared in
 * {@code SecurityConfig} (task 19.1) and the complementary {@code @PreAuthorize}
 * annotations on the controllers. The effective authorization for any request is the
 * intersection of the request-level path rule and the method-level annotation; a role
 * outside that intersection must receive HTTP 403 (handled by
 * {@code JwtAccessDeniedHandler}), and unauthenticated requests must receive HTTP 401.
 *
 * <p>Supported roles (authorities stored as {@code ROLE_<name>}): SUPER_ADMIN,
 * TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT.
 *
 * <p>Each test seeds the {@link org.springframework.security.core.Authentication} via
 * {@code SecurityMockMvcRequestPostProcessors.user(...).roles(...)} so the role matrix
 * can be driven without per-method {@code @WithMockUser} annotations. "Authorized" is
 * asserted as "not 401 and not 403" so the assertions target the authorization decision
 * rather than downstream business logic; "forbidden" is asserted as an exact 403.
 *
 * <p><b>Known inconsistencies (flagged, not blocking):</b>
 * <ul>
 *   <li>{@code CustomerController} {@code @PreAuthorize} uses the literal role
 *       {@code 'ADMIN'} instead of {@code 'TENANT_ADMIN'}. These tests therefore do not
 *       rely on TENANT_ADMIN reaching customer endpoints via the method annotation.</li>
 *   <li>Requirement 19.2 states SALESPERSON should be allowed order <i>status updates</i>,
 *       but {@code OrderController.updateOrderStatusV2} excludes SALESPERSON, so a
 *       SALESPERSON is denied (403). The test below documents the <i>actual</i> behavior.</li>
 * </ul>
 *
 * Validates: Requirements 19.1, 19.2, 19.3, 19.4, 19.5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RbacIntegrationTest {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String TENANT_ADMIN = "TENANT_ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String SALESPERSON = "SALESPERSON";
    private static final String DISPATCHER = "DISPATCHER";
    private static final String ACCOUNTANT = "ACCOUNTANT";

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
                .sku("RBAC001")
                .name("Ashwagandha Capsules")
                .category("Supplements")
                .salePrice(BigDecimal.valueOf(500.00))
                .mrp(BigDecimal.valueOf(600.00))
                .weightGrams(BigDecimal.valueOf(250))
                .stockQuantity(100)
                .lowStockThreshold(10)
                .isActive(true)
                .build());

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

    // ==================== 401: UNAUTHENTICATED ACCESS (Req 18.4 / 19.5) ====================

    /**
     * Unauthenticated access to protected endpoints is rejected with HTTP 401.
     * Validates Requirement 19.5 (and 18.4): authentication is required before
     * authorization is even considered.
     */
    @Test
    @DisplayName("Unauthenticated requests to protected endpoints return 401")
    void unauthenticated_returns401() throws Exception {
        String[] protectedGets = {
                "/orders",
                "/products",
                "/reports/daily-sales?date=" + LocalDate.now(),
                "/dispatch/queue",
                "/billing/history",
                "/admin/tenants",
                "/admin/users"
        };
        for (String path : protectedGets) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== TENANT MANAGEMENT: SUPER_ADMIN ONLY ====================

    /**
     * Tenant management (/admin/tenants) is restricted to SUPER_ADMIN; every other
     * role receives 403. Validates Requirements 19.1, 19.5.
     */
    @Test
    @DisplayName("Tenant management is SUPER_ADMIN only; other roles get 403")
    void tenantManagement_superAdminOnly() throws Exception {
        expectAuthorized(get("/admin/tenants"), SUPER_ADMIN);

        for (String role : new String[]{TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT}) {
            expectForbidden(get("/admin/tenants"), role);
        }
    }

    // ==================== USER MANAGEMENT: SALESPERSON RESTRICTED (Req 19.2) ====================

    /**
     * User management (/admin/users) is restricted to TENANT_ADMIN. SALESPERSON and
     * the other operational roles are denied with 403, satisfying Requirement 19.2's
     * "restrict user management" clause.
     */
    @Test
    @DisplayName("User management allows TENANT_ADMIN; SALESPERSON and operational roles get 403 (Req 19.2)")
    void userManagement_restrictedFromSalesperson() throws Exception {
        expectAuthorized(get("/admin/users"), TENANT_ADMIN);

        for (String role : new String[]{SALESPERSON, DISPATCHER, ACCOUNTANT, MANAGER}) {
            expectForbidden(get("/admin/users"), role);
        }
    }

    // ==================== PRODUCT READ ====================

    /**
     * Product viewing is allowed for TENANT_ADMIN, MANAGER, SALESPERSON; DISPATCHER and
     * ACCOUNTANT are denied by the method-level rule. Validates Requirements 19.1, 19.5.
     */
    @Test
    @DisplayName("Product read allows admin/manager/salesperson; dispatcher/accountant get 403")
    void productRead_roleMatrix() throws Exception {
        for (String role : new String[]{TENANT_ADMIN, MANAGER, SALESPERSON}) {
            expectAuthorized(get("/products"), role);
        }
        for (String role : new String[]{DISPATCHER, ACCOUNTANT}) {
            expectForbidden(get("/products"), role);
        }
    }

    // ==================== ORDER READ ====================

    /**
     * Order viewing is allowed for TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER;
     * ACCOUNTANT is denied. Validates Requirements 19.1, 19.5.
     */
    @Test
    @DisplayName("Order read allows admin/manager/salesperson/dispatcher; accountant gets 403")
    void orderRead_roleMatrix() throws Exception {
        for (String role : new String[]{TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER}) {
            expectAuthorized(get("/orders"), role);
        }
        expectForbidden(get("/orders"), ACCOUNTANT);
    }

    // ==================== SALESPERSON: ORDER CREATION (Req 19.2) ====================

    /**
     * A SALESPERSON is allowed to create orders. Validates Requirement 19.2
     * ("allow order creation").
     */
    @Test
    @DisplayName("SALESPERSON can create orders (Req 19.2)")
    void salesperson_canCreateOrder() throws Exception {
        mockMvc.perform(post("/orders/manual")
                        .with(user("salesUser").roles(SALESPERSON))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildManualOrderRequest(2))))
                .andExpect(status().isCreated());
    }

    /**
     * Roles outside the order-creation rule (DISPATCHER, ACCOUNTANT) are denied with 403.
     * Validates Requirements 19.2, 19.5.
     */
    @Test
    @DisplayName("DISPATCHER and ACCOUNTANT cannot create orders (403)")
    void nonSalesRoles_cannotCreateOrder() throws Exception {
        for (String role : new String[]{DISPATCHER, ACCOUNTANT}) {
            mockMvc.perform(post("/orders/manual")
                            .with(user("u").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildManualOrderRequest(1))))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== DISPATCHER (Req 19.2, 19.3) ====================

    /**
     * A DISPATCHER can update order status. Validates Requirements 19.2/19.3
     * (dispatch workflow / status updates).
     */
    @Test
    @DisplayName("DISPATCHER can update order status (Req 19.2/19.3)")
    void dispatcher_canUpdateOrderStatus() throws Exception {
        Long orderId = createOrderAsAdmin();

        UpdateOrderStatusRequest statusRequest = new UpdateOrderStatusRequest();
        statusRequest.setNewStatus(Order.OrderStatus.CONFIRMED);
        statusRequest.setNotes("Confirmed by dispatcher");

        mockMvc.perform(put("/orders/" + orderId + "/status")
                        .with(user("dispatchUser").roles(DISPATCHER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk());
    }

    /**
     * A DISPATCHER can view the dispatch queue (PAID/PACKED orders). Validates
     * Requirement 19.3.
     */
    @Test
    @DisplayName("DISPATCHER can view dispatch queue; ACCOUNTANT gets 403 (Req 19.3)")
    void dispatcher_canViewDispatchQueue() throws Exception {
        expectAuthorized(get("/dispatch/queue"), DISPATCHER);
        // Accountant has no dispatch responsibilities.
        expectForbidden(get("/dispatch/queue"), ACCOUNTANT);
    }

    // ==================== ACCOUNTANT (Req 19.4) ====================

    /**
     * An ACCOUNTANT can generate reports and access billing exports. Validates
     * Requirement 19.4 (payment recording / report generation).
     */
    @Test
    @DisplayName("ACCOUNTANT can run reports and billing exports (Req 19.4)")
    void accountant_canAccessReportsAndBilling() throws Exception {
        expectAuthorized(get("/reports/daily-sales?date=" + LocalDate.now()), ACCOUNTANT);
        expectAuthorized(get("/billing/history"), ACCOUNTANT);
    }

    /**
     * Reports are allowed for TENANT_ADMIN, MANAGER, ACCOUNTANT; SALESPERSON and
     * DISPATCHER are denied. Validates Requirements 19.4, 19.5.
     */
    @Test
    @DisplayName("Reports allow admin/manager/accountant; salesperson/dispatcher get 403")
    void reports_roleMatrix() throws Exception {
        String path = "/reports/daily-sales?date=" + LocalDate.now();
        for (String role : new String[]{TENANT_ADMIN, MANAGER, ACCOUNTANT}) {
            expectAuthorized(get(path), role);
        }
        for (String role : new String[]{SALESPERSON, DISPATCHER}) {
            expectForbidden(get(path), role);
        }
    }

    /**
     * Billing exports are allowed for TENANT_ADMIN, MANAGER, ACCOUNTANT; SALESPERSON and
     * DISPATCHER are denied. Validates Requirements 19.4, 19.5.
     */
    @Test
    @DisplayName("Billing exports allow admin/manager/accountant; salesperson/dispatcher get 403")
    void billing_roleMatrix() throws Exception {
        for (String role : new String[]{TENANT_ADMIN, MANAGER, ACCOUNTANT}) {
            expectAuthorized(get("/billing/history"), role);
        }
        for (String role : new String[]{SALESPERSON, DISPATCHER}) {
            expectForbidden(get("/billing/history"), role);
        }
    }

    // ==================== SALESPERSON STATUS UPDATE (Req 19.2) ====================

    /**
     * A SALESPERSON is allowed to update order status via PUT /orders/{id}/status.
     * Both the request-level matcher in SecurityConfig (which includes SALESPERSON)
     * and the method-level {@code @PreAuthorize} on {@code updateOrderStatusV2} include
     * the SALESPERSON role, so the request is authorized (HTTP 200). Validates
     * Requirement 19.2 ("allow order status updates for salesperson").
     */
    @Test
    @DisplayName("SALESPERSON is allowed to update order status (Req 19.2)")
    void salesperson_statusUpdate_currentlyForbidden() throws Exception {
        Long orderId = createOrderAsAdmin();

        UpdateOrderStatusRequest statusRequest = new UpdateOrderStatusRequest();
        statusRequest.setNewStatus(Order.OrderStatus.CONFIRMED);

        mockMvc.perform(put("/orders/" + orderId + "/status")
                        .with(user("salesUser").roles(SALESPERSON))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk());
    }

    // ==================== HELPERS ====================

    /**
     * Performs the request as the given role and asserts the RBAC layer authorized it
     * (i.e. neither 401 Unauthorized nor 403 Forbidden). Downstream business-logic status
     * codes are intentionally not asserted here.
     */
    private void expectAuthorized(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                  String role) throws Exception {
        ResultActions actions = mockMvc.perform(builder.with(user("u_" + role).roles(role)));
        int statusCode = actions.andReturn().getResponse().getStatus();
        assertThat(statusCode)
                .as("role %s should be authorized (not 401/403) for the endpoint", role)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    /**
     * Performs the request as the given role and asserts the RBAC layer denied it with
     * HTTP 403 Forbidden (Req 19.5).
     */
    private void expectForbidden(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                                 String role) throws Exception {
        mockMvc.perform(builder.with(user("u_" + role).roles(role)))
                .andExpect(status().isForbidden());
    }

    private Long createOrderAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/orders/manual")
                        .with(user("adminUser").roles(TENANT_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildManualOrderRequest(1))))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        return response.getId();
    }

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
}
