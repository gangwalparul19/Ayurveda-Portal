package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.repository.CustomerRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CustomerService.
 * Tests customer creation and retrieval, phone number search, 
 * findOrCreateCustomer logic, and duplicate detection.
 * 
 * Tests Requirements: 10.1, 10.2, 10.3, 10.4, 10.5
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer testCustomer;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // Setup test customer with all fields
        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .addressLine1("123 Main Street")
                .addressLine2("Apartment 4B")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .gstin("27ABCDE1234F1Z5")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Setup test order
        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderNumber("ORD-20240101-0001");
        testOrder.setCustomer(testCustomer);
        testOrder.setStatus(Order.OrderStatus.DELIVERED);
        testOrder.setTotalAmount(BigDecimal.valueOf(1500.00));
        testOrder.setOrderDate(LocalDate.now().minusDays(10));
    }

    // ==================== CREATE CUSTOMER TESTS ====================

    @Test
    void testCreateCustomer_WithRequiredFields_Success() {
        // Arrange - Requirement 10.1: Create customer with name and phone (required)
        Customer newCustomer = Customer.builder()
                .name("Jane Smith")
                .phone("9123456789")
                .build();

        when(customerRepository.findByPhone("9123456789")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenReturn(newCustomer);

        // Act
        Customer result = customerService.createCustomer(newCustomer);

        // Assert
        assertNotNull(result);
        assertEquals("Jane Smith", result.getName());
        assertEquals("9123456789", result.getPhone());
        verify(customerRepository).findByPhone("9123456789");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testCreateCustomer_WithOptionalFields_Success() {
        // Arrange - Requirement 10.2: Allow optional address, city, state, pincode, and email
        Customer newCustomer = Customer.builder()
                .name("Jane Smith")
                .phone("9123456789")
                .email("jane@example.com")
                .addressLine1("456 Park Avenue")
                .addressLine2("Suite 100")
                .city("Delhi")
                .state("Delhi")
                .pincode("110001")
                .gstin("07XYZAB5678C1D2")
                .build();

        when(customerRepository.findByPhone("9123456789")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenReturn(newCustomer);

        // Act
        Customer result = customerService.createCustomer(newCustomer);

        // Assert
        assertNotNull(result);
        assertEquals("Jane Smith", result.getName());
        assertEquals("9123456789", result.getPhone());
        assertEquals("jane@example.com", result.getEmail());
        assertEquals("456 Park Avenue", result.getAddressLine1());
        assertEquals("Suite 100", result.getAddressLine2());
        assertEquals("Delhi", result.getCity());
        assertEquals("Delhi", result.getState());
        assertEquals("110001", result.getPincode());
        assertEquals("07XYZAB5678C1D2", result.getGstin());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testCreateCustomer_DuplicatePhone_ReturnsExisting() {
        // Arrange - Requirement 10.5: Detect potential duplicate customers based on phone number
        Customer newCustomer = Customer.builder()
                .name("Different Name")
                .phone("9876543210")
                .build();

        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.of(testCustomer));

        // Act
        Customer result = customerService.createCustomer(newCustomer);

        // Assert
        assertNotNull(result);
        assertEquals(testCustomer.getId(), result.getId());
        assertEquals(testCustomer.getName(), result.getName());
        verify(customerRepository).findByPhone("9876543210");
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void testCreateCustomer_PhoneNormalization_RemovesCountryCode() {
        // Arrange - Test phone normalization: +91
        Customer newCustomer = Customer.builder()
                .name("Test User")
                .phone("+919876543210")
                .build();

        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // Act
        Customer result = customerService.createCustomer(newCustomer);

        // Assert
        assertNotNull(result);
        assertEquals("9876543210", result.getPhone());
        verify(customerRepository).findByPhone("9876543210");
    }

    @Test
    void testCreateCustomer_PhoneNormalization_RemovesLeadingZero() {
        // Arrange - Test phone normalization: leading 0
        Customer newCustomer = Customer.builder()
                .name("Test User")
                .phone("09876543210")
                .build();

        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // Act
        Customer result = customerService.createCustomer(newCustomer);

        // Assert
        assertNotNull(result);
        assertEquals("9876543210", result.getPhone());
    }

    @Test
    void testCreateCustomer_PhoneNormalization_RemovesSpacesAndDashes() {
        // Arrange - Test phone normalization: spaces and dashes
        Customer newCustomer = Customer.builder()
                .name("Test User")
                .phone("987-654-3210")
                .build();

        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // Act
        Customer result = customerService.createCustomer(newCustomer);

        // Assert
        assertNotNull(result);
        assertEquals("9876543210", result.getPhone());
    }

    // ==================== RETRIEVE CUSTOMER TESTS ====================

    @Test
    void testGetCustomerById_Success() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        // Act
        Optional<Customer> result = customerService.getCustomerById(1L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testCustomer.getId(), result.get().getId());
        assertEquals(testCustomer.getName(), result.get().getName());
        verify(customerRepository).findById(1L);
    }

    @Test
    void testGetCustomerById_NotFound_ReturnsEmpty() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Customer> result = customerService.getCustomerById(999L);

        // Assert
        assertFalse(result.isPresent());
        verify(customerRepository).findById(999L);
    }

    @Test
    void testGetAllCustomers_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<Customer> customers = Arrays.asList(testCustomer);
        Page<Customer> page = new PageImpl<>(customers, pageable, 1);
        when(customerRepository.findAll(pageable)).thenReturn(page);

        // Act
        Page<Customer> result = customerService.getAllCustomers(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(testCustomer.getName(), result.getContent().get(0).getName());
        verify(customerRepository).findAll(pageable);
    }

    // ==================== PHONE NUMBER SEARCH TESTS ====================

    @Test
    void testFindByPhone_CustomerExists_ReturnsCustomer() {
        // Arrange - Requirement 10.3: Search customer by phone, return matching customer
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.of(testCustomer));

        // Act
        Optional<Customer> result = customerService.findByPhone("9876543210");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testCustomer.getPhone(), result.get().getPhone());
        assertEquals(testCustomer.getName(), result.get().getName());
        verify(customerRepository).findByPhone("9876543210");
    }

    @Test
    void testFindByPhone_CustomerNotFound_ReturnsEmpty() {
        // Arrange - Requirement 10.3: Return null (Optional.empty) when not found
        when(customerRepository.findByPhone("9999999999")).thenReturn(Optional.empty());

        // Act
        Optional<Customer> result = customerService.findByPhone("9999999999");

        // Assert
        assertFalse(result.isPresent());
        verify(customerRepository).findByPhone("9999999999");
    }

    @Test
    void testFindByPhone_NullPhone_ReturnsEmpty() {
        // Arrange
        // Act
        Optional<Customer> result = customerService.findByPhone(null);

        // Assert
        assertFalse(result.isPresent());
        verify(customerRepository, never()).findByPhone(anyString());
    }

    @Test
    void testFindByPhone_EmptyPhone_ReturnsEmpty() {
        // Arrange
        // Act
        Optional<Customer> result = customerService.findByPhone("");

        // Assert
        assertFalse(result.isPresent());
        verify(customerRepository, never()).findByPhone(anyString());
    }

    @Test
    void testFindByPhone_WithCountryCode_Normalizes() {
        // Arrange - Test phone normalization in search
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.of(testCustomer));

        // Act
        Optional<Customer> result = customerService.findByPhone("+919876543210");

        // Assert
        assertTrue(result.isPresent());
        verify(customerRepository).findByPhone("9876543210");
    }

    @Test
    void testSearchCustomers_Success() {
        // Arrange
        List<Customer> customers = Arrays.asList(testCustomer);
        when(customerRepository.searchByNameOrPhone("John")).thenReturn(customers);

        // Act
        List<Customer> result = customerService.searchCustomers("John");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testCustomer.getName(), result.get(0).getName());
        verify(customerRepository).searchByNameOrPhone("John");
    }

    // ==================== FIND OR CREATE CUSTOMER TESTS ====================

    @Test
    void testFindOrCreate_ExistingCustomer_ReturnsExisting() {
        // Arrange - Requirement 10.4: Find existing customer by phone
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.of(testCustomer));

        // Act
        Customer result = customerService.findOrCreate("Different Name", "9876543210", "Different Address");

        // Assert
        assertNotNull(result);
        assertEquals(testCustomer.getId(), result.getId());
        assertEquals(testCustomer.getName(), result.getName());
        assertEquals(testCustomer.getPhone(), result.getPhone());
        verify(customerRepository).findByPhone("9876543210");
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void testFindOrCreate_NewCustomer_CreatesNew() {
        // Arrange - Requirement 10.4: Create new customer if not found
        when(customerRepository.findByPhone("9123456789")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // Act
        Customer result = customerService.findOrCreate("New Customer", "9123456789", "New Address");

        // Assert
        assertNotNull(result);
        assertEquals("New Customer", result.getName());
        assertEquals("9123456789", result.getPhone());
        assertEquals("New Address", result.getAddressLine1());
        verify(customerRepository).findByPhone("9123456789");
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testFindOrCreate_NullName_UsesUnknown() {
        // Arrange - Handle null name gracefully
        when(customerRepository.findByPhone("9123456789")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // Act
        Customer result = customerService.findOrCreate(null, "9123456789", "Address");

        // Assert
        assertNotNull(result);
        assertEquals("Unknown", result.getName());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testFindOrCreate_EmptyName_UsesUnknown() {
        // Arrange - Handle empty name gracefully
        when(customerRepository.findByPhone("9123456789")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // Act
        Customer result = customerService.findOrCreate("", "9123456789", "Address");

        // Assert
        assertNotNull(result);
        assertEquals("Unknown", result.getName());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testFindOrCreateCustomer_WithAllFields_CreatesNew() {
        // Arrange - Requirement 10.4: Create customer with complete information
        when(customerRepository.findByPhone("9123456789")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        // Act
        Customer result = customerService.findOrCreateCustomer(
                "New Customer",
                "9123456789",
                "customer@example.com",
                "123 Street",
                "Pune",
                "Maharashtra",
                "411001"
        );

        // Assert
        assertNotNull(result);
        assertEquals("New Customer", result.getName());
        assertEquals("9123456789", result.getPhone());
        assertEquals("customer@example.com", result.getEmail());
        assertEquals("123 Street", result.getAddressLine1());
        assertEquals("Pune", result.getCity());
        assertEquals("Maharashtra", result.getState());
        assertEquals("411001", result.getPincode());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testFindOrCreateCustomer_ExistingCustomer_ReturnsExisting() {
        // Arrange - Requirement 10.4: Find existing customer
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.of(testCustomer));

        // Act
        Customer result = customerService.findOrCreateCustomer(
                "Different Name",
                "9876543210",
                "different@example.com",
                "Different Address",
                "Different City",
                "Different State",
                "999999"
        );

        // Assert
        assertNotNull(result);
        assertEquals(testCustomer.getId(), result.getId());
        assertEquals(testCustomer.getName(), result.getName());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    // ==================== DUPLICATE DETECTION TESTS ====================

    @Test
    void testFindDuplicatesByPhone_CustomerExists_ReturnsList() {
        // Arrange - Requirement 10.5: Detect potential duplicate customers based on phone number
        when(customerRepository.findByPhone("9876543210")).thenReturn(Optional.of(testCustomer));

        // Act
        List<Customer> result = customerService.findDuplicatesByPhone("9876543210");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testCustomer.getId(), result.get(0).getId());
        verify(customerRepository).findByPhone("9876543210");
    }

    @Test
    void testFindDuplicatesByPhone_CustomerNotFound_ReturnsEmptyList() {
        // Arrange - Requirement 10.5: Return empty list when no duplicates
        when(customerRepository.findByPhone("9999999999")).thenReturn(Optional.empty());

        // Act
        List<Customer> result = customerService.findDuplicatesByPhone("9999999999");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(customerRepository).findByPhone("9999999999");
    }

    @Test
    void testFindDuplicatesByPhone_NullPhone_ReturnsEmptyList() {
        // Arrange
        // Act
        List<Customer> result = customerService.findDuplicatesByPhone(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(customerRepository, never()).findByPhone(anyString());
    }

    @Test
    void testFindDuplicatesByPhone_EmptyPhone_ReturnsEmptyList() {
        // Arrange
        // Act
        List<Customer> result = customerService.findDuplicatesByPhone("");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(customerRepository, never()).findByPhone(anyString());
    }

    // ==================== UPDATE CUSTOMER TESTS ====================

    @Test
    void testUpdateCustomer_Success() {
        // Arrange
        Customer updatedCustomer = Customer.builder()
                .name("John Updated")
                .phone("9111111111")
                .email("john.updated@example.com")
                .addressLine1("456 New Street")
                .city("Delhi")
                .state("Delhi")
                .pincode("110001")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);

        // Act
        Customer result = customerService.updateCustomer(1L, updatedCustomer);

        // Assert
        assertNotNull(result);
        verify(customerRepository).findById(1L);
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void testUpdateCustomer_NotFound_ThrowsException() {
        // Arrange
        Customer updatedCustomer = Customer.builder()
                .name("Updated Name")
                .phone("9111111111")
                .build();

        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> customerService.updateCustomer(999L, updatedCustomer)
        );

        assertTrue(exception.getMessage().contains("Customer"));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void testUpdateCustomer_PhoneNormalization() {
        // Arrange - Test phone normalization during update
        Customer updatedCustomer = Customer.builder()
                .name("John Updated")
                .phone("+919111111111")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Customer result = customerService.updateCustomer(1L, updatedCustomer);

        // Assert
        assertNotNull(result);
        assertEquals("9111111111", result.getPhone());
        verify(customerRepository).save(any(Customer.class));
    }

    // ==================== DELETE CUSTOMER TESTS ====================

    @Test
    void testDeleteCustomer_Success() {
        // Arrange
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        doNothing().when(customerRepository).delete(testCustomer);

        // Act
        customerService.deleteCustomer(1L);

        // Assert
        verify(customerRepository).findById(1L);
        verify(customerRepository).delete(testCustomer);
    }

    @Test
    void testDeleteCustomer_NotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> customerService.deleteCustomer(999L)
        );

        assertTrue(exception.getMessage().contains("Customer"));
        verify(customerRepository, never()).delete(any(Customer.class));
    }

    // ==================== CUSTOMER ORDER HISTORY TESTS ====================

    @Test
    void testGetCustomerOrderHistory_Success() {
        // Arrange
        List<Order> orders = Arrays.asList(testOrder);
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerOrderHistoryDTO result = customerService.getCustomerOrderHistory(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getCustomerId());
        assertEquals("John Doe", result.getCustomerName());
        assertEquals("9876543210", result.getPhone());
        assertEquals(1, result.getTotalOrders());
        assertEquals(1, result.getDeliveredOrders());
        assertEquals(0, result.getCancelledOrders());
        assertEquals(0, result.getReturnedOrders());
        assertEquals(0, BigDecimal.valueOf(1500.00).compareTo(result.getTotalAmountSpent()));
        // Requirement 24.1: full order list is returned
        assertNotNull(result.getOrders());
        assertEquals(1, result.getOrders().size());
        assertEquals("ORD-20240101-0001", result.getOrders().get(0).getOrderNumber());
        assertEquals("DELIVERED", result.getOrders().get(0).getStatus());
        verify(customerRepository).findById(1L);
        verify(orderRepository).findAllByCustomerId(eq(1L), any(Pageable.class));
    }

    @Test
    void testGetCustomerOrderHistory_OrdersSortedByDateDescending() {
        // Arrange - orders supplied in ascending date order
        Order oldest = new Order();
        oldest.setId(10L);
        oldest.setOrderNumber("ORD-OLD");
        oldest.setCustomer(testCustomer);
        oldest.setStatus(Order.OrderStatus.DELIVERED);
        oldest.setTotalAmount(BigDecimal.valueOf(100.00));
        oldest.setOrderDate(LocalDate.now().minusDays(20));

        Order middle = new Order();
        middle.setId(11L);
        middle.setOrderNumber("ORD-MID");
        middle.setCustomer(testCustomer);
        middle.setStatus(Order.OrderStatus.DELIVERED);
        middle.setTotalAmount(BigDecimal.valueOf(200.00));
        middle.setOrderDate(LocalDate.now().minusDays(10));

        Order newest = new Order();
        newest.setId(12L);
        newest.setOrderNumber("ORD-NEW");
        newest.setCustomer(testCustomer);
        newest.setStatus(Order.OrderStatus.DELIVERED);
        newest.setTotalAmount(BigDecimal.valueOf(300.00));
        newest.setOrderDate(LocalDate.now());

        Page<Order> orderPage = new PageImpl<>(Arrays.asList(oldest, middle, newest));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerOrderHistoryDTO result = customerService.getCustomerOrderHistory(1L);

        // Assert - returned ordered by date descending (Requirement 24.1)
        assertNotNull(result.getOrders());
        assertEquals(3, result.getOrders().size());
        assertEquals("ORD-NEW", result.getOrders().get(0).getOrderNumber());
        assertEquals("ORD-MID", result.getOrders().get(1).getOrderNumber());
        assertEquals("ORD-OLD", result.getOrders().get(2).getOrderNumber());
    }

    @Test
    void testGetCustomerOrderHistory_CustomerNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> customerService.getCustomerOrderHistory(999L)
        );

        assertTrue(exception.getMessage().contains("Customer"));
        verify(orderRepository, never()).findAllByCustomerId(anyLong(), any(Pageable.class));
    }

    @Test
    void testGetCustomerOrderHistory_NoOrders_ReturnsZeroMetrics() {
        // Arrange
        Page<Order> emptyPage = new PageImpl<>(Collections.emptyList());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(emptyPage);

        // Act
        CustomerService.CustomerOrderHistoryDTO result = customerService.getCustomerOrderHistory(1L);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalOrders());
        assertEquals(0, result.getDeliveredOrders());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalAmountSpent()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAverageOrderValue()));
        assertNotNull(result.getOrders());
        assertTrue(result.getOrders().isEmpty());
    }

    // ==================== CUSTOMER LIFETIME VALUE TESTS ====================

    @Test
    void testGetCustomerLifetimeValue_Success() {
        // Arrange
        List<Order> orders = Arrays.asList(testOrder);
        Page<Order> orderPage = new PageImpl<>(orders);
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        BigDecimal result = customerService.getCustomerLifetimeValue(1L);

        // Assert
        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(1500.00).compareTo(result));
        verify(orderRepository).findAllByCustomerId(eq(1L), any(Pageable.class));
    }

    @Test
    void testGetCustomerLifetimeValue_OnlyCountsDeliveredOrders() {
        // Arrange - Create orders with different statuses
        Order deliveredOrder = new Order();
        deliveredOrder.setId(1L);
        deliveredOrder.setStatus(Order.OrderStatus.DELIVERED);
        deliveredOrder.setTotalAmount(BigDecimal.valueOf(1000.00));
        deliveredOrder.setOrderDate(LocalDate.now());

        Order cancelledOrder = new Order();
        cancelledOrder.setId(2L);
        cancelledOrder.setStatus(Order.OrderStatus.CANCELLED);
        cancelledOrder.setTotalAmount(BigDecimal.valueOf(500.00));
        cancelledOrder.setOrderDate(LocalDate.now());

        Order newOrder = new Order();
        newOrder.setId(3L);
        newOrder.setStatus(Order.OrderStatus.NEW);
        newOrder.setTotalAmount(BigDecimal.valueOf(300.00));
        newOrder.setOrderDate(LocalDate.now());

        List<Order> orders = Arrays.asList(deliveredOrder, cancelledOrder, newOrder);
        Page<Order> orderPage = new PageImpl<>(orders);
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        BigDecimal result = customerService.getCustomerLifetimeValue(1L);

        // Assert - Only delivered order should count
        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(1000.00).compareTo(result));
    }

    @Test
    void testGetCustomerLifetimeValue_NoDeliveredOrders_ReturnsZero() {
        // Arrange
        Page<Order> emptyPage = new PageImpl<>(Collections.emptyList());
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(emptyPage);

        // Act
        BigDecimal result = customerService.getCustomerLifetimeValue(1L);

        // Assert
        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    // ==================== CUSTOMER LIFETIME METRICS TESTS ====================

    @Test
    void testGetCustomerLifetimeMetrics_Success() {
        // Arrange
        List<Order> orders = Arrays.asList(testOrder);
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getCustomerId());
        assertEquals("John Doe", result.getCustomerName());
        assertEquals("9876543210", result.getPhone());
        assertEquals(1, result.getTotalOrders());
        assertEquals(1, result.getDeliveredOrders());
        assertEquals(0, BigDecimal.valueOf(1500.00).compareTo(result.getLifetimeValue()));
        assertNotNull(result.getCustomerSegment());
        verify(customerRepository).findById(1L);
    }

    @Test
    void testGetCustomerLifetimeMetrics_CustomerNotFound_ThrowsException() {
        // Arrange
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> customerService.getCustomerLifetimeMetrics(999L)
        );

        assertTrue(exception.getMessage().contains("Customer"));
    }

    @Test
    void testGetCustomerOrderHistory_MixedStatuses_ComputesMetrics() {
        // Arrange - mix of DELIVERED, CANCELLED, RETURNED and other statuses
        Order delivered1 = new Order();
        delivered1.setId(20L);
        delivered1.setOrderNumber("ORD-D1");
        delivered1.setStatus(Order.OrderStatus.DELIVERED);
        delivered1.setTotalAmount(BigDecimal.valueOf(1000.00));
        delivered1.setOrderDate(LocalDate.now().minusDays(30));

        Order delivered2 = new Order();
        delivered2.setId(21L);
        delivered2.setOrderNumber("ORD-D2");
        delivered2.setStatus(Order.OrderStatus.DELIVERED);
        delivered2.setTotalAmount(BigDecimal.valueOf(2000.00));
        delivered2.setOrderDate(LocalDate.now().minusDays(5));

        Order cancelled = new Order();
        cancelled.setId(22L);
        cancelled.setOrderNumber("ORD-C1");
        cancelled.setStatus(Order.OrderStatus.CANCELLED);
        cancelled.setTotalAmount(BigDecimal.valueOf(750.00));
        cancelled.setOrderDate(LocalDate.now().minusDays(15));

        Order returned = new Order();
        returned.setId(23L);
        returned.setOrderNumber("ORD-R1");
        returned.setStatus(Order.OrderStatus.RETURNED);
        returned.setTotalAmount(BigDecimal.valueOf(900.00));
        returned.setOrderDate(LocalDate.now().minusDays(40));

        Page<Order> orderPage = new PageImpl<>(Arrays.asList(delivered1, delivered2, cancelled, returned));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerOrderHistoryDTO result = customerService.getCustomerOrderHistory(1L);

        // Assert - counts per status (Requirement 24.2)
        assertEquals(4, result.getTotalOrders());
        assertEquals(2, result.getDeliveredOrders());
        assertEquals(1, result.getCancelledOrders());
        assertEquals(1, result.getReturnedOrders());
        // totalAmountSpent counts DELIVERED only: 1000 + 2000 = 3000
        assertEquals(0, BigDecimal.valueOf(3000.00).compareTo(result.getTotalAmountSpent()));
        // averageOrderValue = 3000 / 2 delivered = 1500
        assertEquals(0, BigDecimal.valueOf(1500.00).compareTo(result.getAverageOrderValue()));
        // first/last order dates span all orders regardless of status
        assertEquals(LocalDate.now().minusDays(40), result.getFirstOrderDate());
        assertEquals(LocalDate.now().minusDays(5), result.getLastOrderDate());
    }

    @Test
    void testGetCustomerLifetimeMetrics_SegmentNew_ZeroOrders() {
        // Arrange - no orders => NEW segment, zero days-since values
        Page<Order> emptyPage = new PageImpl<>(Collections.emptyList());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(emptyPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert
        assertEquals(0, result.getTotalOrders());
        assertEquals("NEW", result.getCustomerSegment());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getLifetimeValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getAverageOrderValue()));
        // With no orders, first/last default to today => 0 days since
        assertEquals(0, result.getDaysSinceFirstOrder());
        assertEquals(0, result.getDaysSinceLastOrder());
    }

    @Test
    void testGetCustomerLifetimeMetrics_SegmentNew_FewRecentOrders() {
        // Arrange - 2 recent orders (< 3) => still NEW
        List<Order> orders = Arrays.asList(
                buildOrder(30L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(2)),
                buildOrder(31L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(1))
        );
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert
        assertEquals(2, result.getTotalOrders());
        assertEquals("NEW", result.getCustomerSegment());
    }

    @Test
    void testGetCustomerLifetimeMetrics_SegmentRegular_ThreeRecentOrders() {
        // Arrange - 3 recent orders, low value => REGULAR (boundary at >= 3)
        List<Order> orders = Arrays.asList(
                buildOrder(40L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(3)),
                buildOrder(41L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(2)),
                buildOrder(42L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(1))
        );
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert
        assertEquals(3, result.getTotalOrders());
        assertEquals("REGULAR", result.getCustomerSegment());
    }

    @Test
    void testGetCustomerLifetimeMetrics_SegmentVip_TenOrders() {
        // Arrange - 10 recent orders => VIP (boundary at >= 10)
        List<Order> orders = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(buildOrder(50L + i, Order.OrderStatus.DELIVERED,
                    BigDecimal.valueOf(100.00), LocalDate.now().minusDays(i + 1)));
        }
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert
        assertEquals(10, result.getTotalOrders());
        assertEquals("VIP", result.getCustomerSegment());
    }

    @Test
    void testGetCustomerLifetimeMetrics_SegmentVip_HighLifetimeValue() {
        // Arrange - few orders but lifetime value > 50000 => VIP
        List<Order> orders = Arrays.asList(
                buildOrder(60L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(30000.00), LocalDate.now().minusDays(2)),
                buildOrder(61L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(30000.00), LocalDate.now().minusDays(1))
        );
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert - 60000 > 50000 threshold despite only 2 orders
        assertEquals(0, BigDecimal.valueOf(60000.00).compareTo(result.getLifetimeValue()));
        assertEquals("VIP", result.getCustomerSegment());
    }

    @Test
    void testGetCustomerLifetimeMetrics_SegmentDormant_OldLastOrder() {
        // Arrange - last order > 180 days ago => DORMANT (takes precedence)
        List<Order> orders = Arrays.asList(
                buildOrder(70L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(400)),
                buildOrder(71L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(200))
        );
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert
        assertEquals("DORMANT", result.getCustomerSegment());
        assertEquals(200, result.getDaysSinceLastOrder());
        assertEquals(400, result.getDaysSinceFirstOrder());
    }

    @Test
    void testGetCustomerLifetimeMetrics_DaysSinceCalculations() {
        // Arrange - verify days-since-first and days-since-last from order dates
        List<Order> orders = Arrays.asList(
                buildOrder(80L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(50)),
                buildOrder(81L, Order.OrderStatus.DELIVERED, BigDecimal.valueOf(1000.00), LocalDate.now().minusDays(7))
        );
        Page<Order> orderPage = new PageImpl<>(orders);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(orderRepository.findAllByCustomerId(eq(1L), any(Pageable.class))).thenReturn(orderPage);

        // Act
        CustomerService.CustomerLifetimeMetricsDTO result = customerService.getCustomerLifetimeMetrics(1L);

        // Assert
        assertEquals(50, result.getDaysSinceFirstOrder());
        assertEquals(7, result.getDaysSinceLastOrder());
    }

    // Helper to build an Order with the given attributes for analytics tests
    private Order buildOrder(Long id, Order.OrderStatus status, BigDecimal amount, LocalDate orderDate) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber("ORD-" + id);
        order.setCustomer(testCustomer);
        order.setStatus(status);
        order.setTotalAmount(amount);
        order.setOrderDate(orderDate);
        return order;
    }

    // Helper method to match Pageable
    private static Pageable eq(Pageable pageable) {
        return any(Pageable.class);
    }

    // Helper method to match Long
    private static Long eq(Long value) {
        return any(Long.class);
    }
}
