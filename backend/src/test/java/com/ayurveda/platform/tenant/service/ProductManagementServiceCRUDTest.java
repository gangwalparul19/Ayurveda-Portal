package com.ayurveda.platform.tenant.service;

import com.ayurveda.platform.dto.request.CreateProductRequest;
import com.ayurveda.platform.dto.request.UpdateProductRequest;
import com.ayurveda.platform.dto.response.ProductResponse;
import com.ayurveda.platform.exception.ResourceNotFoundException;
import com.ayurveda.platform.tenant.entity.Product;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductManagementService CRUD operations.
 * Tests requirements 8.1, 8.2, 8.3, 8.4, 8.5 for task 6.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductManagementService CRUD Tests")
class ProductManagementServiceCRUDTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private StockHistoryRepository stockHistoryRepository;

    @InjectMocks
    private ProductManagementService productManagementService;

    private Product testProduct;
    private CreateProductRequest createRequest;
    private UpdateProductRequest updateRequest;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
                .id(1L)
                .sku("PROD-001")
                .name("Test Product")
                .description("Test Description")
                .category("Ayurvedic Medicine")
                .mrp(new BigDecimal("100.00"))
                .salePrice(new BigDecimal("80.00"))
                .unit("pcs")
                .weightGrams(new BigDecimal("250.00"))
                .hsnCode("3004")
                .gstRate(new BigDecimal("12.00"))
                .imageUrl("http://example.com/image.jpg")
                .stockQuantity(50)
                .lowStockThreshold(10)
                .isActive(true)
                .build();

        createRequest = CreateProductRequest.builder()
                .sku("PROD-001")
                .name("Test Product")
                .description("Test Description")
                .category("Ayurvedic Medicine")
                .mrp(new BigDecimal("100.00"))
                .salePrice(new BigDecimal("80.00"))
                .unit("pcs")
                .weightGrams(new BigDecimal("250.00"))
                .hsnCode("3004")
                .gstRate(new BigDecimal("12.00"))
                .imageUrl("http://example.com/image.jpg")
                .stockQuantity(50)
                .lowStockThreshold(10)
                .build();

        updateRequest = UpdateProductRequest.builder()
                .name("Updated Product")
                .description("Updated Description")
                .category("Updated Category")
                .mrp(new BigDecimal("120.00"))
                .salePrice(new BigDecimal("95.00"))
                .build();
    }

    // ========== CREATE TESTS ==========

    @Test
    @DisplayName("Test createProduct - should create product successfully with all fields")
    void testCreateProduct_Success() {
        // Arrange
        when(productRepository.existsBySku("PROD-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            p.setId(1L);
            return p;
        });

        // Act
        ProductResponse response = productManagementService.createProduct(createRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo("PROD-001");
        assertThat(response.getName()).isEqualTo("Test Product");
        assertThat(response.getDescription()).isEqualTo("Test Description");
        assertThat(response.getCategory()).isEqualTo("Ayurvedic Medicine");
        assertThat(response.getMrp()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(response.getStockQuantity()).isEqualTo(50);

        // Verify SKU uniqueness check was performed
        verify(productRepository).existsBySku("PROD-001");
        
        // Verify product was saved
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        
        assertThat(savedProduct.getSku()).isEqualTo("PROD-001");
        assertThat(savedProduct.getName()).isEqualTo("Test Product");
        assertThat(savedProduct.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Test createProduct with duplicate SKU - should throw IllegalArgumentException")
    void testCreateProduct_DuplicateSKU() {
        // Arrange: SKU already exists
        when(productRepository.existsBySku("PROD-001")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> productManagementService.createProduct(createRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with SKU already exists")
                .hasMessageContaining("PROD-001");

        // Verify product was not saved
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Test createProduct with minimal required fields - should use defaults")
    void testCreateProduct_MinimalFields() {
        // Arrange
        CreateProductRequest minimalRequest = CreateProductRequest.builder()
                .sku("PROD-MIN")
                .name("Minimal Product")
                .mrp(new BigDecimal("50.00"))
                .salePrice(new BigDecimal("40.00"))
                .build();

        when(productRepository.existsBySku("PROD-MIN")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(i -> {
            Product p = i.getArgument(0);
            p.setId(2L);
            return p;
        });

        // Act
        ProductResponse response = productManagementService.createProduct(minimalRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo("PROD-MIN");
        assertThat(response.getName()).isEqualTo("Minimal Product");

        // Verify defaults were applied
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();
        
        assertThat(savedProduct.getUnit()).isEqualTo("pcs");  // Default unit
        assertThat(savedProduct.getGstRate()).isEqualByComparingTo(BigDecimal.ZERO);  // Default GST
        assertThat(savedProduct.getStockQuantity()).isEqualTo(0);  // Default stock
        assertThat(savedProduct.getLowStockThreshold()).isEqualTo(10);  // Default threshold
        assertThat(savedProduct.getIsActive()).isTrue();  // Default active status
    }

    // ========== UPDATE TESTS ==========

    @Test
    @DisplayName("Test updateProduct - should update only provided fields")
    void testUpdateProduct_Success() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        ProductResponse response = productManagementService.updateProduct(1L, updateRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Updated Product");
        assertThat(response.getDescription()).isEqualTo("Updated Description");
        assertThat(response.getCategory()).isEqualTo("Updated Category");
        assertThat(response.getMrp()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(response.getPrice()).isEqualByComparingTo(new BigDecimal("95.00"));

        // Verify SKU and ID were not changed
        assertThat(response.getSku()).isEqualTo("PROD-001");
        assertThat(response.getId()).isEqualTo(1L);

        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Test updateProduct with non-existent ID - should throw ResourceNotFoundException")
    void testUpdateProduct_NotFound() {
        // Arrange
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productManagementService.updateProduct(999L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining("999");

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Test updateProduct with partial fields - should update only provided fields")
    void testUpdateProduct_PartialUpdate() {
        // Arrange
        UpdateProductRequest partialUpdate = UpdateProductRequest.builder()
                .name("Only Name Changed")
                .build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        ProductResponse response = productManagementService.updateProduct(1L, partialUpdate);

        // Assert
        assertThat(response.getName()).isEqualTo("Only Name Changed");
        // Other fields should remain unchanged
        assertThat(response.getDescription()).isEqualTo("Test Description");
        assertThat(response.getCategory()).isEqualTo("Ayurvedic Medicine");
        assertThat(response.getMrp()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ========== DELETE TESTS ==========

    @Test
    @DisplayName("Test deleteProduct - should soft delete when product not in orders")
    void testDeleteProduct_Success() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(orderItemRepository.existsByProductId(1L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        productManagementService.deleteProduct(1L);

        // Assert
        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product deletedProduct = productCaptor.getValue();
        
        assertThat(deletedProduct.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Test deleteProduct when product is in orders - should throw IllegalStateException")
    void testDeleteProduct_ProductInOrders() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(orderItemRepository.existsByProductId(1L)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> productManagementService.deleteProduct(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot delete product")
                .hasMessageContaining("Test Product")
                .hasMessageContaining("PROD-001")
                .hasMessageContaining("appears in one or more orders");

        // Verify product was not saved (not deleted)
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("Test deleteProduct with non-existent ID - should throw ResourceNotFoundException")
    void testDeleteProduct_NotFound() {
        // Arrange
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productManagementService.deleteProduct(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining("999");
    }

    // ========== RETRIEVAL TESTS ==========

    @Test
    @DisplayName("Test getProductById - should return product")
    void testGetProductById_Success() {
        // Arrange
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        // Act
        ProductResponse response = productManagementService.getProductById(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSku()).isEqualTo("PROD-001");
        assertThat(response.getName()).isEqualTo("Test Product");
    }

    @Test
    @DisplayName("Test getProductById with non-existent ID - should throw ResourceNotFoundException")
    void testGetProductById_NotFound() {
        // Arrange
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productManagementService.getProductById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Test getProductBySku - should return product")
    void testGetProductBySku_Success() {
        // Arrange
        when(productRepository.findBySku("PROD-001")).thenReturn(Optional.of(testProduct));

        // Act
        ProductResponse response = productManagementService.getProductBySku("PROD-001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo("PROD-001");
        assertThat(response.getName()).isEqualTo("Test Product");
    }

    @Test
    @DisplayName("Test getProductBySku with non-existent SKU - should throw ResourceNotFoundException")
    void testGetProductBySku_NotFound() {
        // Arrange
        when(productRepository.findBySku("NON-EXISTENT")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> productManagementService.getProductBySku("NON-EXISTENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining("NON-EXISTENT");
    }

    // ========== SEARCH AND FILTER TESTS ==========

    @Test
    @DisplayName("Test getAllProducts with pagination - should return page of products")
    void testGetAllProducts() {
        // Arrange
        Product product2 = Product.builder()
                .id(2L)
                .sku("PROD-002")
                .name("Product 2")
                .mrp(new BigDecimal("200.00"))
                .salePrice(new BigDecimal("160.00"))
                .stockQuantity(30)
                .isActive(true)
                .build();

        List<Product> products = Arrays.asList(testProduct, product2);
        Page<Product> page = new PageImpl<>(products, PageRequest.of(0, 10), 2);
        
        when(productRepository.findAllByIsActiveTrue(any(Pageable.class))).thenReturn(page);

        // Act
        Page<ProductResponse> response = productManagementService.getAllProducts(PageRequest.of(0, 10));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getContent().get(0).getSku()).isEqualTo("PROD-001");
        assertThat(response.getContent().get(1).getSku()).isEqualTo("PROD-002");
    }

    @Test
    @DisplayName("Test getProductsByCategory - should return products in category")
    void testGetProductsByCategory() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        Page<Product> page = new PageImpl<>(products, PageRequest.of(0, 10), 1);
        
        when(productRepository.findAllByCategoryAndIsActiveTrue(eq("Ayurvedic Medicine"), any(Pageable.class)))
                .thenReturn(page);

        // Act
        Page<ProductResponse> response = productManagementService.getProductsByCategory(
                "Ayurvedic Medicine", PageRequest.of(0, 10));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getCategory()).isEqualTo("Ayurvedic Medicine");
    }

    @Test
    @DisplayName("Test searchProducts - should return matching products")
    void testSearchProducts() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        when(productRepository.searchByNameOrSku("Test")).thenReturn(products);

        // Act
        List<ProductResponse> response = productManagementService.searchProducts("Test");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getName()).contains("Test");
    }

    @Test
    @DisplayName("Test searchProducts by SKU - should return matching products")
    void testSearchProducts_BySku() {
        // Arrange
        List<Product> products = Arrays.asList(testProduct);
        when(productRepository.searchByNameOrSku("PROD-001")).thenReturn(products);

        // Act
        List<ProductResponse> response = productManagementService.searchProducts("PROD-001");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getSku()).isEqualTo("PROD-001");
    }

    @Test
    @DisplayName("Test getAllCategories - should return distinct categories")
    void testGetAllCategories() {
        // Arrange
        List<String> categories = Arrays.asList("Ayurvedic Medicine", "Herbal Supplements", "Health Products");
        when(productRepository.findAllActiveCategories()).thenReturn(categories);

        // Act
        List<String> response = productManagementService.getAllCategories();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response).hasSize(3);
        assertThat(response).containsExactly("Ayurvedic Medicine", "Herbal Supplements", "Health Products");
    }

    @Test
    @DisplayName("Test getLowStockProducts - should return products with low stock")
    void testGetLowStockProducts() {
        // Arrange
        Product lowStockProduct = Product.builder()
                .id(3L)
                .sku("PROD-LOW")
                .name("Low Stock Product")
                .mrp(new BigDecimal("100.00"))
                .salePrice(new BigDecimal("80.00"))
                .stockQuantity(5)
                .lowStockThreshold(10)
                .isActive(true)
                .build();

        List<Product> lowStockProducts = Arrays.asList(lowStockProduct);
        when(productRepository.findLowStockProducts()).thenReturn(lowStockProducts);

        // Act
        List<ProductResponse> response = productManagementService.getLowStockProducts();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getStockQuantity()).isLessThanOrEqualTo(10);
    }
}
