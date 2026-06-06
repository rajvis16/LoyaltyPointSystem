package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.service.ProductService;
import com.mark43.loyalty.interfaces.dto.ProductDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Log4j2
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * POST /api/v1/products
     * Registers a new product into the active system catalog.
     */
    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody ProductDTO productDTO) {
        log.info("REST request to create product: {}", productDTO.getName());
        ProductDTO createdProduct = productService.createProduct(productDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    /**
     * GET /api/v1/products/{id}
     * Retrieves a single product configuration by its numeric primary ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {
        log.info("REST request to get product by ID: {}", id);
        ProductDTO productDTO = productService.getProductById(id);
        return ResponseEntity.ok(productDTO);
    }

    /**
     * GET /api/v1/products
     * Fetches all registered products currently active in the catalog with pagination boundaries.
     */
    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("REST request to fetch product catalog matrix slice at page: {}, size: {}", page, size);
        List<ProductDTO> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    /**
     * PUT /api/v1/products/{id}
     * Updates the core catalog properties of an existing product entity.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductDTO productDTO) {

        log.info("REST request to update product ID: {}", id);

        // Defensive Programming Check: Ensure URL route parameters match the body payload context
        if (productDTO.getProductId() != null && !id.equals(productDTO.getProductId())) {
            throw new IllegalArgumentException("Path identifier ID does not match the payload parameter context.");
        }

        ProductDTO updatedProduct = productService.updateProduct(id, productDTO);
        return ResponseEntity.ok(updatedProduct);
    }

    /**
     * DELETE /api/v1/products/{id}
     * Permanently removes a product configuration from the active catalog registry.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("REST request to delete product ID: {}", id);
        productService.deleteProduct(id);

        // Aligned: Returns a pristine 204 No Content to match industry standards
        return ResponseEntity.noContent().build();
    }
}