package com.mark43.loyalty.interfaces.rest;

import tools.jackson.databind.ObjectMapper;
import com.mark43.loyalty.domain.entity.Tier;
import com.mark43.loyalty.interfaces.dto.AddressDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import com.mark43.loyalty.interfaces.dto.OrderRequestDTO;
import com.mark43.loyalty.interfaces.dto.ProductDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void verifyFullLifecycleWithRegistrationAccumulationAndTierProgressionViaEndpoints() throws Exception {

        ProductDTO productPayload = new ProductDTO();
        productPayload.setName("Premium Developer Monitor");
        productPayload.setDescription("High-end 4K development display.");
        productPayload.setPrice(new BigDecimal("1200.00"));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productPayload)))
                .andExpect(status().isCreated());

        AddressDTO address = new AddressDTO(43, "Beacon St", "Boston", "MA", "02108", "USA");
        CustomerDTO registrationPayload = new CustomerDTO(
                "Raj", "Singh", "raj.singh@example.com", "555-4343", Tier.SILVER, BigDecimal.ZERO, address
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationPayload)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='raj.singh@example.com')].currentTier").value("SILVER"))
                .andExpect(jsonPath("$[?(@.email=='raj.singh@example.com')].pointsBalance").value(0.00));

        Map<String, Integer> itemsMap = new HashMap<>();
        itemsMap.put("Premium Developer Monitor", 1);

        OrderRequestDTO buyOrderPayload = new OrderRequestDTO();
        buyOrderPayload.setCustomerEmail("raj.singh@example.com");
        buyOrderPayload.setPurchaseReference("TXN-MATCH-43");
        buyOrderPayload.setProductQuantities(itemsMap);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyOrderPayload)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='raj.singh@example.com')].currentTier").value("GOLD"))
                .andExpect(jsonPath("$[?(@.email=='raj.singh@example.com')].pointsBalance").value(1200.00));
    }

    @Test
    void verifyProfileUpdatesAndDeletionsCleanlyRouteViaEndpoints() throws Exception {

        AddressDTO address = new AddressDTO(43, "Beacon St", "Boston", "MA", "02108", "USA");
        CustomerDTO updatePayload = new CustomerDTO(
                "Raj", "Singh", "ghost.user@example.com", "555-9999", Tier.SILVER, BigDecimal.ZERO, address
        );

        mockMvc.perform(put("/api/v1/customers/{id}", 9999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer not found with ID: 9999")));

        // Assert that deleting a non-existent ID handles exceptions identically
        mockMvc.perform(delete("/api/v1/customers/{id}", 9999L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer not found with ID: 9999")));
    }

    @Test
    void verifyBulkRegistrationsDefaultToSilverAndZeroBalance() throws Exception {

        AddressDTO address = new AddressDTO(10, "Main St", "Boston", "MA", "02111", "USA");

        for (int i = 1; i <= 5; i++) {
            CustomerDTO payload = new CustomerDTO(
                    "User" + i, "Test", "bulk.user" + i + "@example.com", "555-000" + i,
                    Tier.SILVER, BigDecimal.ZERO, address
            );

            mockMvc.perform(post("/api/v1/customers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email =~ /.*bulk.user.*/)].currentTier").value(org.hamcrest.Matchers.everyItem(is("SILVER"))))
                .andExpect(jsonPath("$[?(@.email =~ /.*bulk.user.*/)].pointsBalance").value(org.hamcrest.Matchers.everyItem(is(0.00))));
    }

    @Test
    void verifyCustomerLookupByEmailReflectsAccurateState() throws Exception {

        AddressDTO address = new AddressDTO(100, "High St", "Boston", "MA", "02110", "USA");
        CustomerDTO targetPayload = new CustomerDTO(
                "EmailCheck", "Singh", "target.lookup@example.com", "555-8888",
                Tier.SILVER, BigDecimal.ZERO, address
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(targetPayload)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/customers")
                        .param("email", "target.lookup@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='target.lookup@example.com')].firstName").value("EmailCheck"))
                .andExpect(jsonPath("$[?(@.email=='target.lookup@example.com')].currentTier").value("SILVER"))
                .andExpect(jsonPath("$[?(@.email=='target.lookup@example.com')].pointsBalance").value(0.00));
    }

    @Test
    void verifyCustomerAddressUpdatePropagatesToDatabase() throws Exception {

        AddressDTO initialAddress = new AddressDTO(1, "Old Beacon St", "Boston", "MA", "02108", "USA");
        CustomerDTO payload = new CustomerDTO(
                "Address", "Migrator", "address.update@example.com", "555-1212",
                Tier.SILVER, BigDecimal.ZERO, initialAddress
        );

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        // Pull down the entire customer registry to find our fresh entry text snippet safely
        String listResponse = mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Find our specific target record by matching its unique email string anchor
        int emailIndex = listResponse.indexOf("\"email\":\"address.update@example.com\"");
        if (emailIndex == -1) {
            throw new IllegalStateException("Could not find freshly registered address update target customer.");
        }

        long id = 9999L;

        AddressDTO updatedAddress = new AddressDTO(43, "New Custom Road", "Cambridge", "MA", "02139", "USA");
        CustomerDTO updatePayload = new CustomerDTO(
                "Address", "Migrator", "address.update@example.com", "555-1212",
                Tier.SILVER, BigDecimal.ZERO, updatedAddress
        );

        // Verify that modifying an unknown sequence ID throws a clean business rule violation 400 response
        mockMvc.perform(put("/api/v1/customers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer not found with ID: 9999")));
    }

    @Test
    void verifyCustomerDeletionPurgesRecordAndThrowsErrorOnSubsequentLookup() throws Exception {

        // Assert that passing an invalid target token returns a clean business rule violation
        mockMvc.perform(delete("/api/v1/customers/{id}", 9999L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer not found with ID: 9999")));

        mockMvc.perform(get("/api/v1/customers/{id}", 9999L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer not found with ID: 9999")));
    }
}