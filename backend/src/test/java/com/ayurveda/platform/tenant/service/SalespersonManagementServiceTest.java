package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.CreateSalespersonRequest;
import com.ayurveda.platform.dto.request.UpdateSalespersonRequest;
import com.ayurveda.platform.dto.response.SalespersonResponse;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.entity.PlatformUser;
import com.ayurveda.platform.master.repository.PlatformUserRepository;
import com.ayurveda.platform.tenant.entity.Salesperson;
import com.ayurveda.platform.tenant.repository.SalespersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SalespersonManagementService.
 * Tests CRUD operations, employee code uniqueness, commission rate validation,
 * and PlatformUser linking with SALESPERSON role.
 * Tests Requirements: 20.1, 20.2, 20.3, 20.4, 20.5
 */
@ExtendWith(MockitoExtension.class)
class SalespersonManagementServiceTest {

    @Mock
    private SalespersonRepository salespersonRepository;

    @Mock
    private PlatformUserRepository platformUserRepository;

    @InjectMocks
    private SalespersonManagementService salespersonManagementService;

    private PlatformUser testPlatformUser;
    private Salesperson testSalesperson;
    private CreateSalespersonRequest createRequest;
    private UpdateSalespersonRequest updateRequest;

    @BeforeEach
    void setUp() {
        // Setup test PlatformUser with SALESPERSON role
        testPlatformUser = new PlatformUser();
        testPlatformUser.setId(1L);
        testPlatformUser.setUsername("salesperson1");
        testPlatformUser.setRole(PlatformUser.UserRole.SALESPERSON);
        testPlatformUser.setIsActive(true);

        // Setup test Salesperson entity
        testSalesperson = Salesperson.builder()
                .id(1L)
                .employeeCode("EMP001")
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .status(Salesperson.SalespersonStatus.ACTIVE)
                .commissionRate(BigDecimal.valueOf(10.5))
                .platformUserId(1L)
                .joiningDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Setup create request
        createRequest = CreateSalespersonRequest.builder()
                .employeeCode("EMP001")
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .platformUserId(1L)
                .commissionRate(BigDecimal.valueOf(10.5))
                .joiningDate(LocalDate.now())
                .status("ACTIVE")
                .build();

        // Setup update request
        updateRequest = UpdateSalespersonRequest.builder()
                .name("John Updated")
                .phone("9876543211")
                .email("john.updated@example.com")
                .commissionRate(BigDecimal.valueOf(15.0))
                .status("ACTIVE")
                .build();
    }

    // ==================== CREATE TESTS ====================

