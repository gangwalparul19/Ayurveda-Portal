package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.response.DispatchLabelDTO;
import com.ayurveda.platform.dto.response.LabelProductLineDTO;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.master.service.ConfigurationService;
import com.ayurveda.platform.tenant.entity.Customer;
import com.ayurveda.platform.tenant.entity.Order;
import com.ayurveda.platform.tenant.entity.OrderItem;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.repository.DispatchLabelRepository;
import com.ayurveda.platform.tenant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for prepareLabelData() method in DispatchService.
 * Tests the data preparation for dispatch label PDF generation.
 * 
 * Implements Requirements:
 * - 12.1: Extract order details (number, date)
 * - 12.2: Extract customer shipping information
 * - 12.3: Extract product list with quantities
 * - 12.5: Include vendor information
 */
@ExtendWith(MockitoExtension.class)
class DispatchServiceLabelDataTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DispatchLabelRepository dispatchLabelRepository;

    @Mock
    private ConfigurationService configurationService;

    @InjectMocks
    private DispatchService dispatchService;

    private Order testOrder;
    private Customer testCustomer;
    private List<OrderItem> testOrderItems;

    @BeforeEach
    void setUp() {
        // Set up test customer with complete shipping information
        testCustomer = Customer.builder()
                .id(1L)
                .name("John Doe")
                .phone("9876543210")
                .addressLine1("123 Main Street")
                .addressLine2("Apartment 4B")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .build();

        // Set up test products
        Product product1 = Product.builder()
                .id(1L)
                .sku("AYUR-001")
                .name("Ashwagandha Powder")
                .weightGrams(BigDecimal.valueOf(250))
                .build();

        Product product2 = Product.builder()
                .id(2L)
                .sku("AYUR-002")
                .name("Triphala Tablets")
                .weightGrams(BigDecimal.valueOf(100))
                .build();

        // Set up test order items
        OrderItem item1 = OrderItem.builder()
                .id(1L)
                .product(product1)
                .productNameSnapshot("Ashwagandha Powder")
                .skuSnapshot("AYUR-001")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(200))
                .build();

        OrderItem item2 = OrderItem.builder()
                .id(2L)
                .product(product2)
                .productNameSnapshot("Triphala Tablets")
                .skuSnapshot("AYUR-002")
                .quantity(3)
                .unitPrice(BigDecimal.valueOf(150))
                .build();

        testOrderItems = new ArrayList<>();
        testOrderItems.add(item1);
        testOrderItems.add(item2);

        // Set up test order
        testOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-20250107-0001")
                .orderDate(LocalDate.of(2025, 1, 7))
                .customer(testCustomer)
                .items(testOrderItems)
                .totalAmount(BigDecimal.valueOf(850))
                .paymentMode(Order.PaymentMode.UPI)
                .paymentStatus(Order.PaymentStatus.PAID)
                .status(Order.OrderStatus.PAID)
                .build();

        // Link order items to order
        item1.setOrder(testOrder);
        item2.setOrder(testOrder);

        // Mock configuration service responses
        lenient().when(configurationService.getCompanyName()).thenReturn("Ayurveda Health Products");
        lenient().when(configurationService.getAddress()).thenReturn("456 Business Park, Delhi");
        lenient().when(configurationService.getPhone()).thenReturn("1800-123-4567");
    }

    @Test
    @DisplayName("Should successfully prepare label data with complete information")
    void shouldSuccessfullyPrepareLabelDataWithCompleteInformation() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData, "Label data should not be null");

        // Verify order details (Requirement 12.2)
        assertEquals("ORD-20250107-0001", labelData.getOrderNumber());
        assertEquals(LocalDate.of(2025, 1, 7), labelData.getOrderDate());

        // Verify customer shipping information (Requirement 12.2)
        assertEquals("John Doe", labelData.getCustomerName());
        assertEquals("123 Main Street, Apartment 4B", labelData.getShippingAddress());
        assertEquals("Mumbai", labelData.getCity());
        assertEquals("Maharashtra", labelData.getState());
        assertEquals("400001", labelData.getPincode());
        assertEquals("9876543210", labelData.getPhone());

        // Verify product information (Requirement 12.3)
        assertNotNull(labelData.getProducts());
        assertEquals(2, labelData.getProducts().size());

        LabelProductLineDTO product1 = labelData.getProducts().get(0);
        assertEquals("Ashwagandha Powder", product1.getProductName());
        assertEquals("AYUR-001", product1.getSku());
        assertEquals(2, product1.getQuantity());
        assertEquals(BigDecimal.valueOf(250), product1.getWeight());

        LabelProductLineDTO product2 = labelData.getProducts().get(1);
        assertEquals("Triphala Tablets", product2.getProductName());
        assertEquals("AYUR-002", product2.getSku());
        assertEquals(3, product2.getQuantity());
        assertEquals(BigDecimal.valueOf(100), product2.getWeight());

        // Verify total items calculation
        assertEquals(5, labelData.getTotalItems()); // 2 + 3

        // Verify total weight calculation (2*250 + 3*100 = 500 + 300 = 800)
        assertEquals(0, BigDecimal.valueOf(800).compareTo(labelData.getTotalWeight()));

        // Verify order information
        assertEquals(BigDecimal.valueOf(850), labelData.getOrderAmount());
        assertEquals(Order.PaymentMode.UPI, labelData.getPaymentMode());

        // Verify barcode string (Requirement 12.4)
        assertEquals("ORD-20250107-0001", labelData.getBarcode());

        // Verify vendor information (Requirement 12.5)
        assertEquals("Ayurveda Health Products", labelData.getVendorName());
        assertEquals("456 Business Park, Delhi", labelData.getVendorAddress());
        assertEquals("1800-123-4567", labelData.getVendorPhone());

        // Verify repository interactions
        verify(orderRepository, times(1)).findById(1L);
        verify(configurationService, times(1)).getCompanyName();
        verify(configurationService, times(1)).getAddress();
        verify(configurationService, times(1)).getPhone();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when order not found")
    void shouldThrowResourceNotFoundExceptionWhenOrderNotFound() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
            dispatchService.prepareLabelData(999L),
            "Should throw ResourceNotFoundException for non-existent order"
        );

        verify(orderRepository, times(1)).findById(999L);
        verifyNoInteractions(configurationService);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when customer is null")
    void shouldThrowResourceNotFoundExceptionWhenCustomerIsNull() {
        // Given
        testOrder.setCustomer(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
            dispatchService.prepareLabelData(1L),
            "Should throw ResourceNotFoundException when customer is null"
        );

        verify(orderRepository, times(1)).findById(1L);
        verifyNoInteractions(configurationService);
    }

    @Test
    @DisplayName("Should handle customer with only addressLine1")
    void shouldHandleCustomerWithOnlyAddressLine1() {
        // Given
        testCustomer.setAddressLine2(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals("123 Main Street", labelData.getShippingAddress());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle customer with empty addressLine2")
    void shouldHandleCustomerWithEmptyAddressLine2() {
        // Given
        testCustomer.setAddressLine2("");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals("123 Main Street", labelData.getShippingAddress());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle customer with null optional fields")
    void shouldHandleCustomerWithNullOptionalFields() {
        // Given
        testCustomer.setCity(null);
        testCustomer.setState(null);
        testCustomer.setPincode(null);
        testCustomer.setPhone(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals("", labelData.getCity());
        assertEquals("", labelData.getState());
        assertEquals("", labelData.getPincode());
        assertEquals("", labelData.getPhone());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle order items with null SKU")
    void shouldHandleOrderItemsWithNullSku() {
        // Given
        testOrderItems.get(0).setSkuSnapshot(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(2, labelData.getProducts().size());
        assertEquals("", labelData.getProducts().get(0).getSku());
        assertEquals("AYUR-002", labelData.getProducts().get(1).getSku());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle products with null weight")
    void shouldHandleProductsWithNullWeight() {
        // Given
        testOrderItems.get(0).getProduct().setWeightGrams(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(2, labelData.getProducts().size());
        assertEquals(BigDecimal.ZERO, labelData.getProducts().get(0).getWeight());
        assertEquals(BigDecimal.valueOf(100), labelData.getProducts().get(1).getWeight());
        
        // Total weight should be 0*2 + 100*3 = 300
        assertEquals(0, BigDecimal.valueOf(300).compareTo(labelData.getTotalWeight()));
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle order with single item")
    void shouldHandleOrderWithSingleItem() {
        // Given
        testOrderItems.remove(1); // Keep only one item
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(1, labelData.getProducts().size());
        assertEquals(2, labelData.getTotalItems());
        assertEquals(0, BigDecimal.valueOf(500).compareTo(labelData.getTotalWeight())); // 2 * 250
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle order with many items")
    void shouldHandleOrderWithManyItems() {
        // Given
        for (int i = 3; i <= 10; i++) {
            Product product = Product.builder()
                    .id((long) i)
                    .sku("AYUR-00" + i)
                    .name("Product " + i)
                    .weightGrams(BigDecimal.valueOf(50))
                    .build();

            OrderItem item = OrderItem.builder()
                    .id((long) i)
                    .product(product)
                    .productNameSnapshot("Product " + i)
                    .skuSnapshot("AYUR-00" + i)
                    .quantity(1)
                    .unitPrice(BigDecimal.valueOf(100))
                    .build();
            item.setOrder(testOrder);
            testOrderItems.add(item);
        }
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(10, labelData.getProducts().size());
        assertEquals(13, labelData.getTotalItems()); // 2 + 3 + 8*1
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should calculate correct total items for varying quantities")
    void shouldCalculateCorrectTotalItemsForVaryingQuantities() {
        // Given
        testOrderItems.get(0).setQuantity(10);
        testOrderItems.get(1).setQuantity(25);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(35, labelData.getTotalItems()); // 10 + 25
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should calculate correct total weight for varying quantities and weights")
    void shouldCalculateCorrectTotalWeightForVaryingQuantitiesAndWeights() {
        // Given
        testOrderItems.get(0).setQuantity(5);
        testOrderItems.get(0).getProduct().setWeightGrams(BigDecimal.valueOf(300));
        testOrderItems.get(1).setQuantity(10);
        testOrderItems.get(1).getProduct().setWeightGrams(BigDecimal.valueOf(75));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        // Total weight = 5*300 + 10*75 = 1500 + 750 = 2250
        assertEquals(0, BigDecimal.valueOf(2250).compareTo(labelData.getTotalWeight()));
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle COD payment mode")
    void shouldHandleCodPaymentMode() {
        // Given
        testOrder.setPaymentMode(Order.PaymentMode.COD);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(Order.PaymentMode.COD, labelData.getPaymentMode());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle address with whitespace trimming")
    void shouldHandleAddressWithWhitespaceTrimming() {
        // Given
        testCustomer.setAddressLine1("  123 Main Street  ");
        testCustomer.setAddressLine2("  Apartment 4B  ");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals("123 Main Street, Apartment 4B", labelData.getShippingAddress());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should handle order item without linked product entity")
    void shouldHandleOrderItemWithoutLinkedProductEntity() {
        // Given
        testOrderItems.get(0).setProduct(null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(2, labelData.getProducts().size());
        assertEquals(BigDecimal.ZERO, labelData.getProducts().get(0).getWeight());
        assertEquals("Ashwagandha Powder", labelData.getProducts().get(0).getProductName());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should generate correct barcode string matching order number")
    void shouldGenerateCorrectBarcodeStringMatchingOrderNumber() {
        // Given
        String expectedOrderNumber = "ORD-20250107-0001";
        testOrder.setOrderNumber(expectedOrderNumber);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        assertEquals(expectedOrderNumber, labelData.getBarcode());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should preserve decimal precision in total weight calculation")
    void shouldPreserveDecimalPrecisionInTotalWeightCalculation() {
        // Given
        testOrderItems.get(0).setQuantity(3);
        testOrderItems.get(0).getProduct().setWeightGrams(BigDecimal.valueOf(123.45));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        DispatchLabelDTO labelData = dispatchService.prepareLabelData(1L);

        // Then
        assertNotNull(labelData);
        // Total weight = 3*123.45 + 3*100 = 370.35 + 300 = 670.35
        assertEquals(0, new BigDecimal("670.35").compareTo(labelData.getTotalWeight()));
        assertEquals(2, labelData.getTotalWeight().scale()); // Should have 2 decimal places
        verify(orderRepository, times(1)).findById(1L);
    }
}
