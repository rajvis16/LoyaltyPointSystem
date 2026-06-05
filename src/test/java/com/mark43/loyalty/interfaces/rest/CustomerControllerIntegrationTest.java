package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.entity.Tier;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.interfaces.dto.AddressDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.mark43.loyalty.domain.entity.Tier.GOLD;
import static com.mark43.loyalty.domain.entity.Tier.SILVER;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    private AddressDTO validAddress;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();

        // Reusable, fully populated baseline Address DTO matching constraints
        validAddress = new AddressDTO(100, "Main St", "Littleton", "MA", "01460", "USA");
    }

    // =========================================================================
    // POSITIVE LIFECYCLE HAPPY PATH TESTS
    // =========================================================================

    @Test
    void verifySuccessfulCustomerRegistrationAndRetrievalFlow() throws Exception {
        CustomerDTO payload = new CustomerDTO("Amar", "Singh", "amar.singh@example.com", "555-1234", SILVER, validAddress);

        // 1. Execute POST Registration
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        // 2. Query database to verify record persistence
        Customer savedCustomer = customerRepository.findByEmail("amar.singh@example.com")
                .orElseThrow(() -> new AssertionError("Customer record failed to commit to database tables!"));

        assertEquals("Amar", savedCustomer.getFirstName());
        assertEquals(SILVER, savedCustomer.getCurrentTier());

        // 3. Execute GET by ID to verify Web API delivery contract matches database truth
        String getResponseJson = mockMvc.perform(get("/api/v1/customers/" + savedCustomer.getCustomerId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Customer fetchedCustomer = objectMapper.readValue(getResponseJson, Customer.class);
        assertEquals(savedCustomer.getCustomerId(), fetchedCustomer.getCustomerId());
        assertEquals("555-1234", fetchedCustomer.getPhoneNo());
    }

    @Test
    void verifyCustomerMetadataCanBeSuccessfullyUpdated() throws Exception {
        Customer existingCustomer = new Customer();
        existingCustomer.setFirstName("Priya");
        existingCustomer.setLastName("Singh");
        existingCustomer.setEmail("priya.old@example.com");
        existingCustomer.setPhoneNo("555-9000");
        existingCustomer.setCurrentTier(GOLD);
        Customer seededInstance = customerRepository.save(existingCustomer);

        // Update body passing our baseline valid address payload
        CustomerDTO updatePayload = new CustomerDTO("Priya", "Singh", "priya.singh@example.com", "555-9999", GOLD, validAddress);

        String putResponseJson = mockMvc.perform(put("/api/v1/customers/" + seededInstance.getCustomerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Customer updatedInstance = objectMapper.readValue(putResponseJson, Customer.class);
        assertEquals("priya.singh@example.com", updatedInstance.getEmail());
        assertEquals("555-9999", updatedInstance.getPhoneNo());
        assertEquals(GOLD, updatedInstance.getCurrentTier());
    }

    // =========================================================================
    // NEGATIVE / VALIDATION BOUNDARY CONSTRAINT TESTS
    // =========================================================================

    @Test
    void verifyRegistrationFailsWhenAddressIsMissing() throws Exception {
        // Build a payload where the address field explicitly maps to NULL
        CustomerDTO toxicPayload = new CustomerDTO("Amar", "Singh", "no.address@example.com", "555-4321", SILVER, null);

        String errorResponseJson = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toxicPayload)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Audit Response Body to catch payload rejection strings
        assertTrue(errorResponseJson.contains("Validation Failed") || errorResponseJson.contains("invalid parameters"),
                "Error metadata failed to explicitly track validation breakdown tokens.");
        assertTrue(errorResponseJson.contains("Address details are required."),
                "The validation response payload failed to state the custom field message violation requirements.");
    }

    @Test
    void verifyRegistrationFailsAndThrowsBadRequestWhenEmailAlreadyExists() throws Exception {
        Customer coreCustomer = new Customer();
        coreCustomer.setFirstName("Duplicate"); coreCustomer.setLastName("Check"); coreCustomer.setEmail("conflict@example.com"); coreCustomer.setPhoneNo("555-8888"); coreCustomer.setCurrentTier(SILVER);
        customerRepository.save(coreCustomer);

        // 💡 FIXED: Now passes a non-null validAddress payload to get past structural filters cleanly
        CustomerDTO duplicateEmailPayload = new CustomerDTO("New", "User", "conflict@example.com", "555-1111", SILVER, validAddress);

        String errorResponse = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEmailPayload)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(errorResponse.contains("already exists"),
                "The service boundary layer failed to notify the client about the duplicate email constraint block.");
    }

    @Test
    void verifyRegistrationFailsAndThrowsBadRequestWhenPhoneNumberAlreadyExists() throws Exception {
        Customer coreCustomer = new Customer();
        coreCustomer.setFirstName("Phone"); coreCustomer.setLastName("Check"); coreCustomer.setEmail("phone.original@example.com"); coreCustomer.setPhoneNo("555-9999"); coreCustomer.setCurrentTier(SILVER);
        customerRepository.save(coreCustomer);

        // 💡 FIXED: Address payload parameter is now fully populated, allowing execution to reach the service routine cleanly
        CustomerDTO duplicatePhonePayload = new CustomerDTO("Another", "User", "phone.unique@example.com", "555-9999", SILVER, validAddress);

        String errorResponse = mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicatePhonePayload)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(errorResponse.contains("already exists"),
                "The validation response payload failed to notify the client about the duplicate phone configuration.");
    }
}