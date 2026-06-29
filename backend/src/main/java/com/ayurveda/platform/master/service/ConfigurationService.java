package com.ayurveda.platform.master.service;

import com.ayurveda.platform.master.entity.CompanyConfig;
import com.ayurveda.platform.master.repository.CompanyConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Configuration Management Service
 * 
 * Loads and caches company configuration from database on startup.
 * Provides getter methods for all configuration values.
 * Supports both file-based (application.yml) and database configuration.
 * 
 * Requirements: 17.1, 17.2, 17.3, 17.4, 17.5
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {

    private final CompanyConfigRepository companyConfigRepository;

    // In-memory cache for configuration (Requirement 17.5 - cache for performance)
    private CompanyConfig cachedConfig;

    /**
     * Initialize configuration on startup.
     * Validates configuration and loads it into memory cache.
     * Fails fast if configuration is invalid (Requirement 17.5).
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing Configuration Service...");
        
        try {
            loadConfiguration();
            validateConfiguration();
            log.info("Configuration Service initialized successfully");
            log.info("Company: {}", cachedConfig.getCompanyName());
            log.info("Order Number Prefix: {}", cachedConfig.getOrderNumberPrefix());
            log.info("Low Stock Threshold: {}", cachedConfig.getLowStockThreshold());
            log.info("Default Tax Rate: {}%", cachedConfig.getDefaultTaxRate());
        } catch (Exception e) {
            log.error("Failed to initialize Configuration Service", e);
            throw new IllegalStateException("Configuration validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load configuration from database.
     * Creates default configuration if none exists.
     */
    private void loadConfiguration() {
        Optional<CompanyConfig> configOptional = companyConfigRepository.findFirstByOrderByIdAsc();
        
        if (configOptional.isPresent()) {
            cachedConfig = configOptional.get();
            log.info("Loaded configuration from database (ID: {})", cachedConfig.getId());
        } else {
            log.warn("No configuration found in database, creating default configuration");
            cachedConfig = createDefaultConfiguration();
            cachedConfig = companyConfigRepository.save(cachedConfig);
            log.info("Default configuration created with ID: {}", cachedConfig.getId());
        }
    }

    /**
     * Validate configuration on startup (Requirement 17.5).
     * Ensures all required fields are present and valid.
     */
    private void validateConfiguration() {
        if (cachedConfig == null) {
            throw new IllegalStateException("Configuration is null");
        }

        // Validate required company details (Requirement 17.1)
        if (cachedConfig.getCompanyName() == null || cachedConfig.getCompanyName().trim().isEmpty()) {
            throw new IllegalStateException("Company name is required in configuration");
        }

        // Validate business rules (Requirement 17.2)
        if (cachedConfig.getLowStockThreshold() == null || cachedConfig.getLowStockThreshold() < 0) {
            throw new IllegalStateException("Low stock threshold must be a non-negative integer");
        }

        if (cachedConfig.getOrderNumberPrefix() == null || cachedConfig.getOrderNumberPrefix().trim().isEmpty()) {
            throw new IllegalStateException("Order number prefix is required in configuration");
        }

        // Validate tax rates (Requirement 17.2)
        validateTaxRate(cachedConfig.getDefaultTaxRate(), "Default tax rate");
        validateTaxRate(cachedConfig.getCgstRate(), "CGST rate");
        validateTaxRate(cachedConfig.getSgstRate(), "SGST rate");
        validateTaxRate(cachedConfig.getIgstRate(), "IGST rate");

        // Validate other business rules
        if (cachedConfig.getDuplicateCheckDays() == null || cachedConfig.getDuplicateCheckDays() < 1) {
            throw new IllegalStateException("Duplicate check days must be at least 1");
        }

        if (cachedConfig.getFuzzyMatchThreshold() == null || 
            cachedConfig.getFuzzyMatchThreshold().compareTo(BigDecimal.ZERO) < 0 ||
            cachedConfig.getFuzzyMatchThreshold().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("Fuzzy match threshold must be between 0.0 and 1.0");
        }

        log.info("Configuration validation completed successfully");
    }

    /**
     * Validate a tax rate value.
     */
    private void validateTaxRate(BigDecimal taxRate, String rateName) {
        if (taxRate == null) {
            throw new IllegalStateException(rateName + " is required in configuration");
        }
        if (taxRate.compareTo(BigDecimal.ZERO) < 0 || taxRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException(rateName + " must be between 0 and 100");
        }
    }

    /**
     * Create default configuration with sensible defaults.
     */
    private CompanyConfig createDefaultConfiguration() {
        return CompanyConfig.builder()
                .companyName("Ayurveda Company")
                .address("Not Configured")
                .phone("0000000000")
                .email("info@ayurveda.com")
                .logoPath(null)
                .gstin(null)
                .lowStockThreshold(10)
                .orderNumberPrefix("ORD")
                .defaultTaxRate(BigDecimal.valueOf(18.0))
                .cgstRate(BigDecimal.valueOf(9.0))
                .sgstRate(BigDecimal.valueOf(9.0))
                .igstRate(BigDecimal.valueOf(18.0))
                .enableWhatsappParsing(true)
                .enableStorefront(true)
                .defaultShippingCharge(BigDecimal.ZERO)
                .minimumOrderValue(BigDecimal.ZERO)
                .duplicateCheckDays(7)
                .fuzzyMatchThreshold(BigDecimal.valueOf(0.6))
                .build();
    }

    /**
     * Reload configuration from database.
     * Call this after configuration updates to refresh cache.
     */
    public void reloadConfiguration() {
        log.info("Reloading configuration from database...");
        loadConfiguration();
        validateConfiguration();
        log.info("Configuration reloaded successfully");
    }

    /**
     * Get the complete cached configuration.
     * @return Current company configuration
     */
    public CompanyConfig getConfiguration() {
        if (cachedConfig == null) {
            throw new IllegalStateException("Configuration not initialized");
        }
        return cachedConfig;
    }

    // ========== Company Details Getters (Requirement 17.1) ==========

    public String getCompanyName() {
        return getConfiguration().getCompanyName();
    }

    public String getAddress() {
        return getConfiguration().getAddress();
    }

    public String getPhone() {
        return getConfiguration().getPhone();
    }

    public String getEmail() {
        return getConfiguration().getEmail();
    }

    public String getLogoPath() {
        return getConfiguration().getLogoPath();
    }

    public String getGstin() {
        return getConfiguration().getGstin();
    }

    // ========== Business Rules Getters (Requirement 17.2) ==========

    public Integer getLowStockThreshold() {
        return getConfiguration().getLowStockThreshold();
    }

    public String getOrderNumberPrefix() {
        return getConfiguration().getOrderNumberPrefix();
    }

    public BigDecimal getDefaultTaxRate() {
        return getConfiguration().getDefaultTaxRate();
    }

    public BigDecimal getCgstRate() {
        return getConfiguration().getCgstRate();
    }

    public BigDecimal getSgstRate() {
        return getConfiguration().getSgstRate();
    }

    public BigDecimal getIgstRate() {
        return getConfiguration().getIgstRate();
    }

    // ========== Feature Toggles ==========

    public Boolean isWhatsappParsingEnabled() {
        return getConfiguration().getEnableWhatsappParsing();
    }

    public Boolean isStorefrontEnabled() {
        return getConfiguration().getEnableStorefront();
    }

    // ========== Additional Business Rules ==========

    public BigDecimal getDefaultShippingCharge() {
        return getConfiguration().getDefaultShippingCharge();
    }

    public BigDecimal getMinimumOrderValue() {
        return getConfiguration().getMinimumOrderValue();
    }

    public Integer getDuplicateCheckDays() {
        return getConfiguration().getDuplicateCheckDays();
    }

    public BigDecimal getFuzzyMatchThreshold() {
        return getConfiguration().getFuzzyMatchThreshold();
    }

    // ========== Terms and Conditions ==========

    public String getTermsAndConditions() {
        return getConfiguration().getTermsAndConditions();
    }

    public String getBankDetails() {
        return getConfiguration().getBankDetails();
    }

    // ========== Configuration Management ==========

    /**
     * Update configuration in database and refresh cache.
     * @param updatedConfig The updated configuration
     * @param userId User ID performing the update
     * @return Updated configuration
     */
    public CompanyConfig updateConfiguration(CompanyConfig updatedConfig, Long userId) {
        log.info("Updating configuration (User ID: {})", userId);
        
        updatedConfig.setLastUpdatedBy(userId);
        CompanyConfig saved = companyConfigRepository.save(updatedConfig);
        
        // Reload and validate new configuration
        reloadConfiguration();
        
        log.info("Configuration updated successfully");
        return saved;
    }

    /**
     * Check if configuration exists in database.
     * @return true if configuration exists
     */
    public boolean configurationExists() {
        return companyConfigRepository.existsByIdIsNotNull();
    }
}
