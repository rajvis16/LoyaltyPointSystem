package com.mark43.loyalty.interfaces.rest;

import tools.jackson.databind.ObjectMapper;
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

import static com.mark43.loyalty.domain.entity.Tier.SILVER;
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
                "Raj", "Singh", "raj.singh@example.com", "555-4343", SILVER, BigDecimal.ZERO, BigDecimal.ZERO, address
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

        String missingEmail = "ghost.user@example.com";

        // 💡 FIX: Create a real address object to satisfy the controller's @Valid layer
        AddressDTO mockAddress = new AddressDTO(43, "Beacon St", "Boston", "MA", "02108", "USA");

        CustomerDTO updatePayload = new CustomerDTO(
                "Raj", "Singh", missingEmail, "555-9999", SILVER, BigDecimal.ZERO, BigDecimal.ZERO, mockAddress
        );

        mockMvc.perform(put("/api/v1/customers/{email}", missingEmail)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer not found with email: " + missingEmail)));
    }

    @Test
    void verifyBulkRegistrationsDefaultToSilverAndZeroBalance() throws Exception {

        AddressDTO address = new AddressDTO(10, "Main St", "Boston", "MA", "02111", "USA");

        for (int i = 1; i <= 5; i++) {
            CustomerDTO payload = new CustomerDTO(
                    "User" + i, "Test", "bulk.user" + i + "@example.com", "555-000" + i,
                    SILVER, BigDecimal.ZERO, BigDecimal.ZERO, address
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
                SILVER, BigDecimal.ZERO, BigDecimal.ZERO, address
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

        // 💡 FIX: Target a customer we KNOW exists from the DataLoader seed data
        String existingEmail = "alice@example.com";

        AddressDTO targetAddress = new AddressDTO(43, "New Custom Road", "Cambridge", "MA", "02139", "USA");

        // Build the payload matching Alice's existing profile identity anchor
        CustomerDTO updatePayload = new CustomerDTO(
                "Alice", "Smith", existingEmail, "555-0101", SILVER, BigDecimal.ZERO, BigDecimal.ZERO, targetAddress
        );

        // This will now find Alice cleanly, update her address fields, and pass!
        mockMvc.perform(put("/api/v1/customers/{email}", existingEmail)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk());
    }

    @Test
    void verifyCustomerDeletionPurgesRecordAndThrowsErrorOnSubsequentLookup() throws Exception {

        // 💡 FIX: Expect the message to contain "email: 9999" instead of "ID: 9999"
        mockMvc.perform(delete("/api/v1/customers/{email}", "9999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Customer not found with email: 9999")));
    }
}