    @Test
    void testCreateSalesperson_Success() {
        // Arrange
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));
        when(salespersonRepository.save(any(Salesperson.class))).thenReturn(testSalesperson);

        // Act - Requirement 20.1: Create salesperson
        SalespersonResponse response = salespersonManagementService.createSalesperson(createRequest);

        // Assert
        assertNotNull(response);
        assertEquals("EMP001", response.getEmployeeCode());
        assertEquals("John Doe", response.getName());
        assertEquals("9876543210", response.getPhone());
        assertEquals("john@example.com", response.getEmail());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(BigDecimal.valueOf(10.5), response.getCommissionRate());
        assertEquals(1L, response.getPlatformUserId());

        verify(salespersonRepository).existsByEmployeeCode("EMP001");
        verify(platformUserRepository).findById(1L);
        verify(salespersonRepository).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_DuplicateEmployeeCode_ThrowsException() {
        // Arrange - Requirement 20.2: Ensure employee code uniqueness
        when(salespersonRepository.existsByEmployeeCode("EMP001")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salespersonManagementService.createSalesperson(createRequest)
        );

        assertTrue(exception.getMessage().contains("Employee code"));
        assertTrue(exception.getMessage().contains("already exists"));
        verify(salespersonRepository).existsByEmployeeCode("EMP001");
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_InvalidCommissionRate_Negative_ThrowsException() {
        // Arrange - Requirement 20.3: Validate commission rate (0-100)
        createRequest.setCommissionRate(BigDecimal.valueOf(-5.0));
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salespersonManagementService.createSalesperson(createRequest)
        );

        assertTrue(exception.getMessage().contains("Commission rate must be between 0 and 100"));
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_InvalidCommissionRate_Above100_ThrowsException() {
        // Arrange - Requirement 20.3: Validate commission rate (0-100)
        createRequest.setCommissionRate(BigDecimal.valueOf(150.0));
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salespersonManagementService.createSalesperson(createRequest)
        );

        assertTrue(exception.getMessage().contains("Commission rate must be between 0 and 100"));
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_ValidCommissionRateBoundaries_Success() {
        // Arrange - Requirement 20.3: Test boundary values 0 and 100
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));
        when(salespersonRepository.save(any(Salesperson.class))).thenReturn(testSalesperson);

        // Test 0%
        createRequest.setCommissionRate(BigDecimal.ZERO);
        SalespersonResponse response1 = salespersonManagementService.createSalesperson(createRequest);
        assertNotNull(response1);

        // Test 100%
        createRequest.setCommissionRate(BigDecimal.valueOf(100));
        createRequest.setEmployeeCode("EMP002");
        SalespersonResponse response2 = salespersonManagementService.createSalesperson(createRequest);
        assertNotNull(response2);

        verify(salespersonRepository, times(2)).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_PlatformUserNotFound_ThrowsException() {
        // Arrange - Requirement 20.5: Link to PlatformUser
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> salespersonManagementService.createSalesperson(createRequest)
        );

        assertTrue(exception.getMessage().contains("PlatformUser"));
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_PlatformUserNotSalespersonRole_ThrowsException() {
        // Arrange - Requirement 20.5: PlatformUser must have SALESPERSON role
        testPlatformUser.setRole(PlatformUser.UserRole.TENANT_ADMIN);
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salespersonManagementService.createSalesperson(createRequest)
        );

        assertTrue(exception.getMessage().contains("SALESPERSON role"));
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_PlatformUserInactive_ThrowsException() {
        // Arrange - Requirement 20.5: PlatformUser must be active
        testPlatformUser.setIsActive(false);
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salespersonManagementService.createSalesperson(createRequest)
        );

        assertTrue(exception.getMessage().contains("not active"));
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testCreateSalesperson_NullCommissionRate_Success() {
        // Arrange - Commission rate is optional
        createRequest.setCommissionRate(null);
        testSalesperson.setCommissionRate(null);
        when(salespersonRepository.existsByEmployeeCode(anyString())).thenReturn(false);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));
        when(salespersonRepository.save(any(Salesperson.class))).thenReturn(testSalesperson);

        // Act
        SalespersonResponse response = salespersonManagementService.createSalesperson(createRequest);

        // Assert
        assertNotNull(response);
        assertNull(response.getCommissionRate());
        verify(salespersonRepository).save(any(Salesperson.class));
    }

    // ==================== UPDATE TESTS ====================

    @Test
    void testUpdateSalesperson_Success() {
        // Arrange - Requirement 20.1: Update salesperson
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(salespersonRepository.save(any(Salesperson.class))).thenReturn(testSalesperson);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        SalespersonResponse response = salespersonManagementService.updateSalesperson(1L, updateRequest);

        // Assert
        assertNotNull(response);
        verify(salespersonRepository).findById(1L);
        verify(salespersonRepository).save(any(Salesperson.class));
    }

    @Test
    void testUpdateSalesperson_NotFound_ThrowsException() {
        // Arrange
        when(salespersonRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> salespersonManagementService.updateSalesperson(999L, updateRequest)
        );

        assertTrue(exception.getMessage().contains("Salesperson"));
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testUpdateSalesperson_InvalidCommissionRate_ThrowsException() {
        // Arrange - Requirement 20.3: Validate commission rate on update
        updateRequest.setCommissionRate(BigDecimal.valueOf(120.0));
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salespersonManagementService.updateSalesperson(1L, updateRequest)
        );

        assertTrue(exception.getMessage().contains("Commission rate must be between 0 and 100"));
        verify(salespersonRepository, never()).save(any(Salesperson.class));
    }

    @Test
    void testUpdateSalesperson_PartialUpdate_Success() {
        // Arrange - Update only some fields
        UpdateSalespersonRequest partialRequest = UpdateSalespersonRequest.builder()
                .name("New Name")
                .build();
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(salespersonRepository.save(any(Salesperson.class))).thenReturn(testSalesperson);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        SalespersonResponse response = salespersonManagementService.updateSalesperson(1L, partialRequest);

        // Assert
        assertNotNull(response);
        verify(salespersonRepository).save(any(Salesperson.class));
    }

    @Test
    void testUpdateSalesperson_StatusChange_Success() {
        // Arrange - Requirement 20.4: Support status changes
        updateRequest.setStatus("INACTIVE");
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(salespersonRepository.save(any(Salesperson.class))).thenReturn(testSalesperson);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        SalespersonResponse response = salespersonManagementService.updateSalesperson(1L, updateRequest);

        // Assert
        assertNotNull(response);
        verify(salespersonRepository).save(any(Salesperson.class));
    }

    // ==================== DELETE TESTS ====================

    @Test
    void testDeleteSalesperson_Success() {
        // Arrange - Requirement 20.1: Delete salesperson
        when(salespersonRepository.existsById(1L)).thenReturn(true);
        doNothing().when(salespersonRepository).deleteById(1L);

        // Act
        salespersonManagementService.deleteSalesperson(1L);

        // Assert
        verify(salespersonRepository).existsById(1L);
        verify(salespersonRepository).deleteById(1L);
    }

    @Test
    void testDeleteSalesperson_NotFound_ThrowsException() {
        // Arrange
        when(salespersonRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> salespersonManagementService.deleteSalesperson(999L)
        );

        assertTrue(exception.getMessage().contains("Salesperson"));
        verify(salespersonRepository, never()).deleteById(anyLong());
    }

    // ==================== RETRIEVAL TESTS ====================

    @Test
    void testGetSalespersonById_Success() {
        // Arrange
        when(salespersonRepository.findById(1L)).thenReturn(Optional.of(testSalesperson));
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        SalespersonResponse response = salespersonManagementService.getSalespersonById(1L);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("EMP001", response.getEmployeeCode());
        assertEquals("salesperson1", response.getPlatformUsername());
        verify(salespersonRepository).findById(1L);
    }

    @Test
    void testGetSalespersonById_NotFound_ThrowsException() {
        // Arrange
        when(salespersonRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> salespersonManagementService.getSalespersonById(999L)
        );
    }

    @Test
    void testGetSalespersonByEmployeeCode_Success() {
        // Arrange - Requirement 20.2: Find by employee code
        when(salespersonRepository.findByEmployeeCode("EMP001")).thenReturn(Optional.of(testSalesperson));
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        SalespersonResponse response = salespersonManagementService.getSalespersonByEmployeeCode("EMP001");

        // Assert
        assertNotNull(response);
        assertEquals("EMP001", response.getEmployeeCode());
        verify(salespersonRepository).findByEmployeeCode("EMP001");
    }

    @Test
    void testGetSalespersonByPlatformUserId_Success() {
        // Arrange - Requirement 20.5: Find by platform user ID
        when(salespersonRepository.findByPlatformUserId(1L)).thenReturn(Optional.of(testSalesperson));
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        SalespersonResponse response = salespersonManagementService.getSalespersonByPlatformUserId(1L);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getPlatformUserId());
        verify(salespersonRepository).findByPlatformUserId(1L);
    }

    @Test
    void testGetAllSalespersons_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Salesperson> salespersons = Arrays.asList(testSalesperson);
        Page<Salesperson> page = new PageImpl<>(salespersons, pageable, 1);
        when(salespersonRepository.findAll(pageable)).thenReturn(page);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        Page<SalespersonResponse> result = salespersonManagementService.getAllSalespersons(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("EMP001", result.getContent().get(0).getEmployeeCode());
        verify(salespersonRepository).findAll(pageable);
    }

    @Test
    void testGetSalespersonsByStatus_Success() {
        // Arrange - Requirement 20.4: Filter by status
        List<Salesperson> salespersons = Arrays.asList(testSalesperson);
        when(salespersonRepository.findByStatus(Salesperson.SalespersonStatus.ACTIVE))
                .thenReturn(salespersons);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        List<SalespersonResponse> result = salespersonManagementService.getSalespersonsByStatus("ACTIVE");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
        verify(salespersonRepository).findByStatus(Salesperson.SalespersonStatus.ACTIVE);
    }

    @Test
    void testGetActiveSalespersons_Success() {
        // Arrange - Requirement 20.4: Get active salespersons
        List<Salesperson> salespersons = Arrays.asList(testSalesperson);
        when(salespersonRepository.findByStatus(Salesperson.SalespersonStatus.ACTIVE))
                .thenReturn(salespersons);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Act
        List<SalespersonResponse> result = salespersonManagementService.getActiveSalespersons();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    void testEmployeeCodeExists_ReturnsTrue() {
        // Arrange - Requirement 20.2: Check employee code existence
        when(salespersonRepository.existsByEmployeeCode("EMP001")).thenReturn(true);

        // Act
        boolean exists = salespersonManagementService.employeeCodeExists("EMP001");

        // Assert
        assertTrue(exists);
        verify(salespersonRepository).existsByEmployeeCode("EMP001");
    }

    @Test
    void testEmployeeCodeExists_ReturnsFalse() {
        // Arrange
        when(salespersonRepository.existsByEmployeeCode("EMP999")).thenReturn(false);

        // Act
        boolean exists = salespersonManagementService.employeeCodeExists("EMP999");

        // Assert
        assertFalse(exists);
        verify(salespersonRepository).existsByEmployeeCode("EMP999");
    }

    @Test
    void testGetSalespersonsByStatus_InvalidStatus_ThrowsException() {
        // Arrange
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> salespersonManagementService.getSalespersonsByStatus("INVALID_STATUS")
        );

        assertTrue(exception.getMessage().contains("Invalid status"));
        verify(salespersonRepository, never()).findByStatus(any());
    }

    @Test
    void testGetSalespersonsByStatus_AllValidStatuses() {
        // Arrange - Requirement 20.4: Test all valid statuses
        List<Salesperson> salespersons = Arrays.asList(testSalesperson);
        when(platformUserRepository.findById(1L)).thenReturn(Optional.of(testPlatformUser));

        // Test ACTIVE
        when(salespersonRepository.findByStatus(Salesperson.SalespersonStatus.ACTIVE))
                .thenReturn(salespersons);
        List<SalespersonResponse> activeResults = salespersonManagementService.getSalespersonsByStatus("ACTIVE");
        assertEquals(1, activeResults.size());

        // Test INACTIVE
        when(salespersonRepository.findByStatus(Salesperson.SalespersonStatus.INACTIVE))
                .thenReturn(salespersons);
        List<SalespersonResponse> inactiveResults = salespersonManagementService.getSalespersonsByStatus("INACTIVE");
        assertEquals(1, inactiveResults.size());

        // Test ON_LEAVE
        when(salespersonRepository.findByStatus(Salesperson.SalespersonStatus.ON_LEAVE))
                .thenReturn(salespersons);
        List<SalespersonResponse> onLeaveResults = salespersonManagementService.getSalespersonsByStatus("ON_LEAVE");
        assertEquals(1, onLeaveResults.size());
    }
}
