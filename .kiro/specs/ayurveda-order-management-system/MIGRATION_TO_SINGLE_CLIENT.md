# Migration from Multi-Tenant to Single-Client Configuration-Based Architecture

## Summary of Changes

This document outlines the architectural changes made to convert the Ayurveda Order & Dispatch Management System from a multi-tenant SaaS platform to a single-client configuration-based application.

## Key Architectural Changes

### 1. Database Architecture

**Before (Multi-Tenant):**
- Master Database: Stored tenant configurations and platform users
- Multiple Tenant Databases: One isolated database per client
- AbstractRoutingDataSource: Dynamic routing based on tenant context
- TenantContext with ThreadLocal storage

**After (Single-Client):**
- Application Database: Stores user accounts, roles, and system configuration
- Business Database: Contains orders, products, customers, and operational data
- Standard Spring Data JPA: No routing needed
- CompanyConfig entity: Stores company-specific settings in database

### 2. Configuration Management

**Before (Multi-Tenant):**
- Tenant entity with database connection details
- Runtime tenant switching via JWT claims
- Per-tenant business rules in database

**After (Single-Client):**
- Configuration file (application.yml): Company details, business rules
- ConfigurationService: Loads and caches settings at startup
- No runtime tenant context switching required

### 3. Authentication & Authorization

**Before (Multi-Tenant):**
- JWT token contains: userId, tenantKey, role, permissions
- TenantContextFilter extracts tenant from JWT
- Roles: SUPER_ADMIN, TENANT_ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT

**After (Single-Client):**
- JWT token contains: userId, role, permissions (no tenant key)
- Standard JwtAuthenticationFilter
- Roles: ADMIN, MANAGER, SALESPERSON, DISPATCHER, ACCOUNTANT

### 4. User Management

**Before (Multi-Tenant):**
- PlatformUser entity in master database
- Salesperson linked to PlatformUser via platformUserId

**After (Single-Client):**
- User entity in application database
- Salesperson linked to User via userId
- Simplified user management (no cross-tenant concerns)

### 5. Data Isolation

**Before (Multi-Tenant):**
- Strong data isolation via separate databases
- Tenant context validation on every request
- Cross-tenant access prevention

**After (Single-Client):**
- Single database per deployment
- No tenant isolation needed
- Standard application-level security

## Configuration Structure

### application.yml Example

```yaml
app:
  company:
    name: "Shifa Ayurveda"
    address: "123 Main Street, Mumbai, Maharashtra 400001"
    phone: "+91-9876543210"
    email: "info@shifaayurveda.com"
    logo-path: "/assets/logo.png"
    gstin: "27AAAAA0000A1Z5"
    
  business-rules:
    low-stock-threshold: 10
    order-number-prefix: "ORD"
    default-tax-rate: 18.0
    enable-whatsapp-parsing: true
    enable-storefront: true
    
  security:
    jwt-secret: "${JWT_SECRET}"
    jwt-expiration: 86400000  # 24 hours
    jwt-refresh-expiration: 604800000  # 7 days
```

## Deployment Model

### Before (Multi-Tenant)
- Single deployment serving multiple clients
- Tenant onboarding creates new database
- Shared application server
- Tenant-specific subdomains

### After (Single-Client)
- Separate deployment per client
- Each client has dedicated instance
- Configuration file customized per client
- Client-specific domain

## Migration Benefits

1. **Simplicity**: Removed complexity of tenant routing and context management
2. **Performance**: Eliminated tenant lookup overhead
3. **Maintenance**: Easier to debug and maintain single-client deployments
4. **Customization**: Each client can have custom configurations without affecting others
5. **Deployment**: Standard deployment practices (no multi-tenant concerns)

## Implementation Tasks Removed

The following tasks from the original implementation plan are no longer needed:

1. ~~Multi-tenant database routing with AbstractRoutingDataSource~~
2. ~~TenantContext and ThreadLocal management~~
3. ~~Tenant entity and tenant onboarding~~
4. ~~Cross-tenant data isolation validation~~
5. ~~Tenant-specific datasource configuration~~
6. ~~Tenant context propagation in filters~~

## Implementation Tasks Added

New tasks for configuration-based architecture:

1. Configuration management service to load company settings
2. CompanyConfig entity for database-stored configuration
3. Startup configuration validation
4. Configuration caching for performance

## What Stays The Same

The following features remain unchanged:

- Order management workflow (NEW → CONFIRMED → PAID → PACKED → DISPATCHED → DELIVERED)
- WhatsApp message parsing
- Product and stock management
- Customer management
- Dispatch label generation
- Reporting and analytics
- Vyapar billing export
- Role-based access control (with simplified roles)
- Audit logging
- JWT authentication (without tenant context)

## Next Steps

1. Review updated requirements.md for configuration management (Requirement 17)
2. Review updated design.md for simplified architecture
3. Review updated tasks.md for implementation plan
4. Begin implementation with configuration management setup
5. Follow standard Spring Boot application patterns (no multi-tenancy)

## Questions to Address

Before starting implementation, confirm:

1. Will each client have a separate server/deployment?
2. Should configuration be in files only, or also in database?
3. What configuration items need to be changeable without restart?
4. Do we need to support multiple companies in future? (If yes, reconsider architecture)
