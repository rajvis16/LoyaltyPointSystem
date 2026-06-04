package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.entity.CustomerProductLedgerEntry;
import com.mark43.loyalty.infrastructure.repository.PointLedgerEntryRepository;
import tools.jackson.databind.ObjectMapper;
import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.entity.Product;
import com.mark43.loyalty.infrastructure.repository.CustomerProductLedgerRepository;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.interfaces.dto.OrderRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mark43.loyalty.domain.entity.ProductAction.BOUGHT;
import static com.mark43.loyalty.domain.entity.Tier.SILVER;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // Enforces true runtime environment isolation
class ProductOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerProductLedgerRepository customerProductLedgerRepository;

    @Autowired
    private PointLedgerEntryRepository pointLedgerRepository;

    @BeforeEach
    void setup() {

        pointLedgerRepository.deleteAll();
        customerProductLedgerRepository.deleteAll();

        customerRepository.deleteAll();
        productRepository.deleteAll();

        productRepository.save(new Product(null, "Laptop", "Laptop description", new BigDecimal("1000.00")));
        productRepository.save(new Product(null, "Mouse", "Mouse description", new BigDecimal("50.00")));

        Customer customer = new Customer();
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail("raj.singh@example.com");
        customer.setPhoneNo("555-4343");
        customer.setCurrentTier(SILVER);

        customerRepository.save(customer);
    }

    @Test
    void verifyIfBuyProductsApiSucceedsAndAppendsCorrectLedgerStates() throws Exception {

        Map<String, Integer> items = new HashMap<>();
        items.put("Laptop", 1);
        items.put("Mouse", 2);

        OrderRequestDTO requestPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-V1-001", items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPayload)))
                .andExpect(status().isCreated());

        assertEquals(2, customerProductLedgerRepository.count(), "Inventory trail should record exactly two separate entries");
        assertEquals(1, pointLedgerRepository.count(), "Loyalty trail should write one consolidated earning event row");

        Product laptop = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("Laptop"))
                .findFirst()
                .orElseThrow();

        Product mouse = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("Mouse"))
                .findFirst()
                .orElseThrow();

        List<CustomerProductLedgerEntry> productLedgerRows = customerProductLedgerRepository.findAll();
        CustomerProductLedgerEntry laptopRow = productLedgerRows.stream()
                .filter(row -> row.getProductId().equals(laptop.getProductId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a ledger entry tracking the Laptop ID but none was found"));

        CustomerProductLedgerEntry mouseRow = productLedgerRows.stream()
                .filter(row -> row.getProductId().equals(mouse.getProductId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a ledger entry tracking the Mouse ID but none was found"));

        Customer customer = customerRepository.findAll().get(0);

        assertEquals(customer.getCustomerId(), laptopRow.getCustomerId(), "Laptop entry must map to the correct customer primary key identifier");
        assertEquals("TXN-V1-001", laptopRow.getPurchaseId(), "Laptop entry must preserve the transactional invoice string anchor");
        assertEquals(1, laptopRow.getQuantity(), "Laptop entry quantity must equal exactly 1 unit");
        assertEquals(BOUGHT, laptopRow.getAction(), "Laptop entry action lifecycle must track as BOUGHT");
        assertNotNull(laptopRow.getTransactionDate(), "Laptop entry must track a non-null LocalDateTime tracking timestamp");

        assertEquals(customer.getCustomerId(), mouseRow.getCustomerId(), "Mouse entry must map to the correct customer primary key identifier");
        assertEquals("TXN-V1-001", mouseRow.getPurchaseId(), "Mouse entry must preserve the transactional invoice string anchor");
        assertEquals(2, mouseRow.getQuantity(), "Mouse entry quantity must equal exactly 2 units");
        assertEquals(BOUGHT, mouseRow.getAction(), "Mouse entry action lifecycle must track as BOUGHT");
        assertNotNull(mouseRow.getTransactionDate(), "Mouse entry must track a non-null LocalDateTime tracking timestamp");
    }

    @Test
    void verifyIfReturnProductsApiSucceedsForValidHistoricalOwnership() throws Exception {

        Map<String, Integer> buyItems = new HashMap<>();
        buyItems.put("Mouse", 3);
        OrderRequestDTO buyPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-REF-100", buyItems);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyPayload)))
                .andExpect(status().isCreated());

        Map<String, Integer> returnItems = new HashMap<>();
        returnItems.put("Mouse", 1);
        OrderRequestDTO returnPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-REF-100", returnItems);

        mockMvc.perform(post("/api/v1/orders/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnPayload)))
                .andExpect(status().isOk());

        assertEquals(2, customerProductLedgerRepository.count(), "Ledger should maintain the full transaction audit history");
    }

    @Test
    void verifyIfBuyProductsThrowsExceptionWhenCustomerIsMissingFromDatabase() throws Exception {

        Map<String, Integer> items = new HashMap<>();
        items.put("Laptop", 1);
        OrderRequestDTO requestPayload = new OrderRequestDTO("missing@example.com", "TXN-000", items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPayload)))
                .andExpect(status().isBadRequest()) // Caught via GlobalExceptionHandler
                .andExpect(jsonPath("$.message", containsString("Customer not found with email: missing@example.com")));

        assertEquals(0, customerProductLedgerRepository.count());
    }

    @Test
    void verifyIfBuyProductsThrowsExceptionWhenProductDoesNotExistInCatalog() throws Exception {

        Map<String, Integer> items = new HashMap<>();
        items.put("Phantom-Device", 1);
        OrderRequestDTO requestPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-002", items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Product not found in catalog: Phantom-Device")));
    }

    @Test
    void verifyIfReturnProductsApiBlocksFraudWhenReturnExceedsHistoricalOwnership() throws Exception {

        Map<String, Integer> buyItems = new HashMap<>();
        buyItems.put("Mouse", 1);
        OrderRequestDTO buyPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-FRAUD-CHECK", buyItems);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyPayload)))
                .andExpect(status().isCreated());

        Map<String, Integer> exploitItems = new HashMap<>();
        exploitItems.put("Mouse", 10);
        OrderRequestDTO fraudPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-FRAUD-CHECK", exploitItems);

        mockMvc.perform(post("/api/v1/orders/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fraudPayload)))
                .andExpect(status().isBadRequest()) // Safely blocked at business perimeter
                .andExpect(jsonPath("$.message", containsString("attempting to return 10 unit(s) of 'Mouse' but only net owns 1")));
    }

    @Test
    void verifyIfBuyProductsApiFailsValidationWhenQuantityIsZeroOrNegative() throws Exception {

        Map<String, Integer> items = new HashMap<>();
        items.put("Laptop", 0);

        OrderRequestDTO brokenPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-BAD", items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(brokenPayload)))
                .andExpect(status().isBadRequest()); // Blocked at framework gateway before hitting your service beans
    }

    @Test
    void verifyIfBuyProductsApiFailsValidationWhenFieldsAreBlankOrMissing() throws Exception {

        Map<String, Integer> items = new HashMap<>();
        items.put("Laptop", 1);

        OrderRequestDTO brokenPayload = new OrderRequestDTO("", "   ", items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(brokenPayload)))
                .andExpect(status().isBadRequest());
    }
}