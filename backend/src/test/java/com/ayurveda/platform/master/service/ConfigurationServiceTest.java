package com.ayurveda.platform.master.service;

import com.ayurveda.platform.master.entity.CompanyConfig;
import com.ayurveda.platform.master.repository.CompanyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfigurationService.
 * Tests configuration loading, validation, caching, and getter methods.
 * 
 * Requirements: 17.1, 17.2, 17.3, 17.4, 17.5
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private CompanyConfigRepository companyConfigRepository;

    @InjectMocks
    private ConfigurationService configurationService;

    private CompanyConfig validConfig;

    @BeforeEach
    void setUp() {
        validConfig = CompanyConfig.builder()
                .id(1L)
                .companyName("Test Ayurveda Company")
                .address("123 Test Street, Test City")
                .phone("9876543210")
                .email("test@ayurveda.com")
                .logoPath("/logos/test-logo.png")
                .gstin("29ABCDE1234F1Z5")
                .lowStockThreshold(10)
                .orderNumberPrefix("ORD")
                .defaultTaxRate(BigDecimal.valueOf(18.0))
                .cgstRate(BigDecimal.valueOf(9.0))
                .sgstRate(BigDecimal.valueOf(9.0))
                .igstRate(BigDecimal.valueOf(18.0))
                .enableWhatsappParsing(true)
                .enableStorefront(true)
                .defaultShippingCharge(BigDecimal.valueOf(50.0))
                .minimumOrderValue(BigDecimal.valueOf(100.0))
                .duplicateCheckDays(7)
                .fuzzyMatchThreshold(BigDecimal.valueOf(0.6))
                .termsAndConditions("Test terms and conditions")
                .bankDetails("Test bank details")
                .build();
    }

    // ========== Initialization Tests ==========

    @Test
    void testInitialize_WithExistingConfig_Success() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act
        configurationService.initialize();

        // Assert
        verify(companyConfigRepository, times(1)).findFirstByOrderByIdAsc();
        assertEquals("Test Ayurveda Company", configurationService.getCompanyName());
    }

    @Test
    void testInitialize_WithNoConfig_CreatesDefault() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(companyConfigRepository.save(any(CompanyConfig.class))).thenAnswer(invocation -> {
            CompanyConfig config = invocation.getArgument(0);
            config.setId(1L);
            return config;
        });

        // Act
        configurationService.initialize();

        // Assert
        verify(companyConfigRepository, times(1)).findFirstByOrderByIdAsc();
        verify(companyConfigRepository, times(1)).save(any(CompanyConfig.class));
        assertEquals("Ayurveda Company", configurationService.getCompanyName());
        assertEquals("ORD", configurationService.getOrderNumberPrefix());
    }

    @Test
    void testInitialize_WithInvalidConfig_ThrowsException() {
        // Arrange - Config with null company name
        CompanyConfig invalidConfig = CompanyConfig.builder()
                .id(1L)
                .companyName(null)
                .lowStockThreshold(10)
                .orderNumberPrefix("ORD")
                .defaultTaxRate(BigDecimal.valueOf(18.0))
                .cgstRate(BigDecimal.valueOf(9.0))
                .sgstRate(BigDecimal.valueOf(9.0))
                .igstRate(BigDecimal.valueOf(18.0))
                .duplicateCheckDays(7)
                .fuzzyMatchThreshold(BigDecimal.valueOf(0.6))
                .build();

        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(invalidConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("Company name is required"));
    }

    // ========== Validation Tests ==========

    @Test
    void testValidation_WithEmptyCompanyName_ThrowsException() {
        // Arrange
        CompanyConfig config = CompanyConfig.builder()
                .companyName("")
                .lowStockThreshold(10)
                .orderNumberPrefix("ORD")
                .defaultTaxRate(BigDecimal.valueOf(18.0))
                .cgstRate(BigDecimal.valueOf(9.0))
                .sgstRate(BigDecimal.valueOf(9.0))
                .igstRate(BigDecimal.valueOf(18.0))
                .duplicateCheckDays(7)
                .fuzzyMatchThreshold(BigDecimal.valueOf(0.6))
                .build();

        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(config));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("Company name is required"));
    }

    @Test
    void testValidation_WithNegativeLowStockThreshold_ThrowsException() {
        // Arrange
        validConfig.setLowStockThreshold(-5);
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("Low stock threshold"));
    }

    @Test
    void testValidation_WithEmptyOrderNumberPrefix_ThrowsException() {
        // Arrange
        validConfig.setOrderNumberPrefix("");
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("Order number prefix is required"));
    }

    @Test
    void testValidation_WithInvalidTaxRate_ThrowsException() {
        // Arrange - Tax rate > 100
        validConfig.setDefaultTaxRate(BigDecimal.valueOf(150.0));
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("tax rate"));
    }

    @Test
    void testValidation_WithNegativeTaxRate_ThrowsException() {
        // Arrange
        validConfig.setCgstRate(BigDecimal.valueOf(-5.0));
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("CGST rate"));
    }

    @Test
    void testValidation_WithInvalidDuplicateCheckDays_ThrowsException() {
        // Arrange
        validConfig.setDuplicateCheckDays(0);
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("Duplicate check days"));
    }

    @Test
    void testValidation_WithInvalidFuzzyMatchThreshold_ThrowsException() {
        // Arrange - Threshold > 1.0
        validConfig.setFuzzyMatchThreshold(BigDecimal.valueOf(1.5));
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("Fuzzy match threshold"));
    }

    @Test
    void testValidation_WithNegativeFuzzyMatchThreshold_ThrowsException() {
        // Arrange
        validConfig.setFuzzyMatchThreshold(BigDecimal.valueOf(-0.5));
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.initialize());
        
        assertTrue(exception.getMessage().contains("Fuzzy match threshold"));
    }

    // ========== Company Details Getter Tests (Requirement 17.1) ==========

    @Test
    void testGetCompanyDetails_ReturnsCorrectValues() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        // Act & Assert
        assertEquals("Test Ayurveda Company", configurationService.getCompanyName());
        assertEquals("123 Test Street, Test City", configurationService.getAddress());
        assertEquals("9876543210", configurationService.getPhone());
        assertEquals("test@ayurveda.com", configurationService.getEmail());
        assertEquals("/logos/test-logo.png", configurationService.getLogoPath());
        assertEquals("29ABCDE1234F1Z5", configurationService.getGstin());
    }

    // ========== Business Rules Getter Tests (Requirement 17.2) ==========

    @Test
    void testGetBusinessRules_ReturnsCorrectValues() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        // Act & Assert
        assertEquals(10, configurationService.getLowStockThreshold());
        assertEquals("ORD", configurationService.getOrderNumberPrefix());
        assertEquals(BigDecimal.valueOf(18.0), configurationService.getDefaultTaxRate());
        assertEquals(BigDecimal.valueOf(9.0), configurationService.getCgstRate());
        assertEquals(BigDecimal.valueOf(9.0), configurationService.getSgstRate());
        assertEquals(BigDecimal.valueOf(18.0), configurationService.getIgstRate());
    }

    @Test
    void testGetAdditionalBusinessRules_ReturnsCorrectValues() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        // Act & Assert
        assertEquals(BigDecimal.valueOf(50.0), configurationService.getDefaultShippingCharge());
        assertEquals(BigDecimal.valueOf(100.0), configurationService.getMinimumOrderValue());
        assertEquals(7, configurationService.getDuplicateCheckDays());
        assertEquals(BigDecimal.valueOf(0.6), configurationService.getFuzzyMatchThreshold());
    }

    // ========== Feature Toggle Tests ==========

    @Test
    void testGetFeatureToggles_ReturnsCorrectValues() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        // Act & Assert
        assertTrue(configurationService.isWhatsappParsingEnabled());
        assertTrue(configurationService.isStorefrontEnabled());
    }

    // ========== Terms and Conditions Tests ==========

    @Test
    void testGetTermsAndConditions_ReturnsCorrectValues() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        // Act & Assert
        assertEquals("Test terms and conditions", configurationService.getTermsAndConditions());
        assertEquals("Test bank details", configurationService.getBankDetails());
    }

    // ========== Configuration Update Tests ==========

    @Test
    void testUpdateConfiguration_Success() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        CompanyConfig updatedConfig = CompanyConfig.builder()
                .id(1L)
                .companyName("Updated Company")
                .address("New Address")
                .phone("1234567890")
                .email("updated@ayurveda.com")
                .lowStockThreshold(15)
                .orderNumberPrefix("ORD")
                .defaultTaxRate(BigDecimal.valueOf(18.0))
                .cgstRate(BigDecimal.valueOf(9.0))
                .sgstRate(BigDecimal.valueOf(9.0))
                .igstRate(BigDecimal.valueOf(18.0))
                .duplicateCheckDays(7)
                .fuzzyMatchThreshold(BigDecimal.valueOf(0.6))
                .build();

        when(companyConfigRepository.save(any(CompanyConfig.class))).thenReturn(updatedConfig);
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(updatedConfig));

        // Act
        CompanyConfig result = configurationService.updateConfiguration(updatedConfig, 1L);

        // Assert
        verify(companyConfigRepository, times(1)).save(any(CompanyConfig.class));
        assertEquals("Updated Company", result.getCompanyName());
        assertEquals(1L, result.getLastUpdatedBy());
    }

    @Test
    void testReloadConfiguration_Success() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        CompanyConfig newConfig = CompanyConfig.builder()
                .id(1L)
                .companyName("Reloaded Company")
                .address("Reloaded Address")
                .phone("1111111111")
                .email("reload@ayurveda.com")
                .lowStockThreshold(20)
                .orderNumberPrefix("ORD")
                .defaultTaxRate(BigDecimal.valueOf(18.0))
                .cgstRate(BigDecimal.valueOf(9.0))
                .sgstRate(BigDecimal.valueOf(9.0))
                .igstRate(BigDecimal.valueOf(18.0))
                .duplicateCheckDays(7)
                .fuzzyMatchThreshold(BigDecimal.valueOf(0.6))
                .build();

        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(newConfig));

        // Act
        configurationService.reloadConfiguration();

        // Assert
        assertEquals("Reloaded Company", configurationService.getCompanyName());
        assertEquals(20, configurationService.getLowStockThreshold());
    }

    // ========== Caching Tests (Requirement 17.5) ==========

    @Test
    void testGetConfiguration_ReturnsCachedInstance() {
        // Arrange
        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(validConfig));
        configurationService.initialize();

        // Act - Call multiple times
        CompanyConfig config1 = configurationService.getConfiguration();
        CompanyConfig config2 = configurationService.getConfiguration();
        String name1 = configurationService.getCompanyName();
        String name2 = configurationService.getCompanyName();

        // Assert - Repository called only once (during initialization)
        verify(companyConfigRepository, times(1)).findFirstByOrderByIdAsc();
        assertSame(config1, config2); // Same instance (cached)
        assertEquals(name1, name2);
    }

    @Test
    void testGetConfiguration_BeforeInitialization_ThrowsException() {
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> configurationService.getConfiguration());
        
        assertTrue(exception.getMessage().contains("Configuration not initialized"));
    }

    // ========== Configuration Exists Test ==========

    @Test
    void testConfigurationExists_WhenExists_ReturnsTrue() {
        // Arrange
        when(companyConfigRepository.existsByIdIsNotNull()).thenReturn(true);

        // Act
        boolean exists = configurationService.configurationExists();

        // Assert
        assertTrue(exists);
        verify(companyConfigRepository, times(1)).existsByIdIsNotNull();
    }

    @Test
    void testConfigurationExists_WhenNotExists_ReturnsFalse() {
        // Arrange
        when(companyConfigRepository.existsByIdIsNotNull()).thenReturn(false);

        // Act
        boolean exists = configurationService.configurationExists();

        // Assert
        assertFalse(exists);
        verify(companyConfigRepository, times(1)).existsByIdIsNotNull();
    }

    // ========== Edge Case Tests ==========

    @Test
    void testGetConfiguration_WithBoundaryValues_Success() {
        // Arrange - Config with boundary values
        CompanyConfig boundaryConfig = CompanyConfig.builder()
                .companyName("A") // Minimum length
                .lowStockThreshold(0) // Minimum value
                .orderNumberPrefix("O") // Minimum length
                .defaultTaxRate(BigDecimal.ZERO) // Minimum tax
                .cgstRate(BigDecimal.ZERO)
                .sgstRate(BigDecimal.valueOf(100.0)) // Maximum tax
                .igstRate(BigDecimal.valueOf(100.0))
                .duplicateCheckDays(1) // Minimum days
                .fuzzyMatchThreshold(BigDecimal.ZERO) // Minimum threshold
                .build();

        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(boundaryConfig));

        // Act
        configurationService.initialize();

        // Assert
        assertEquals("A", configurationService.getCompanyName());
        assertEquals(0, configurationService.getLowStockThreshold());
        assertEquals(BigDecimal.ZERO, configurationService.getDefaultTaxRate());
        assertEquals(BigDecimal.valueOf(100.0), configurationService.getSgstRate());
    }

    @Test
    void testGetConfiguration_WithMaxBoundaryValues_Success() {
        // Arrange - Config with maximum boundary values
        CompanyConfig maxConfig = CompanyConfig.builder()
                .companyName("Maximum Company Name")
                .lowStockThreshold(Integer.MAX_VALUE)
                .orderNumberPrefix("MAXPREFIX")
                .defaultTaxRate(BigDecimal.valueOf(100.0))
                .cgstRate(BigDecimal.valueOf(100.0))
                .sgstRate(BigDecimal.valueOf(100.0))
                .igstRate(BigDecimal.valueOf(100.0))
                .duplicateCheckDays(Integer.MAX_VALUE)
                .fuzzyMatchThreshold(BigDecimal.ONE)
                .build();

        when(companyConfigRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(maxConfig));

        // Act
        configurationService.initialize();

        // Assert
        assertEquals(Integer.MAX_VALUE, configurationService.getLowStockThreshold());
        assertEquals(BigDecimal.valueOf(100.0), configurationService.getDefaultTaxRate());
        assertEquals(BigDecimal.ONE, configurationService.getFuzzyMatchThreshold());
    }
}
