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
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Performance tests (Task 31.3).
 *
 * Exercises the full Spring MVC stack (controller -> service -> repository -> H2)
 * and asserts that the system meets the latency and concurrency targets defined
 * in Requirement 30:
 * <ul>
 *   <li>30.1 - CRUD API operations respond within 200ms (95th percentile)</li>
 *   <li>30.2 - Daily report generation completes within 2 seconds</li>
 *   <li>30.3 - A single dispatch label is generated within 500 milliseconds</li>
 *   <li>30.4 - The system supports 50 concurrent users without degradation</li>
 * </ul>
 *
 * <p>Each test performs a number of warm-up iterations before measuring so that
 * JIT compilation and lazy Spring/Hibernate initialisation do not skew the timing
 * of the first request. Latency targets are evaluated at the 95th percentile to
 * tolerate the occasional outlier (e.g. a GC pause) while still enforcing the
 * requirement across the bulk of requests.
 *
 * <p>This test is intentionally NOT {@code @Transactional}: the concurrency test
 * issues requests from a pool of worker threads, which must observe data committed
 * by the test thread. Seed data is therefore cleaned up explicitly before and
 * after each test.
 *
 * Validates: Requirements 30.1, 30.2, 30.3, 30.4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerformanceTest {

    /** 30.1 - CRUD operations must respond within 200ms at the 95th percentile. */
    private static final long CRUD_P95_BUDGET_MS = 200;
    /** 30.2 - Daily report generation must complete within 2 seconds. */
    private static final long REPORT_BUDGET_MS = 2000;
    /** 30.3 - A single dispatch label must be generated within 500ms. */
    private static final long LABEL_P95_BUDGET_MS = 500;
    /** 30.4 - Number of concurrent users to simulate. */
    private static final int CONCURRENT_USERS = 50;

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
    private Long testOrderId;

    @BeforeEach
    void setUp() throws Exception {
        cleanData();
        seedData();
        testOrderId = createOrderViaApi(1);
    }

    @AfterEach
    void tearDown() {
        cleanData();
    }

    // ==================== 30.1 - CRUD RESPONSE TIME ====================

    /**
     * CRUD API operations (create + reads) respond within 200ms at the 95th
     * percentile. Validates Requirement 30.1.
     */
    @Test
    @DisplayName("CRUD API operations respond within 200ms (p95)")
    void crudOperations_respondWithinBudget() throws Exception {
        // Warm up the MVC stack, security filters and Hibernate.
        for (int i = 0; i < 30; i++) {
            performCrudCycle();
        }

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            samples.add(measureMillis(this::performCrudCycle));
        }

        long p95 = percentile(samples, 95);
        System.out.printf("[perf] CRUD p95=%dms (budget %dms)%n", p95, CRUD_P95_BUDGET_MS);
        assertThat(p95)
                .as("CRUD operations should complete within %dms at the 95th percentile", CRUD_P95_BUDGET_MS)
                .isLessThanOrEqualTo(CRUD_P95_BUDGET_MS);
    }

    // ==================== 30.2 - REPORT GENERATION TIME ====================

    /**
     * Daily sales report generation completes within 2 seconds.
     * Validates Requirement 30.2.
     */
    @Test
    @DisplayName("Daily report generation completes within 2 seconds")
    void dailyReport_generatesWithinBudget() throws Exception {
        // Warm up.
        for (int i = 0; i < 3; i++) {
            generateDailyReport();
        }

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            samples.add(measureMillis(this::generateDailyReport));
        }

        long p95 = percentile(samples, 95);
        System.out.printf("[perf] Daily report p95=%dms (budget %dms)%n", p95, REPORT_BUDGET_MS);
        assertThat(p95)
                .as("Daily report generation should complete within %dms", REPORT_BUDGET_MS)
                .isLessThanOrEqualTo(REPORT_BUDGET_MS);
    }

    // ==================== 30.3 - LABEL GENERATION TIME ====================

    /**
     * A single dispatch label PDF is generated within 500 milliseconds.
     * Validates Requirement 30.3.
     */
    @Test
    @DisplayName("Single dispatch label generates within 500ms (p95)")
    void singleLabel_generatesWithinBudget() throws Exception {
        // Move the order into a dispatchable state (PAID or later).
        transitionStatus(testOrderId, Order.OrderStatus.CONFIRMED);
        transitionStatus(testOrderId, Order.OrderStatus.PAID);

        // Warm up.
        for (int i = 0; i < 5; i++) {
            generateSingleLabel(testOrderId);
        }

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            samples.add(measureMillis(() -> generateSingleLabel(testOrderId)));
        }

        long p95 = percentile(samples, 95);
        System.out.printf("[perf] Single label p95=%dms (budget %dms)%n", p95, LABEL_P95_BUDGET_MS);
        assertThat(p95)
                .as("A single dispatch label should be generated within %dms at the 95th percentile",
                        LABEL_P95_BUDGET_MS)
                .isLessThanOrEqualTo(LABEL_P95_BUDGET_MS);
    }

    // ==================== 30.4 - CONCURRENT USER HANDLING ====================

    /**
     * The system handles 50 concurrent users without errors or severe degradation.
     * All 50 simultaneous requests must succeed, and the 95th percentile latency
     * under load must stay within a multiple of the single-request CRUD budget.
     * Validates Requirement 30.4.
     */
    @Test
    @DisplayName("System handles 50 concurrent users without degradation")
    void concurrentUsers_handledWithoutDegradation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_USERS);
        try {
            CountDownLatch ready = new CountDownLatch(CONCURRENT_USERS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(CONCURRENT_USERS);

            List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < CONCURRENT_USERS; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        // Release all threads at the same instant to maximise contention.
                        start.await();
                        long elapsed = measureMillis(this::readProduct);
                        latencies.add(elapsed);
                        successCount.incrementAndGet();
                    } catch (Throwable t) {
                        failureCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // Wait until every worker is ready, then fire them simultaneously.
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS))
                    .as("all %d concurrent requests should complete", CONCURRENT_USERS)
                    .isTrue();

            assertThat(failureCount.get())
                    .as("no concurrent request should fail")
                    .isZero();
            assertThat(successCount.get())
                    .as("all %d concurrent requests should succeed", CONCURRENT_USERS)
                    .isEqualTo(CONCURRENT_USERS);

            long p95 = percentile(latencies, 95);
            System.out.printf("[perf] 50 concurrent users p95=%dms%n", p95);
            // Under 50x concurrency we allow generous headroom over the single-request
            // budget, but still bound latency to detect serious degradation.
            assertThat(p95)
                    .as("latency under %d concurrent users should not degrade severely", CONCURRENT_USERS)
                    .isLessThanOrEqualTo(REPORT_BUDGET_MS);
        } finally {
            executor.shutdownNow();
        }
    }

    // ==================== WORKLOADS ====================

    /**
     * A representative CRUD cycle exercising create + read + list operations.
     * Each request is asserted to return a 2xx response so that the latency
     * budget is only credited for genuinely successful operations. All endpoints
     * used here return DTOs (not lazy JPA entities) so they serialize reliably
     * outside of a surrounding transaction.
     */
    private void performCrudCycle() throws Exception {
        // CREATE - POST /orders/manual returns an OrderResponse DTO (201).
        createOrderViaApi(1);
        // READ - GET /products/{id} returns a ProductResponse DTO.
        mockMvc.perform(get("/products/" + testProduct.getId()).with(adminUser()))
                .andExpect(status().isOk());
        // READ - GET /customers/{id} returns a CustomerResponse DTO.
        mockMvc.perform(get("/customers/" + testCustomer.getId()).with(adminUser()))
                .andExpect(status().isOk());
        // LIST - GET /products returns a Page<ProductResponse>.
        mockMvc.perform(get("/products").with(adminUser()))
                .andExpect(status().isOk());
    }

    /** Read workload used by the concurrency test; asserts a successful response. */
    private void readProduct() throws Exception {
        mockMvc.perform(get("/products/" + testProduct.getId()).with(adminUser()))
                .andExpect(status().isOk());
    }

    private void generateDailyReport() throws Exception {
        mockMvc.perform(get("/reports/daily-sales")
                        .param("date", LocalDate.now().toString())
                        .with(adminUser()))
                .andExpect(status().isOk());
    }

    private void generateSingleLabel(Long orderId) throws Exception {
        mockMvc.perform(get("/dispatch/labels/" + orderId).with(adminUser()))
                .andExpect(status().isOk());
    }

    // ==================== TIMING HELPERS ====================

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Runs the given action and returns its wall-clock duration in milliseconds. */
    private long measureMillis(ThrowingRunnable action) throws Exception {
        long startNanos = System.nanoTime();
        action.run();
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** Computes the given percentile (0-100) of the supplied samples. */
    private long percentile(List<Long> samples, int percentile) {
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    // ==================== DATA HELPERS ====================

    /**
     * A request post-processor that authenticates as user id "1" with the full
     * set of roles used across the order, product, customer, report and dispatch
     * controllers. Applied per request so it works correctly on the worker
     * threads of the concurrency test.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminUser() {
        return user("1").roles("ADMIN", "TENANT_ADMIN", "MANAGER", "SALESPERSON", "DISPATCHER", "ACCOUNTANT");
    }

    private void cleanData() {
        orderRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        salespersonRepository.deleteAll();
    }

    private void seedData() {
        testProduct = productRepository.save(Product.builder()
                .sku("ASHWA001")
                .name("Ashwagandha Capsules")
                .category("Supplements")
                .description("Premium Ashwagandha extract")
                .salePrice(BigDecimal.valueOf(500.00))
                .mrp(BigDecimal.valueOf(600.00))
                .weightGrams(BigDecimal.valueOf(250))
                .stockQuantity(100000)
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
                        .with(adminUser())
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
                        .with(adminUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk());
    }
}
