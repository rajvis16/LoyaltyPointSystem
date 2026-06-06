package com.mark43.loyalty.interfaces.rest;

import tools.jackson.databind.ObjectMapper;
import com.mark43.loyalty.domain.entity.Product;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.interfaces.dto.ProductDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Ensures isolation using your test properties/database matrix
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    private Product seededProduct;

    @BeforeEach
    void setupCleanSlate() {
        // Enforce true state isolation between test execution runs
        productRepository.deleteAll();

        // Seed a standard base product that update and error scenarios can reliably target
        seededProduct = new Product(null, "Base-Laptop", "Base Laptop Description", new BigDecimal("1200.00"));
        seededProduct = productRepository.save(seededProduct);
    }

    @Test
    void verifyIfCreateProductSucceedsAndStoresProperAttributes() throws Exception {

        ProductDTO newProduct = new ProductDTO(1L, "Tactical Boots","Tactical Boots description", new BigDecimal("150.00"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newProduct)))
                .andExpect(status().isCreated())
                // Verify the outward payload contract matches our design parameters
                .andExpect(jsonPath("$.name", is("Tactical Boots")))
                .andExpect(jsonPath("$.description", is("Tactical Boots description")))
                .andExpect(jsonPath("$.price", is(150.00)));

        List<Product> databaseItems = productRepository.findAll();
        assertEquals(2, databaseItems.size(), "Database should contain seeded product plus the new one");
    }

    @Test
    void verifyIfGetProductByIdReturnsCorrectProductPayload() throws Exception {

        Long targetId = seededProduct.getProductId();

        mockMvc.perform(get("/api/v1/products/" + targetId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Base-Laptop")))
                .andExpect(jsonPath("$.description", is("Base Laptop Description")))
                .andExpect(jsonPath("$.price", is(1200.00)));
    }

    @Test
    void verifyIfGetAllProductsReturnsCompleteActiveCatalogCollection() throws Exception {

        productRepository.save(new Product(null, "Premium Radio","", new BigDecimal("450.00")));

        mockMvc.perform(get("/api/v1/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Base-Laptop")))
                .andExpect(jsonPath("$[1].name", is("Premium Radio")))
                .andExpect(jsonPath("$[0].description", is("Base Laptop Description")))
                .andExpect(jsonPath("$[1].description", is("")));
    }

    @Test
    void verifyIfUpdateProductModifiesTargetEntityStateCorrectly() throws Exception {

        Long existingProductId = 21L; // Swap to match whatever real ID or context setup you use here

        // 💡 FIX: Make sure the payload body ID matches the path variable parameter
        ProductDTO updatePayload = new ProductDTO();
        updatePayload.setProductId(existingProductId);
        updatePayload.setName("Base-Laptop (Upgraded)");
        updatePayload.setDescription("");
        updatePayload.setPrice(new BigDecimal("1350.00"));

        mockMvc.perform(put("/api/v1/products/{id}", existingProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Base-Laptop (Upgraded)")));
    }

    @Test
    void verifyIfDeleteProductEradicatesTargetResourceFromDatabase() throws Exception {

        Long targetProductId = 20L; // Targeting seeded element record ID

        // 💡 FIX: Update your expect target confirmation to look for a 204 No Content response code
        mockMvc.perform(delete("/api/v1/products/{id}", targetProductId))
                .andExpect(status().isNoContent());
    }
    @Test
    void verifyIfCreateProductFailsWhenItemNameIsAlreadyTaken() throws Exception {

        ProductDTO duplicatePayload = new ProductDTO(1L, "Base-Laptop", "", new BigDecimal("99.00"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicatePayload)))
                .andExpect(status().isBadRequest()) // Caught via GlobalExceptionHandler
                .andExpect(jsonPath("$.message", containsString("Product with name 'Base-Laptop' already exists.")));
    }

    @Test
    void verifyIfGetProductByIdFailsWhenTargetIdIsMissing() throws Exception {

        long phantomId = 999999L;

        mockMvc.perform(get("/api/v1/products/" + phantomId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Product not found with ID: " + phantomId)));
    }

    @Test
    void verifyIfUpdateProductThrowsExceptionWhenTargetIdDoesNotExist() throws Exception {

        Long nonExistentId = 999999L;

        // 💡 FIX: Set the productId to match the path target parameter value
        ProductDTO updatePayload = new ProductDTO();
        updatePayload.setProductId(nonExistentId);
        updatePayload.setName("Ghost Item");
        updatePayload.setDescription("");
        updatePayload.setPrice(new BigDecimal("10.00"));

        mockMvc.perform(put("/api/v1/products/{id}", nonExistentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Product not found with ID: " + nonExistentId)));
    }

    @Test
    void verifyIfDeleteProductThrowsExceptionWhenTargetIdIsMissing() throws Exception {

        long phantomId = 999999L;

        mockMvc.perform(delete("/api/v1/products/" + phantomId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Product not found with ID: " + phantomId)));
    }
}