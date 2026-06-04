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
     * Registers a new product into the active system catalog.

     * POST /api/v1/products
     * Request Body: Validated ProductDTO payload
     */
    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody ProductDTO productDTO) {

        log.info("REST request to create product: {}", productDTO.getName());
        ProductDTO createdProduct = productService.createProduct(productDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    /**
     * Retrieves a single product configuration by its primary database ID.

     * GET /api/v1/products/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable Long id) {

        log.info("REST request to get product by ID: {}", id);
        ProductDTO productDTO = productService.getProductById(id);

        return ResponseEntity.ok(productDTO);
    }

    /**
     * Fetches all registered products currently active in the catalog.

     * GET /api/v1/products
     */
    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts() {

        log.info("REST request to fetch entire product catalog");
        List<ProductDTO> products = productService.getAllProducts();

        return ResponseEntity.ok(products);
    }

    /**
     * Updates the core catalog properties of an existing product entity.

     * PUT /api/v1/products/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductDTO productDTO) {

        log.info("REST request to update product ID: {}", id);
        ProductDTO updatedProduct = productService.updateProduct(id, productDTO);
        
        return ResponseEntity.ok(updatedProduct);
    }

    /**
     * Permanently removes a product configuration from the active catalog.

     * DELETE /api/v1/products/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {

        log.info("REST request to delete product ID: {}", id);
        productService.deleteProduct(id);

        return ResponseEntity.ok().build();
    }
}