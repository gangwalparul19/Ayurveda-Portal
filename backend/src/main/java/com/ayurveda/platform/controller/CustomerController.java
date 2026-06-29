package com.ayurveda.platform.controller;

import com.ayurveda.platform.dto.request.CreateCustomerRequest;
import com.ayurveda.platform.dto.request.FindOrCreateCustomerRequest;
import com.ayurveda.platform.dto.request.UpdateCustomerRequest;
import com.ayurveda.platform.dto.response.CustomerResponse;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Customer Management REST Controller
 * 
 * Implements customer CRUD operations, search, duplicate detection, and analytics endpoints.
 * 
 * Implements Requirements:
 * - 10: Customer Management (CRUD, search, duplicate detection, analytics)
 * - 19: Role-Based Access Control
 * - 22: Order Filtering and Search
 * - 24: Customer Order History
 * - 31: Data Validation
 */
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Get all customers with pagination and optional filtering.
     * 
     * Implements Requirements:
     * - 10.3: List customers with filtering and pagination
     * - 19.2: Role-based access control (Allow ADMIN, MANAGER, SALESPERSON, ACCOUNTANT)
     * - 22: Order Filtering and Search
     * 
     * @param page Page number (default 0)
     * @param size Page size (default 20)
     * @param city Optional city filter
     * @param state Optional state filter
     * @return Paginated list of customers
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON', 'ACCOUNTANT')")
    public ResponseEntity<Page<CustomerResponse>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state) {
        log.info("Fetching all customers - page: {}, size: {}, city: {}, state: {}", page, size, city, state);
        
        Page<Customer> customers = customerService.getAllCustomers(
                PageRequest.of(page, size, Sort.by("name")));
        
        // Map to response DTOs
        Page<CustomerResponse> response = customers.map(this::mapToResponse);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Search customers by name or phone.
     * 
     * Implements Requirements:
     * - 10.3: Search customers
     * - 19.2: Role-based access control
     * - 22: Order Filtering and Search
     * 
     * @param query Search query
     * @return List of matching customers
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON', 'ACCOUNTANT')")
    public ResponseEntity<List<CustomerResponse>> searchCustomers(@RequestParam("q") String query) {
        log.info("Searching customers with query: {}", query);
        List<Customer> customers = customerService.searchCustomers(query);
        List<CustomerResponse> response = customers.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Get customer by phone number.
     * 
     * Implements Requirements:
     * - 10.2: Get customer by phone
     * - 19.2: Role-based access control
     * 
     * @param phone Phone number to search
     * @return Customer if found, 404 otherwise
     */
    @GetMapping("/phone/{phone}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON', 'ACCOUNTANT')")
    public ResponseEntity<CustomerResponse> getCustomerByPhone(@PathVariable String phone) {
        log.info("Searching customer by phone: {}", phone);
        return customerService.findByPhone(phone)
                .map(this::mapToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get customer by ID.
     * 
     * Implements Requirements:
     * - 10.1: Get customer by ID
     * - 19.2: Role-based access control
     * 
     * @param id Customer ID
     * @return Customer if found, 404 otherwise
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON', 'ACCOUNTANT', 'DISPATCHER')")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable Long id) {
        log.info("Fetching customer by ID: {}", id);
        return customerService.getCustomerById(id)
                .map(this::mapToResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new customer.
     * 
     * Implements Requirements:
     * - 10.1: Create customer (require name and phone)
     * - 10.2: Allow optional fields (address, city, state, pincode, email)
     * - 10.5: Detect duplicate phone numbers
     * - 19.2: Role-based access control (ADMIN, MANAGER, SALESPERSON can create)
     * - 31: Data Validation (phone, email format validation)
     * 
     * @param request Customer creation request with validated data
     * @return Created customer (or existing if duplicate phone)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        log.info("Creating customer: {}", request.getName());
        
        // Map request to entity
        Customer customer = Customer.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .gstin(request.getGstin())
                .build();
        
        Customer created = customerService.createCustomer(customer);
        CustomerResponse response = mapToResponse(created);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update an existing customer.
     * 
     * Implements Requirements:
     * - 10.1: Update customer information
     * - 19.2: Role-based access control
     * - 31: Data Validation
     * 
     * @param id Customer ID
     * @param request Updated customer data with validation
     * @return Updated customer
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request) {
        log.info("Updating customer ID: {}", id);
        
        // Map request to entity
        Customer customer = Customer.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .gstin(request.getGstin())
                .build();
        
        Customer updated = customerService.updateCustomer(id, customer);
        CustomerResponse response = mapToResponse(updated);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a customer.
     * 
     * Implements Requirements:
     * - 10.1: Delete customer
     * - 19.2: Role-based access control (Only ADMIN and MANAGER can delete)
     * 
     * @param id Customer ID
     * @return No content response
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        log.info("Deleting customer ID: {}", id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Find or create customer by phone.
     * 
     * Implements Requirements:
     * - 10.4: Find existing customer by phone or create new customer if not found
     * - 19.2: Role-based access control
     * - 31: Data Validation
     * 
     * @param request Customer data with validated fields
     * @return Existing or newly created customer
     */
    @PostMapping("/find-or-create")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<CustomerResponse> findOrCreateCustomer(
            @Valid @RequestBody FindOrCreateCustomerRequest request) {
        log.info("Finding or creating customer with phone: {}", request.getPhone());
        
        Customer customer = customerService.findOrCreateCustomer(
                request.getName(),
                request.getPhone(),
                request.getEmail(),
                request.getAddress(),
                request.getCity(),
                request.getState(),
                request.getPincode()
        );
        
        CustomerResponse response = mapToResponse(customer);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Find duplicate customers by phone.
     * 
     * Implements Requirements:
     * - 10.5: Detect potential duplicate customers based on phone number
     * - 19.2: Role-based access control
     * 
     * @param phone Phone number to check
     * @return List of duplicate customers
     */
    @GetMapping("/duplicates")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON')")
    public ResponseEntity<List<CustomerResponse>> findDuplicates(@RequestParam String phone) {
        log.info("Finding duplicates for phone: {}", phone);
        List<Customer> duplicates = customerService.findDuplicatesByPhone(phone);
        List<CustomerResponse> response = duplicates.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Get customer order history with metrics.
     * 
     * Implements Requirements:
     * - 24: Customer Order History
     * - 19.2: Role-based access control
     * 
     * @param id Customer ID
     * @return Customer order history DTO with metrics
     */
    @GetMapping("/{id}/orders")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'SALESPERSON', 'ACCOUNTANT')")
    public ResponseEntity<CustomerService.CustomerOrderHistoryDTO> getOrderHistory(@PathVariable Long id) {
        log.info("Fetching order history for customer ID: {}", id);
        return ResponseEntity.ok(customerService.getCustomerOrderHistory(id));
    }
    
    /**
     * Get customer lifetime value.
     * 
     * Implements Requirements:
     * - 24.3: Calculate customer lifetime value as sum of all DELIVERED order totals
     * - 19.2: Role-based access control
     * 
     * @param id Customer ID
     * @return Customer lifetime value
     */
    @GetMapping("/{id}/lifetime-value")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<BigDecimal> getLifetimeValue(@PathVariable Long id) {
        log.info("Fetching lifetime value for customer ID: {}", id);
        return ResponseEntity.ok(customerService.getCustomerLifetimeValue(id));
    }
    
    /**
     * Get customer lifetime metrics including segmentation.
     * 
     * Implements Requirements:
     * - 24: Customer Order History and Metrics
     * - 19.2: Role-based access control
     * 
     * @param id Customer ID
     * @return Customer lifetime metrics DTO with segmentation
     */
    @GetMapping("/{id}/metrics")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN', 'MANAGER', 'ACCOUNTANT')")
    public ResponseEntity<CustomerService.CustomerLifetimeMetricsDTO> getLifetimeMetrics(@PathVariable Long id) {
        log.info("Fetching lifetime metrics for customer ID: {}", id);
        return ResponseEntity.ok(customerService.getCustomerLifetimeMetrics(id));
    }
    
    /**
     * Map Customer entity to CustomerResponse DTO.
     * 
     * @param customer Customer entity
     * @return CustomerResponse DTO
     */
    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .addressLine1(customer.getAddressLine1())
                .addressLine2(customer.getAddressLine2())
                .city(customer.getCity())
                .state(customer.getState())
                .pincode(customer.getPincode())
                .gstin(customer.getGstin())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
