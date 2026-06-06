package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Product;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.interfaces.dto.ProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductDTO productDto;
    private Product productEntity;

    @BeforeEach
    void setUp() {
        productDto = new ProductDTO(1L, "Tactical Boots", "", new BigDecimal("120.00"));
        productEntity = new Product(10L, "Tactical Boots","",  new BigDecimal("120.00"));
    }

    @Test
    void verifyIfCreateProductSucceedsAndConvertsToSecureDtoWithoutId() {

        when(productRepository.findByName("Tactical Boots")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(productEntity);

        ProductDTO result = productService.createProduct(productDto);

        assertNotNull(result);
        assertEquals("Tactical Boots", result.getName());
        assertEquals(new BigDecimal("120.00"), result.getPrice());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void verifyIfCreateProductThrowsExceptionWhenNameAlreadyExists() {

        when(productRepository.findByName("Tactical Boots")).thenReturn(Optional.of(productEntity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productService.createProduct(productDto)
        );

        assertTrue(exception.getMessage().contains("already exists"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void verifyIfGetProductByIdSucceedsAndMapsToDto() {

        when(productRepository.findById(10L)).thenReturn(Optional.of(productEntity));

        ProductDTO result = productService.getProductById(10L);

        assertNotNull(result);
        assertEquals("Tactical Boots", result.getName());
    }

    @Test
    void verifyIfGetProductByNameSucceedsAndMapsToDto() {

        when(productRepository.findByName("Tactical Boots")).thenReturn(Optional.of(productEntity));

        ProductDTO result = productService.getProductByName("Tactical Boots");

        assertNotNull(result);
        assertEquals("Tactical Boots", result.getName());
    }

    @Test
    void verifyIfGetAllProductsReturnsCompleteDtoCollection() {

        Product radio = new Product(11L, "Radio Set", "", new BigDecimal("450.00"));
        when(productRepository.findAll()).thenReturn(Arrays.asList(productEntity, radio));

        List<ProductDTO> results = productService.getAllProducts();

        assertEquals(2, results.size());
        assertEquals("Tactical Boots", results.get(0).getName());
        assertEquals("Radio Set", results.get(1).getName());
    }

    @Test
    void verifyIfUpdateProductSucceedsWhenModifyingPriceAndName() {

        ProductDTO updateDetails = new ProductDTO(2L, "Boots Enhanced", "", new BigDecimal("135.50"));
        when(productRepository.findById(10L)).thenReturn(Optional.of(productEntity));
        when(productRepository.save(any(Product.class))).thenReturn(productEntity);

        ProductDTO result = productService.updateProduct(10L, updateDetails);

        assertNotNull(result);
        assertEquals("Boots Enhanced", productEntity.getName());
        assertEquals(new BigDecimal("135.50"), productEntity.getPrice());
        verify(productRepository, times(1)).save(productEntity);
    }

    @Test
    void verifyIfDeleteProductSucceedsWhenIdIsValid() {

        when(productRepository.findById(10L)).thenReturn(Optional.of(productEntity));

        productService.deleteProduct(10L);

        verify(productRepository, times(1)).delete(productEntity);
    }
}