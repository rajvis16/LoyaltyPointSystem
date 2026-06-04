package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Product;
import com.mark43.loyalty.domain.service.ProductService;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.interfaces.dto.ProductDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public ProductDTO createProduct(ProductDTO productDTO) {

        if (productDTO == null) {
            throw new IllegalArgumentException("Product payload cannot be null.");
        }

        if (productRepository.findByName(productDTO.getName()).isPresent()) {
            throw new IllegalArgumentException("Product with name '" + productDTO.getName() + "' already exists.");
        }

        log.info("Adding new product to catalog: {}", productDTO.getName());

        Product productEntity = convertToEntity(productDTO);
        Product savedProduct = productRepository.save(productEntity);

        return convertToDto(savedProduct);
    }

    @Override
    public ProductDTO getProductById(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        return convertToDto(product);
    }

    @Override
    public ProductDTO getProductByName(String name) {

        Product product = productRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with name: " + name));

        return convertToDto(product);
    }

    @Override
    public List<ProductDTO> getAllProducts() {

        return productRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {

        if (productDTO == null) {
            throw new IllegalArgumentException("Product update payload cannot be null.");
        }

        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        // Update permitted catalog attributes from incoming DTO boundary
        existingProduct.setName(productDTO.getName());
        existingProduct.setPrice(productDTO.getPrice());

        log.info("Updating product catalog details for ID: {}", productId);

        Product updatedProduct = productRepository.save(existingProduct);

        return convertToDto(updatedProduct);
    }

    @Override
    public void deleteProduct(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        productRepository.delete(product);

        log.info("Removed product ID {} from active catalog", productId);
    }

    private Product convertToEntity(ProductDTO productDTO) {

        Product entity = new Product();

        entity.setName(productDTO.getName());
        entity.setPrice(productDTO.getPrice());

        return entity;
    }

    private ProductDTO convertToDto(Product entity) {

        ProductDTO dto = new ProductDTO();

        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setPrice(entity.getPrice());
        return dto;
    }
}