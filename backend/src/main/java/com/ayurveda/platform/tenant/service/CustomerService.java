package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Customer Management Service
 * 
 * Implements Requirements:
 * - 10.1: Create customer with name and phone (required), optional address/email
 * - 10.2: Allow optional address, city, state, pincode, and email
 * - 10.3: Search customer by phone, return matching customer or null
 * - 10.4: Find existing customer by phone or create new customer (WhatsApp orders)
 * - 10.5: Detect potential duplicate customers based on phone number
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    /**
     * Get all customers with pagination.
     * 
     * @param pageable Pagination parameters
     * @return Page of customers
     */
    public Page<Customer> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    /**
     * Get customer by ID.
     * 
     * @param id Customer ID
     * @return Optional containing customer if found
     */
    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    /**
     * Search customer by phone number.
     * 
     * Implements Requirement 10.3:
     * - Search for customer by phone
     * - Return matching customer or null
     * 
     * @param phone Phone number to search
     * @return Optional containing customer if found
     */
    public Optional<Customer> findByPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return Optional.empty();
        }
        
        // Normalize phone number before search
        String normalizedPhone = normalizePhoneNumber(phone);
        return customerRepository.findByPhone(normalizedPhone);
    }

    /**
     * Search customers by name or phone.
     * 
     * @param query Search query
     * @return List of matching customers
     */
    public List<Customer> searchCustomers(String query) {
        return customerRepository.searchByNameOrPhone(query);
    }

    /**
     * Create a new customer.
     * 
     * Implements Requirements 10.1, 10.2:
     * - Require name and phone number (10.1)
     * - Allow optional address, city, state, pincode, and email (10.2)
     * - Detect duplicate phone numbers (10.5)
     * 
     * @param customer Customer to create
     * @return Created customer (or existing if duplicate phone)
     */
    public Customer createCustomer(Customer customer) {
        log.info("Creating customer with name: {} and phone: {}", customer.getName(), customer.getPhone());
        
        // Normalize phone number
        if (customer.getPhone() != null && !customer.getPhone().isEmpty()) {
            customer.setPhone(normalizePhoneNumber(customer.getPhone()));
            
            // Check for duplicate phone (Requirement 10.5)
            Optional<Customer> existing = customerRepository.findByPhone(customer.getPhone());
            if (existing.isPresent()) {
                log.info("Customer with phone {} already exists, returning existing customer", customer.getPhone());
                return existing.get(); // Return existing customer
            }
        }
        
        Customer saved = customerRepository.save(customer);
        log.info("Customer created successfully with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Update an existing customer.
     * 
     * @param id Customer ID
     * @param updated Updated customer data
     * @return Updated customer
     * @throws ResourceNotFoundException if customer not found
     */
    public Customer updateCustomer(Long id, Customer updated) {
        log.info("Updating customer ID: {}", id);
        
        return customerRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            
            // Handle phone number update with normalization
            if (updated.getPhone() != null && !updated.getPhone().isEmpty()) {
                existing.setPhone(normalizePhoneNumber(updated.getPhone()));
            }
            
            existing.setEmail(updated.getEmail());
            existing.setAddressLine1(updated.getAddressLine1());
            existing.setAddressLine2(updated.getAddressLine2());
            existing.setCity(updated.getCity());
            existing.setState(updated.getState());
            existing.setPincode(updated.getPincode());
            existing.setGstin(updated.getGstin());
            
            Customer savedCustomer = customerRepository.save(existing);
            log.info("Customer updated successfully with ID: {}", savedCustomer.getId());
            return savedCustomer;
        }).orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
    }

    /**
     * Delete a customer.
     * 
     * @param id Customer ID
     * @throws ResourceNotFoundException if customer not found
     */
    public void deleteCustomer(Long id) {
        log.info("Deleting customer ID: {}", id);
        
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        
        customerRepository.delete(customer);
        log.info("Customer deleted successfully with ID: {}", id);
    }

    /**
     * Find or create a customer from WhatsApp-parsed data.
     * 
     * Implements Requirement 10.4:
     * - Find existing customer by phone
     * - Create new customer if not found
     * 
     * @param name Customer name
     * @param phone Customer phone
     * @param address Customer address
     * @return Existing or newly created customer
     */
    public Customer findOrCreate(String name, String phone, String address) {
        log.info("Finding or creating customer with phone: {}", phone);
        
        if (phone != null && !phone.isEmpty()) {
            String normalizedPhone = normalizePhoneNumber(phone);
            Optional<Customer> existing = customerRepository.findByPhone(normalizedPhone);
            if (existing.isPresent()) {
                log.info("Found existing customer with ID: {}", existing.get().getId());
                return existing.get();
            }
        }

        // Create new customer
        Customer customer = new Customer();
        customer.setName(name != null && !name.isEmpty() ? name : "Unknown");
        customer.setPhone(phone != null ? normalizePhoneNumber(phone) : null);
        customer.setAddressLine1(address);
        
        Customer saved = customerRepository.save(customer);
        log.info("Created new customer with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Find or create customer for WhatsApp orders.
     * Alternative method that accepts a CustomerInfo object.
     * 
     * Implements Requirement 10.4:
     * - Find existing customer by phone or create new customer if not found
     * 
     * @param name Customer name
     * @param phone Customer phone number
     * @param email Customer email
     * @param address Customer address
     * @param city Customer city
     * @param state Customer state
     * @param pincode Customer pincode
     * @return Existing or newly created customer
     */
    public Customer findOrCreateCustomer(String name, String phone, String email, 
                                         String address, String city, String state, String pincode) {
        log.info("Finding or creating customer with phone: {}", phone);
        
        if (phone != null && !phone.isEmpty()) {
            String normalizedPhone = normalizePhoneNumber(phone);
            Optional<Customer> existing = customerRepository.findByPhone(normalizedPhone);
            if (existing.isPresent()) {
                log.info("Found existing customer with ID: {}", existing.get().getId());
                return existing.get();
            }
        }

        // Create new customer with all available information
        Customer customer = Customer.builder()
                .name(name != null && !name.isEmpty() ? name : "Unknown")
                .phone(phone != null ? normalizePhoneNumber(phone) : null)
                .email(email)
                .addressLine1(address)
                .city(city)
                .state(state)
                .pincode(pincode)
                .build();
        
        Customer saved = customerRepository.save(customer);
        log.info("Created new customer with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Find duplicate customers by phone number.
     * 
     * Implements Requirement 10.5:
     * - Detect potential duplicate customers based on phone number
     * 
     * @param phone Phone number to check
     * @return List of customers with matching phone
     */
    public List<Customer> findDuplicatesByPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return new ArrayList<>();
        }
        
        String normalizedPhone = normalizePhoneNumber(phone);
        Optional<Customer> customer = customerRepository.findByPhone(normalizedPhone);
        
        return customer.map(List::of).orElse(new ArrayList<>());
    }

    /**
     * Get customer order history with metrics.
     * 
     * @param customerId Customer ID
     * @return Customer order history DTO
     * @throws ResourceNotFoundException if customer not found
     */
    public CustomerOrderHistoryDTO getCustomerOrderHistory(Long customerId) {
        log.info("Fetching order history for customer ID: {}", customerId);
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        
        Page<Order> ordersPage = orderRepository.findAllByCustomerId(customerId, 
                org.springframework.data.domain.PageRequest.of(0, 1000));
        List<Order> orders = ordersPage.getContent();
        
        // Requirement 24.1: return all orders for the customer ordered by date descending
        List<OrderSummaryDTO> orderSummaries = orders.stream()
                .sorted(java.util.Comparator
                        .comparing(Order::getOrderDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .reversed())
                .map(this::toOrderSummary)
                .collect(java.util.stream.Collectors.toList());
        
        // Calculate metrics
        long totalOrders = orders.size();
        long deliveredOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .count();
        long cancelledOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.CANCELLED)
                .count();
        long returnedOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.RETURNED)
                .count();
        
        BigDecimal totalAmountSpent = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averageOrderValue = deliveredOrders > 0
                ? totalAmountSpent.divide(BigDecimal.valueOf(deliveredOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        LocalDate firstOrderDate = orders.stream()
                .map(Order::getOrderDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        
        LocalDate lastOrderDate = orders.stream()
                .map(Order::getOrderDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        
        return CustomerOrderHistoryDTO.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .orders(orderSummaries)
                .totalOrders((int) totalOrders)
                .deliveredOrders((int) deliveredOrders)
                .cancelledOrders((int) cancelledOrders)
                .returnedOrders((int) returnedOrders)
                .totalAmountSpent(totalAmountSpent)
                .averageOrderValue(averageOrderValue)
                .firstOrderDate(firstOrderDate)
                .lastOrderDate(lastOrderDate)
                .build();
    }

    /**
     * Calculate customer lifetime value (sum of all delivered orders).
     * 
     * Implements Requirement 24.3:
     * - Calculate customer lifetime value as sum of all DELIVERED order totals
     * 
     * @param customerId Customer ID
     * @return Customer lifetime value
     */
    public BigDecimal getCustomerLifetimeValue(Long customerId) {
        log.info("Calculating lifetime value for customer ID: {}", customerId);
        
        Page<Order> ordersPage = orderRepository.findAllByCustomerId(customerId, 
                org.springframework.data.domain.PageRequest.of(0, 1000));
        
        return ordersPage.getContent().stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get customer lifetime metrics including segmentation.
     * 
     * @param customerId Customer ID
     * @return Customer lifetime metrics
     * @throws ResourceNotFoundException if customer not found
     */
    public CustomerLifetimeMetricsDTO getCustomerLifetimeMetrics(Long customerId) {
        log.info("Fetching lifetime metrics for customer ID: {}", customerId);
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        
        Page<Order> ordersPage = orderRepository.findAllByCustomerId(customerId, 
                org.springframework.data.domain.PageRequest.of(0, 1000));
        List<Order> orders = ordersPage.getContent();
        
        // Calculate metrics
        long totalOrders = orders.size();
        long deliveredOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .count();
        long cancelledOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.CANCELLED)
                .count();
        long returnedOrders = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.RETURNED)
                .count();
        
        BigDecimal lifetimeValue = orders.stream()
                .filter(o -> o.getStatus() == Order.OrderStatus.DELIVERED)
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averageOrderValue = deliveredOrders > 0
                ? lifetimeValue.divide(BigDecimal.valueOf(deliveredOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        LocalDate firstOrderDate = orders.stream()
                .map(Order::getOrderDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        
        LocalDate lastOrderDate = orders.stream()
                .map(Order::getOrderDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
        
        long daysSinceFirstOrder = ChronoUnit.DAYS.between(firstOrderDate, LocalDate.now());
        long daysSinceLastOrder = ChronoUnit.DAYS.between(lastOrderDate, LocalDate.now());
        
        // Determine customer segment
        String segment = determineCustomerSegment(totalOrders, lifetimeValue, daysSinceLastOrder);
        
        return CustomerLifetimeMetricsDTO.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .phone(customer.getPhone())
                .totalOrders((int) totalOrders)
                .deliveredOrders((int) deliveredOrders)
                .cancelledOrders((int) cancelledOrders)
                .returnedOrders((int) returnedOrders)
                .lifetimeValue(lifetimeValue)
                .averageOrderValue(averageOrderValue)
                .daysSinceFirstOrder((int) daysSinceFirstOrder)
                .daysSinceLastOrder((int) daysSinceLastOrder)
                .customerSegment(segment)
                .build();
    }

    /**
     * Map an Order entity to a lightweight OrderSummaryDTO for customer order history.
     *
     * @param order Order entity
     * @return OrderSummaryDTO
     */
    private OrderSummaryDTO toOrderSummary(Order order) {
        return OrderSummaryDTO.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .totalAmount(order.getTotalAmount())
                .build();
    }

    /**
     * Normalize phone number to consistent format.
     * Removes +91, 0 prefix and spaces/dashes.
     * 
     * @param phone Raw phone number
     * @return Normalized phone number (10 digits)
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        
        // Remove all non-digit characters
        String normalized = phone.replaceAll("[^0-9]", "");
        
        // Remove +91 or 91 prefix
        if (normalized.startsWith("91") && normalized.length() == 12) {
            normalized = normalized.substring(2);
        }
        
        // Remove leading 0
        if (normalized.startsWith("0") && normalized.length() == 11) {
            normalized = normalized.substring(1);
        }
        
        return normalized;
    }

    /**
     * Determine customer segment based on order history and value.
     * 
     * @param totalOrders Total number of orders
     * @param lifetimeValue Total lifetime value
     * @param daysSinceLastOrder Days since last order
     * @return Customer segment (NEW, REGULAR, VIP, DORMANT)
     */
    private String determineCustomerSegment(long totalOrders, BigDecimal lifetimeValue, long daysSinceLastOrder) {
        if (totalOrders == 0) {
            return "NEW";
        }
        
        if (daysSinceLastOrder > 180) {
            return "DORMANT";
        }
        
        if (totalOrders >= 10 || lifetimeValue.compareTo(BigDecimal.valueOf(50000)) > 0) {
            return "VIP";
        }
        
        if (totalOrders >= 3) {
            return "REGULAR";
        }
        
        return "NEW";
    }

    // Inner DTOs for customer analytics

    @lombok.Data
    @lombok.Builder
    public static class CustomerOrderHistoryDTO {
        private Long customerId;
        private String customerName;
        private String phone;
        private String email;
        private List<OrderSummaryDTO> orders;
        private Integer totalOrders;
        private Integer deliveredOrders;
        private Integer cancelledOrders;
        private Integer returnedOrders;
        private BigDecimal totalAmountSpent;
        private BigDecimal averageOrderValue;
        private LocalDate firstOrderDate;
        private LocalDate lastOrderDate;
    }

    /**
     * Lightweight summary of an order for customer order history listings.
     */
    @lombok.Data
    @lombok.Builder
    public static class OrderSummaryDTO {
        private Long orderId;
        private String orderNumber;
        private LocalDate orderDate;
        private String status;
        private String paymentStatus;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    @lombok.Builder
    public static class CustomerLifetimeMetricsDTO {
        private Long customerId;
        private String customerName;
        private String phone;
        private Integer totalOrders;
        private Integer deliveredOrders;
        private Integer cancelledOrders;
        private Integer returnedOrders;
        private BigDecimal lifetimeValue;
        private BigDecimal averageOrderValue;
        private Integer daysSinceFirstOrder;
        private Integer daysSinceLastOrder;
        private String customerSegment; // NEW, REGULAR, VIP, DORMANT
    }
}
