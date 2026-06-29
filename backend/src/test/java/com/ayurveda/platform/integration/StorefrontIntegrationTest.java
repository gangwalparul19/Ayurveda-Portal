package com.ayurveda.platform.integration;

import com.ayurveda.platform.dto.request.StorefrontOrderRequest;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the public storefront (Task 23.2).
 *
 * Exercises the full Spring MVC stack (controller -> service -> repository -> H2)
 * for the customer-facing storefront endpoints and verifies the public,
 * unauthenticated behaviour described in Requirement 1.3:
 * <ul>
 *   <li>Unauthenticated browsing of ACTIVE products works; INACTIVE products are
 *       never exposed through the public browse endpoint.</li>
 *   <li>Placing a public order succeeds, auto-creates the customer, and persists an
 *       order whose source is {@link Order.OrderSource#STOREFRONT}.</li>
 *   <li>Invalid order payloads (bad phone number, missing items) are rejected with
 *       HTTP 400 by request validation.</li>
 * </ul>
 *
 * <p><b>Profile choice.</b> Two storefront controllers exist, gated by profile:
 * {@code StorefrontSimpleController} ({@code @Profile("simple")}, direct repository
 * access, paths under {@code /storefront/...}) and {@code StorefrontController}
 * ({@code @Profile("dev")}, multi-tenant, paths under {@code /storefront/{tenantKey}/...}).
 * The "dev" controller depends on the multi-tenant {@code DataSourceConfig}
 * ({@code @Profile("dev")}), which wires Hikari pools per tenant and is not suitable
 * for the single in-memory H2 datasource used in tests. We therefore activate the
 * {@code "simple"} profile alongside {@code "test"} so the simpler, single-database
 * {@code StorefrontSimpleController} is the active storefront controller. The "test"
 * profile is listed last in {@code @ActiveProfiles} so {@code application-test.yml}
 * (in-memory H2) wins over the MySQL datasource declared in {@code application-simple.yml}.
 * All {@code /storefront/**} routes are {@code permitAll} in {@code SecurityConfig}, so
 * no authentication is applied in any test below.
 *
 * Validates: Requirement 1.3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"simple", "test"})
@Transactional
class StorefrontIntegrationTest {

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

    private Product activeProduct;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();

        activeProduct = productRepository.save(Product.builder()
                .sku("STORE-ACT-001")
                .name("Brahmi Tonic")
                .category("Tonics")
                .description("Active storefront product")
                .salePrice(BigDecimal.valueOf(250.00))
                .mrp(BigDecimal.valueOf(300.00))
                .weightGrams(BigDecimal.valueOf(200))
                .stockQuantity(50)
                .lowStockThreshold(5)
                .isActive(true)
                .build());

        // An inactive product that must never appear on the public storefront.
        productRepository.save(Product.builder()
                .sku("STORE-INACT-001")
                .name("Discontinued Churna")
                .category("Powders")
                .description("Inactive storefront product")
                .salePrice(BigDecimal.valueOf(150.00))
                .mrp(BigDecimal.valueOf(180.00))
                .weightGrams(BigDecimal.valueOf(100))
                .stockQuantity(20)
                .lowStockThreshold(5)
                .isActive(false)
                .build());
    }

    // ==================== BROWSING (unauthenticated) ====================

    /**
     * GET /storefront/products is reachable without authentication and exposes only
     * ACTIVE products; the inactive product is filtered out.
     * Validates Requirement 1.3 (public, unauthenticated browsing).
     */
    @Test
    @DisplayName("Unauthenticated browse returns only active products")
    void browseProducts_unauthenticated_returnsOnlyActiveProducts() throws Exception {
        mockMvc.perform(get("/storefront/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Brahmi Tonic"))
                .andExpect(jsonPath("$.content[0].sku").value("STORE-ACT-001"));
    }

    /**
     * GET /storefront/products/{id} returns an active product, and an inactive
     * product is not retrievable through the public endpoint.
     * Validates Requirement 1.3.
     */
    @Test
    @DisplayName("Active product detail is public; inactive product is not exposed")
    void productDetail_exposesActiveButNotInactive() throws Exception {
        mockMvc.perform(get("/storefront/products/" + activeProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Brahmi Tonic"));

        Product inactive = productRepository.findAll().stream()
                .filter(p -> Boolean.FALSE.equals(p.getIsActive()))
                .findFirst()
                .orElseThrow();

        // The simple controller throws a RuntimeException for a missing/inactive product,
        // which the global handler maps to HTTP 500 - i.e. it is never returned as a
        // successful public product, confirming inactive products are not exposed.
        mockMvc.perform(get("/storefront/products/" + inactive.getId()))
                .andExpect(status().is5xxServerError());
    }

    // ==================== PLACING AN ORDER (unauthenticated) ====================

    /**
     * POST /storefront/orders places a public order, auto-creates the customer, and
     * persists an order with source STOREFRONT and status NEW.
     * Validates Requirement 1.3 (unauthenticated order creation + customer auto-creation).
     */
    @Test
    @DisplayName("Public order placement succeeds and persists a STOREFRONT order")
    void placeOrder_succeeds_persistsStorefrontOrderAndAutoCreatesCustomer() throws Exception {
        String newCustomerPhone = "9876500011";
        assertThat(customerRepository.findByPhone(newCustomerPhone)).isEmpty();

        StorefrontOrderRequest request = buildValidOrderRequest(newCustomerPhone, 3);

        MvcResult result = mockMvc.perform(post("/storefront/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.orderSource").value("STOREFRONT"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn();

        Long orderId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();

        // Order persisted with STOREFRONT source.
        Order saved = orderRepository.findById(orderId).orElseThrow();
        assertThat(saved.getOrderSource()).isEqualTo(Order.OrderSource.STOREFRONT);
        assertThat(saved.getStatus()).isEqualTo(Order.OrderStatus.NEW);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(3);

        // Customer auto-created from the order details.
        Customer createdCustomer = customerRepository.findByPhone(newCustomerPhone).orElseThrow();
        assertThat(createdCustomer.getName()).isEqualTo("Meera Nair");
        assertThat(saved.getCustomer().getId()).isEqualTo(createdCustomer.getId());
    }

    // ==================== VALIDATION FAILURES ====================

    /**
     * POST /storefront/orders rejects an invalid phone number with HTTP 400.
     * Validates Requirement 1.3 (input validation on the public endpoint).
     */
    @Test
    @DisplayName("Order with invalid phone number is rejected with 400")
    void placeOrder_invalidPhone_returnsBadRequest() throws Exception {
        StorefrontOrderRequest request = buildValidOrderRequest("12345", 1);

        mockMvc.perform(post("/storefront/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * POST /storefront/orders rejects a request with no order items with HTTP 400.
     * Validates Requirement 1.3 (input validation on the public endpoint).
     */
    @Test
    @DisplayName("Order with no items is rejected with 400")
    void placeOrder_missingItems_returnsBadRequest() throws Exception {
        StorefrontOrderRequest request = buildValidOrderRequest("9876500022", 1);
        request.setItems(List.of()); // no items

        mockMvc.perform(post("/storefront/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ==================== HELPERS ====================

    private StorefrontOrderRequest buildValidOrderRequest(String phone, int quantity) {
        StorefrontOrderRequest.OrderItemRequest item = new StorefrontOrderRequest.OrderItemRequest();
        item.setProductId(activeProduct.getId());
        item.setProductName(activeProduct.getName());
        item.setQuantity(quantity);
        item.setPrice(activeProduct.getSalePrice().doubleValue());

        StorefrontOrderRequest request = new StorefrontOrderRequest();
        request.setCustomerName("Meera Nair");
        request.setCustomerPhone(phone);
        request.setCustomerEmail("meera@example.com");
        request.setDeliveryAddress("78 Lake Road");
        request.setCity("Kochi");
        request.setState("Kerala");
        request.setPincode("682001");
        request.setItems(List.of(item));
        request.setPaymentMethod("COD");
        return request;
    }
}
