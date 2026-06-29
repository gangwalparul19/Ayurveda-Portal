package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.BulkStatusUpdateRequest;
import com.ayurveda.platform.dto.response.OrderResponse;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.service.OrderService;
import com.ayurveda.platform.util.WhatsAppTextParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderController bulk status endpoint (POST /orders/bulk/status).
 *
 * Focuses on the per-order success/failure breakdown the controller builds from
 * the index-aligned list returned by {@link OrderService#bulkUpdateStatus}.
 *
 * Tests Requirement 23: Bulk Order Operations (23.3)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Order Controller - Bulk Operations Tests")
class OrderControllerBulkOperationsTest {

    @Mock
    private OrderService orderService;

    @Mock
    private WhatsAppTextParser whatsAppParser;

    private OrderController orderController;

    @BeforeEach
    void setUp() {
        orderController = new OrderController(orderService, whatsAppParser);
    }

    private BulkStatusUpdateRequest request(List<Long> ids) {
        BulkStatusUpdateRequest req = new BulkStatusUpdateRequest();
        req.setOrderIds(ids);
        req.setTargetStatus(Order.OrderStatus.CONFIRMED);
        req.setNotes("Bulk confirm");
        return req;
    }

    @SuppressWarnings("unchecked")
    private List<Long> asLongList(Object value) {
        return (List<Long>) value;
    }

    private OrderResponse responseFor(Long id) {
        return OrderResponse.builder()
                .id(id)
                .orderNumber("ORD-000" + id)
                .status(Order.OrderStatus.CONFIRMED)
                .build();
    }

    @Test
    @DisplayName("All-success breakdown reports every order as succeeded")
    void bulkUpdateStatus_allSuccess_breakdown() {
        List<Long> ids = List.of(1L, 2L, 3L);
        when(orderService.bulkUpdateStatus(eq(ids), eq(Order.OrderStatus.CONFIRMED), anyString(), any()))
                .thenReturn(List.of(responseFor(1L), responseFor(2L), responseFor(3L)));

        ResponseEntity<Map<String, Object>> response =
                orderController.bulkUpdateStatus(request(ids), null);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("successCount")).isEqualTo(3);
        assertThat(body.get("failureCount")).isEqualTo(0);
        assertThat(asLongList(body.get("succeededOrderIds"))).containsExactly(1L, 2L, 3L);
        assertThat(asLongList(body.get("failedOrderIds"))).isEmpty();
        assertThat((List<?>) body.get("results")).hasSize(3);
    }

    @Test
    @DisplayName("Partial-success breakdown splits succeeded and failed order IDs by index")
    void bulkUpdateStatus_partialSuccess_breakdown() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        // Index-aligned results: order 2 and 3 failed (null entries)
        when(orderService.bulkUpdateStatus(eq(ids), eq(Order.OrderStatus.CONFIRMED), anyString(), any()))
                .thenReturn(Arrays.asList(responseFor(1L), null, null, responseFor(4L)));

        ResponseEntity<Map<String, Object>> response =
                orderController.bulkUpdateStatus(request(ids), null);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("successCount")).isEqualTo(2);
        assertThat(body.get("failureCount")).isEqualTo(2);
        assertThat(asLongList(body.get("succeededOrderIds"))).containsExactly(1L, 4L);
        assertThat(asLongList(body.get("failedOrderIds"))).containsExactly(2L, 3L);
        assertThat((List<?>) body.get("results")).hasSize(2);
    }
}
