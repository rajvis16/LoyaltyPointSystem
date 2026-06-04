package com.mark43.loyalty.domain.service;

import com.mark43.loyalty.interfaces.dto.ProductDTO;
import java.util.List;

public interface ProductService {

    /**
     * Registers a new product into the active system catalog using the incoming payload state.
     *
     * @param product The data transfer object holding the new product properties.
     * @return The fully populated ProductDTO matching the committed state.
     */
    ProductDTO createProduct(ProductDTO product);

    /**
     * Retrieves a single product configuration by its primary key database ID.
     *
     * @param productId The unique identifier of the target product.
     * @return The matching ProductDTO payload.
     * @throws IllegalArgumentException if no product exists with the given ID.
     */
    ProductDTO getProductById(Long productId);

    /**
     * Retrieves a single product configuration by its unique catalog name.
     *
     * @param name The name of the target product.
     * @return The matching ProductDTO payload.
     * @throws IllegalArgumentException if no product matches the specified name.
     */
    ProductDTO getProductByName(String name);

    /**
     * Fetches all registered products currently available in the active catalog.
     *
     * @return A list of all active items mapped to ProductDTO formats.
     */
    List<ProductDTO> getAllProducts();

    /**
     * Updates the core properties of an existing catalog item.
     *
     * @param productId The unique identifier of the product being modified.
     * @param productDetails The data transfer object containing the updated parameters.
     * @return The updated ProductDTO snapshot representing the saved database state.
     * @throws IllegalArgumentException if the target product is not found or payload is invalid.
     */
    ProductDTO updateProduct(Long productId, ProductDTO productDetails);

    /**
     * Permanently removes a product from the active database catalog.
     *
     * @param productId The unique identifier of the item to be withdrawn.
     * @throws IllegalArgumentException if no product matches the specified ID.
     */
    void deleteProduct(Long productId);
}