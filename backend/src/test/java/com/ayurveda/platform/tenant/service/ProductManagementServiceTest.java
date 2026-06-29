package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.exception.InsufficientStockException;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.Product;
import com.ayurveda.platform.tenant.entity.StockHistory;
import com.ayurveda.platform.tenant.repository.OrderItemRepository;
import com.ayurveda.platform.tenant.repository.ProductRepository;
import com.ayurveda.platform.tenant.repository.StockHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductManagementService stock management functionality.
 * Tests requirements 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 21.1, 21.2, 21.3, 21.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductManagementService Stock Management Tests")
class ProductManagementServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @InjectMocks
    private ProductManagementService productManagementService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .sku("PROD-001")
                .name("Test Product")
                .description("Test Description")
                .mrp(new BigDecimal("100.00"))
                .salePrice(new BigDecimal("80.00"))
                .stockQuantity(50)
                .lowStockThreshold(10)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("Test updateStock with STOCK_IN operation - should increase stock correctly")
    void testUpdateStock_StockIn() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.updateStock(1L, 20, StockHistory.StockOperation.STOCK_IN,
                "PURCHASE", 123L, "Purchase order received", 999L);

        // Assert
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getStockQuantity()).isEqualTo(70); // 50 + 20

        ArgumentCaptor<StockHistory> historyCaptor = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(historyCaptor.capture());
        StockHistory savedHistory = historyCaptor.getValue();
        
        assertThat(savedHistory.getOperation()).isEqualTo(StockHistory.StockOperation.STOCK_IN);
        assertThat(savedHistory.getQuantityBefore()).isEqualTo(50);
        assertThat(savedHistory.getQuantityChanged()).isEqualTo(20);
        assertThat(savedHistory.getQuantityAfter()).isEqualTo(70);
        assertThat(savedHistory.getReferenceType()).isEqualTo("PURCHASE");
        assertThat(savedHistory.getReferenceId()).isEqualTo(123L);
        assertThat(savedHistory.getNotes()).isEqualTo("Purchase order received");
        assertThat(savedHistory.getPerformedBy()).isEqualTo(999L);
    }

    @Test
    @DisplayName("Test updateStock with STOCK_OUT operation - should decrease stock correctly")
    void testUpdateStock_StockOut() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.updateStock(1L, -15, StockHistory.StockOperation.STOCK_OUT,
                "ORDER", 456L, "Order packed", 888L);

        // Assert
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getStockQuantity()).isEqualTo(35); // 50 - 15

        ArgumentCaptor<StockHistory> historyCaptor = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(historyCaptor.capture());
        StockHistory savedHistory = historyCaptor.getValue();
        
        assertThat(savedHistory.getOperation()).isEqualTo(StockHistory.StockOperation.STOCK_OUT);
        assertThat(savedHistory.getQuantityBefore()).isEqualTo(50);
        assertThat(savedHistory.getQuantityChanged()).isEqualTo(-15);
        assertThat(savedHistory.getQuantityAfter()).isEqualTo(35);
    }

    @Test
    @DisplayName("Test updateStock with ADJUSTMENT operation")
    void testUpdateStock_Adjustment() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.updateStock(1L, -5, StockHistory.StockOperation.ADJUSTMENT,
                "ADJUSTMENT", null, "Stock count correction", 777L);

        // Assert
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getStockQuantity()).isEqualTo(45); // 50 - 5
    }

    @Test
    @DisplayName("Test updateStock with RETURN operation - should increase stock")
    void testUpdateStock_Return() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.updateStock(1L, 10, StockHistory.StockOperation.RETURN,
                "ORDER", 789L, "Customer return", 666L);

        // Assert
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getStockQuantity()).isEqualTo(60); // 50 + 10

        ArgumentCaptor<StockHistory> historyCaptor = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(historyCaptor.capture());
        StockHistory savedHistory = historyCaptor.getValue();
        
        assertThat(savedHistory.getOperation()).isEqualTo(StockHistory.StockOperation.RETURN);
    }

    @Test
    @DisplayName("Test updateStock with negative stock - should throw InsufficientStockException")
    void testUpdateStock_NegativeStock() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // Act & Assert
        assertThatThrownBy(() -> 
            productManagementService.updateStock(1L, -60, StockHistory.StockOperation.STOCK_OUT,
                    "ORDER", 111L, "Large order", 555L)
        )
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("Insufficient stock for product 'Test Product'")
        .hasMessageContaining("SKU: PROD-001")
        .hasMessageContaining("Available: 50")
        .hasMessageContaining("Required: 60");

        // Verify that neither product nor stock history were saved
        verify(productRepository, never()).save(any(Product.class));
        verify(stockHistoryRepository, never()).save(any(StockHistory.class));
    }

    @Test
    @DisplayName("Test updateStock with exact stock amount - should result in zero stock")
    void testUpdateStock_ExactAmount() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.updateStock(1L, -50, StockHistory.StockOperation.STOCK_OUT,
                "ORDER", 222L, "Complete stock depletion", 444L);

        // Assert
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Test updateStock with product not found - should throw ResourceNotFoundException")
    void testUpdateStock_ProductNotFound() {
        // Arrange
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> 
            productManagementService.updateStock(999L, 10, StockHistory.StockOperation.STOCK_IN)
        )
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Product")
        .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Test simplified updateStock method without optional parameters")
    void testUpdateStock_Simplified() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.updateStock(1L, 25, StockHistory.StockOperation.STOCK_IN);

        // Assert
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getStockQuantity()).isEqualTo(75); // 50 + 25

        ArgumentCaptor<StockHistory> historyCaptor = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(historyCaptor.capture());
        StockHistory savedHistory = historyCaptor.getValue();
        
        assertThat(savedHistory.getReferenceType()).isNull();
        assertThat(savedHistory.getReferenceId()).isNull();
        assertThat(savedHistory.getNotes()).isNull();
        assertThat(savedHistory.getPerformedBy()).isNull();
    }

    @Test
    @DisplayName("Test getStockHistory - should return history for product")
    void testGetStockHistory() {
        // Arrange
        when(productRepository.existsById(1L)).thenReturn(true);
        
        // Act
        productManagementService.getStockHistory(1L);

        // Assert
        verify(stockHistoryRepository).findByProductId(1L);
    }

    @Test
    @DisplayName("Test getStockHistory with non-existent product - should throw ResourceNotFoundException")
    void testGetStockHistory_ProductNotFound() {
        // Arrange
        when(productRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> 
            productManagementService.getStockHistory(999L)
        )
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Product")
        .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Test getLowStockProducts with custom threshold")
    void testGetLowStockProducts_WithThreshold() {
        // Arrange
        int customThreshold = 20;
        
        // Act
        productManagementService.getLowStockProducts(customThreshold);

        // Assert
        verify(productRepository).findAllByStockQuantityLessThanEqualAndIsActiveTrue(customThreshold);
    }

    @Test
    @DisplayName("Test getLowStockProducts without threshold - uses product's lowStockThreshold")
    void testGetLowStockProducts_Default() {
        // Act
        productManagementService.getLowStockProducts();

        // Assert
        verify(productRepository).findLowStockProducts();
    }

    @Test
    @DisplayName("Test stock history audit trail completeness")
    void testStockHistory_AuditTrailCompleteness() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);
        when(stockHistoryRepository.save(any(StockHistory.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.updateStock(1L, 30, StockHistory.StockOperation.STOCK_IN,
                "PURCHASE", 100L, "Supplier delivery", 200L);

        // Assert
        ArgumentCaptor<StockHistory> historyCaptor = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(historyCaptor.capture());
        StockHistory savedHistory = historyCaptor.getValue();
        
        // Verify all audit fields are captured
        assertThat(savedHistory.getProduct()).isEqualTo(testProduct);
        assertThat(savedHistory.getOperation()).isNotNull();
        assertThat(savedHistory.getQuantityBefore()).isNotNull();
        assertThat(savedHistory.getQuantityChanged()).isNotNull();
        assertThat(savedHistory.getQuantityAfter()).isNotNull();
        assertThat(savedHistory.getReferenceType()).isNotNull();
        assertThat(savedHistory.getReferenceId()).isNotNull();
        assertThat(savedHistory.getNotes()).isNotNull();
        assertThat(savedHistory.getPerformedBy()).isNotNull();
    }
}
