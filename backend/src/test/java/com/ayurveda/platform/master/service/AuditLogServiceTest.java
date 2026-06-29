package com.ayurveda.platform.master.service;

import com.ayurveda.platform.master.entity.AuditLog;
import com.ayurveda.platform.master.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditLogService}.
 * Validates audit recording of significant actions (Requirement 32).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService Tests")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("Core record() persists all provided fields")
    void record_persistsAllFields() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.record(5L, 42L, AuditLogService.PAYMENT_RECORDED,
                "{\"amount\":100}", "10.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(5L);
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getAction()).isEqualTo("PAYMENT_RECORDED");
        assertThat(saved.getDetails()).isEqualTo("{\"amount\":100}");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Convenience record() defaults tenantId and ipAddress to null")
    void record_convenienceDefaultsNulls() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.record(7L, AuditLogService.ORDER_CREATED, "details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getTenantId()).isNull();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getDetails()).isEqualTo("details");
        assertThat(saved.getIpAddress()).isNull();
    }

    @Test
    @DisplayName("Map overload serializes details to JSON")
    void record_mapOverloadSerializesJson() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderNumber", "ORD-1");
        details.put("toStatus", "PACKED");

        auditLogService.record(3L, AuditLogService.ORDER_STATUS_CHANGED, details);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("ORDER_STATUS_CHANGED");
        assertThat(saved.getDetails()).isEqualTo("{\"orderNumber\":\"ORD-1\",\"toStatus\":\"PACKED\"}");
    }

    @Test
    @DisplayName("Map overload with empty/null map stores null details")
    void record_emptyMapStoresNullDetails() {
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditLogService.record(1L, AuditLogService.ORDER_CREATED, (Map<String, Object>) null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getDetails()).isNull();
    }

    @Test
    @DisplayName("Blank action is rejected")
    void record_blankActionRejected() {
        assertThatThrownBy(() -> auditLogService.record(null, null, "  ", "details", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(auditLogRepository);
    }
}
