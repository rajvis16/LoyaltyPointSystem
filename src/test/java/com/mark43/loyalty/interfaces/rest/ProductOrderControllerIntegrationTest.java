package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.domain.service.impl.LoyaltyCacheManager;
import com.mark43.loyalty.infrastructure.repository.*;
import com.mark43.loyalty.interfaces.dto.OrderRequestDTO;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductOrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PointLedgerEntryRepository pointLedgerRepository;

    @Autowired
    private CustomerProductLedgerRepository customerProductLedgerRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RewardRepository rewardRepository;

    @Autowired
    private LoyaltyCacheManager cacheManager;

    @BeforeEach
    void setup() throws Exception {
        cacheManager.clearAll();

        // Database tables flushing is kept here to isolate test runs natively
        pointLedgerRepository.deleteAll();
        customerProductLedgerRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        rewardRepository.deleteAll();

        // -------------------------------------------------------------------------
        // REST-DRIVEN BASELINE SEEDING
        // -------------------------------------------------------------------------

        // Seed Products via Catalog Creation REST Endpoint
        String laptopJson = "{\"name\":\"Premium Laptop\",\"description\":\"High-end workspace\",\"price\":1000.00}";
        String mouseJson = "{\"name\":\"Ergonomic Mouse\",\"description\":\"Precision tracking\",\"price\":50.00}";
        String coffeeMakerJson = "{\"name\":\"Premium Coffee Maker\",\"description\":\"Office Brewer\",\"price\":200.00}";

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(laptopJson)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(mouseJson)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(coffeeMakerJson)).andExpect(status().isCreated());

        // Seed Rewards via Rewards Catalog REST Endpoint
        String giftCardJson = "{\"name\":\"$25 Gift Card\",\"description\":\"Checkout Credit\",\"pointsRequired\":300.00}";
        String travelMugJson = "{\"name\":\"Branded Travel Mug\",\"description\":\"Insulated tumbler\",\"pointsRequired\":100.00}";

        mockMvc.perform(post("/api/v1/rewards").contentType(MediaType.APPLICATION_JSON).content(giftCardJson)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/rewards").contentType(MediaType.APPLICATION_JSON).content(travelMugJson)).andExpect(status().isCreated());

        // Provision Initial System Customers via Registration REST Endpoint
        String customerRajJson = "{\"firstName\":\"Raj\",\"lastName\":\"Singh\",\"email\":\"raj.singh@example.com\",\"phoneNo\":\"555-4343\"}";
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(customerRajJson)).andExpect(status().isCreated());
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldViaSuccessfulApiPurchase() throws Exception {
        Map<String, Integer> itemsToPurchase = new HashMap<>();
        itemsToPurchase.put("Premium Laptop", 1);
        itemsToPurchase.put("Ergonomic Mouse", 2);

        OrderRequestDTO requestPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-GOLD-JOURNEY", itemsToPurchase);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPayload)))
                .andExpect(status().isCreated());

        // Verify state changes natively using the customer balance REST contract
        mockMvc.perform(get("/api/v1/loyalty/balance/email")
                        .param("email", "raj.singh@example.com")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier").value("GOLD"))
                .andExpect(jsonPath("$.pointsBalance").value(1100.00))
                .andExpect(jsonPath("$.rollingSpend").value(1100.00));
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldToPlatinumAcrossSequentialPurchases() throws Exception {
        // TRANSACTION 1: Cross into GOLD Threshold ($1,000.00 Spend)
        Map<String, Integer> txn1Items = Map.of("Premium Laptop", 1);
        OrderRequestDTO txn1Payload = new OrderRequestDTO("raj.singh@example.com", "TXN-STEP-GOLD", txn1Items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn1Payload)))
                .andExpect(status().isCreated());

        // Validate state 1 intermediate API contract output parameters
        mockMvc.perform(get("/api/v1/loyalty/balance/email").param("email", "raj.singh@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier").value("GOLD"))
                .andExpect(jsonPath("$.pointsBalance").value(1000.00));

        // TRANSACTION 2: Cross into PLATINUM Threshold ($1,050.00 Spend at GOLD multiplier rate)
        Map<String, Integer> txn2Items = Map.of("Premium Laptop", 1, "Ergonomic Mouse", 1);
        OrderRequestDTO txn2Payload = new OrderRequestDTO("raj.singh@example.com", "TXN-STEP-PLATINUM", txn2Items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn2Payload)))
                .andExpect(status().isCreated());

        // Final Contract Verification: Balance calculation math = 1000.00 + (1050.00 * 1.5) = 2575.00 points
        mockMvc.perform(get("/api/v1/loyalty/balance/email").param("email", "raj.singh@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier").value("PLATINUM"))
                .andExpect(jsonPath("$.pointsBalance").value(2575.00))
                .andExpect(jsonPath("$.rollingSpend").value(2050.00));
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldToPlatinumAndStayingAtPlatinum() throws Exception {
        // Txn 1: Gold entry boundary
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderRequestDTO("raj.singh@example.com", "TXN-M-GOLD", Map.of("Premium Laptop", 1)))));

        // Txn 2: Platinum entry boundary
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderRequestDTO("raj.singh@example.com", "TXN-M-PLAT", Map.of("Premium Laptop", 1, "Ergonomic Mouse", 1)))));

        // Txn 3: Stable maintenance spend under platinum tier
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderRequestDTO("raj.singh@example.com", "TXN-M-STABLE", Map.of("Ergonomic Mouse", 1)))));

        // Verification over API contract pipeline
        mockMvc.perform(get("/api/v1/loyalty/balance/email").param("email", "raj.singh@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier").value("PLATINUM"))
                .andExpect(jsonPath("$.pointsBalance").value(2675.00)) // 1000 + 1575 + (50 * 2.0 multiplier)
                .andExpect(jsonPath("$.rollingSpend").value(2100.00));
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldAndFallingBackToSilverViaReturn() throws Exception {
        String customerEmail = "fallback.raj@example.com";
        String regJson = "{\"firstName\":\"Raj\",\"lastName\":\"Singh\",\"email\":\"" + customerEmail + "\",\"phoneNo\":\"555-9999\"}";
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(regJson)).andExpect(status().isCreated());

        // Purchase laptop and 2 mice to enter Gold ($1,100.00 spend)
        OrderRequestDTO buyPayload = new OrderRequestDTO(customerEmail, "TXN-FLOW-BUY", Map.of("Premium Laptop", 1, "Ergonomic Mouse", 2));
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(buyPayload)));

        // Execute return of the laptop ($1000.00 clawback)
        OrderRequestDTO returnPayload = new OrderRequestDTO(customerEmail, "TXN-FLOW-BUY", Map.of("Premium Laptop", 1));
        mockMvc.perform(post("/api/v1/orders/return").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(returnPayload)))
                .andExpect(status().isOk());

        // Validate profile was demoted down to Silver cleanly via REST
        mockMvc.perform(get("/api/v1/loyalty/balance/email").param("email", customerEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier").value("SILVER"))
                .andExpect(jsonPath("$.pointsBalance").value(100.00))
                .andExpect(jsonPath("$.rollingSpend").value(100.00));
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldAndRedeemingTwentyFivePercentOfPoints() throws Exception {
        String customerEmail = "redeem.raj@example.com";
        String regJson = "{\"firstName\":\"Raj\",\"lastName\":\"Singh\",\"email\":\"" + customerEmail + "\",\"phoneNo\":\"555-7711\"}";
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(regJson)).andExpect(status().isCreated());

        // Earn 1200 points
        OrderRequestDTO buyPayload = new OrderRequestDTO(customerEmail, "TXN-REDEEM-BUY", Map.of("Premium Laptop", 1, "Ergonomic Mouse", 4));
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(buyPayload)));

        // Spend 300 points via redemption endpoint
        RedeemRewardDTO redeemPayload = new RedeemRewardDTO(customerEmail, "$25 Gift Card");
        mockMvc.perform(post("/api/v1/loyalty/redeem").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(redeemPayload)))
                .andExpect(status().isOk());

        // Verify remaining parameters match expectations
        mockMvc.perform(get("/api/v1/loyalty/balance/email").param("email", customerEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTier").value("GOLD"))
                .andExpect(jsonPath("$.pointsBalance").value(900.00))
                .andExpect(jsonPath("$.rollingSpend").value(1200.00));
    }

    @Test
    void verifyCustomerFailsToRedeemBeyondAvailablePointsBalance() throws Exception {
        String customerEmail = "overspend.raj@example.com";
        String regJson = "{\"firstName\":\"Raj\",\"lastName\":\"Singh\",\"email\":\"" + customerEmail + "\",\"phoneNo\":\"555-8822\"}";
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(regJson)).andExpect(status().isCreated());

        // Buy items worth $1200.00
        OrderRequestDTO buyPayload = new OrderRequestDTO(customerEmail, "TXN-OVERSPEND-BUY", Map.of("Premium Laptop", 1, "Ergonomic Mouse", 4));
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(buyPayload)));

        // Create high tier luxury option costing 5,000 points via Admin REST endpoint
        String getawayRewardJson = "{\"name\":\"Ultimate Luxury Weekend Getaway\",\"description\":\"VIP Resort Package\",\"pointsRequired\":5000.00}";
        mockMvc.perform(post("/api/v1/rewards").contentType(MediaType.APPLICATION_JSON).content(getawayRewardJson)).andExpect(status().isCreated());

        // Request an invalid redemption
        RedeemRewardDTO toxicRedeemPayload = new RedeemRewardDTO(customerEmail, "Ultimate Luxury Weekend Getaway");
        mockMvc.perform(post("/api/v1/loyalty/redeem").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(toxicRedeemPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient points balance. Required: 5000.00, Active Available: 1200.00"));
    }

    @Test
    void verifyCustomerFailsToReturnMoreProductsThanHistoricallyPurchased() throws Exception {
        String customerEmail = "fraud.check.raj@example.com";
        String regJson = "{\"firstName\":\"Raj\",\"lastName\":\"Singh\",\"email\":\"" + customerEmail + "\",\"phoneNo\":\"555-6622\"}";
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(regJson)).andExpect(status().isCreated());

        // Buy 1 mouse, then buy another mouse across a sequential payload stream
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new OrderRequestDTO(customerEmail, "TXN-FRAUD-CHECK", Map.of("Ergonomic Mouse", 1)))));
        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new OrderRequestDTO(customerEmail, "TXN-FRAUD-CHECK", Map.of("Ergonomic Mouse", 1)))));

        // Attempt a toxic fraudulent return of 5 units
        OrderRequestDTO exploitPayload = new OrderRequestDTO(customerEmail, "TXN-FRAUD-CHECK", Map.of("Ergonomic Mouse", 5));
        mockMvc.perform(post("/api/v1/orders/return").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(exploitPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid return request. Customer attempting to return 5 unit(s) of 'Ergonomic Mouse' but only net owns 2 unit(s) from this order."));
    }

    @Test
    void verifyPartialReturnRejectsEntirePayloadIfSingleProductExceedsOwnedInventory() throws Exception {
        String customerEmail = "partial.exploit@example.com";
        String regJson = "{\"firstName\":\"Raj\",\"lastName\":\"Singh\",\"email\":\"" + customerEmail + "\",\"phoneNo\":\"555-1122\"}";
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(regJson)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new OrderRequestDTO(customerEmail, "TXN-PARTIAL-CHECK", Map.of("Ergonomic Mouse", 2, "Premium Laptop", 1)))));

        // Return request payload contains 1 legal laptop and 3 illegal mice (only owns 2)
        OrderRequestDTO exploitPayload = new OrderRequestDTO(customerEmail, "TXN-PARTIAL-CHECK", Map.of("Premium Laptop", 1, "Ergonomic Mouse", 3));
        mockMvc.perform(post("/api/v1/orders/return").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(exploitPayload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyReturnFailsWhenPurchaseReferenceDoesNotExist() throws Exception {
        String customerEmail = "ghost.invoice@example.com";
        String regJson = "{\"firstName\":\"Raj\",\"lastName\":\"Singh\",\"email\":\"" + customerEmail + "\",\"phoneNo\":\"555-0099\"}";
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(regJson)).andExpect(status().isCreated());

        OrderRequestDTO ghostPayload = new OrderRequestDTO(customerEmail, "TXN-FAKE-INV-999", Map.of("Premium Laptop", 1));
        mockMvc.perform(post("/api/v1/orders/return").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(ghostPayload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifyEndpointsRejectZeroOrNegativeQuantities() throws Exception {
        OrderRequestDTO toxicPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-BOUNDS-CHECK", Map.of("Premium Laptop", -2));

        mockMvc.perform(post("/api/v1/orders/buy").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(toxicPayload)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/orders/return").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(toxicPayload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void verifyPointClawbackWithPriorRedemptionDeficit() throws Exception {
        String email = "testuser@example.com";
        String regJson = "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"" + email + "\",\"phoneNo\":\"555-9999\"}";
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regJson))
                .andExpect(status().isCreated());

        // 1. Primary Purchase: $600.00 Spend -> Allocates 600.00 points
        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRequestDTO(email, "TXN-TEST-001", Map.of("Premium Coffee Maker", 3)))))
                .andExpect(status().isCreated());

        // 2. Secondary Purchase: $200.00 Spend -> Allocates +200.00 points (Total Stack: 800.00)
        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRequestDTO(email, "TXN-TEST-002", Map.of("Premium Coffee Maker", 1)))))
                .andExpect(status().isCreated());

        // 3. FIFO Redemption: Consumes 100.00 points from the oldest open bucket (Remaining Stack: 700.00)
        mockMvc.perform(post("/api/v1/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RedeemRewardDTO(email, "Branded Travel Mug"))))
                .andExpect(status().isOk());

        // 4. Full Return of First Order: Clawback requested: -600.00 points (Remaining Stack: 100.00)
        mockMvc.perform(post("/api/v1/orders/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRequestDTO(email, "TXN-TEST-001", Map.of("Premium Coffee Maker", 3)))))
                .andExpect(status().isOk());

        // 5. INTERMEDIATE VERIFICATION: Verify math holds up at 100.00 pts
        mockMvc.perform(get("/api/v1/loyalty/balance/email").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollingSpend").value(200.00))
                .andExpect(jsonPath("$.pointsBalance").value(100.00));

        // 6. FINAL DESTRUCTION STEP: Return the final item from order TXN-TEST-002 ($200 spend -> claws back 200.00 pts)
        // Mathematical sum drops to: 100.00 - 200.00 = -100.00 points.
        mockMvc.perform(post("/api/v1/orders/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderRequestDTO(email, "TXN-TEST-002", Map.of("Premium Coffee Maker", 1)))))
                .andExpect(status().isOk());

        // 7. ZERO-FLOOR CONSTRAINT ASSERTION:
        // Rolling spend drops to $0.00 perfectly.
        // 💥 FAIL-POINT: Without the DTO service-layer clamp, this will fail expecting 0.00 but finding -100.00!
        mockMvc.perform(get("/api/v1/loyalty/balance/email").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rollingSpend").value(0.00))
                .andExpect(jsonPath("$.pointsBalance").value(0.00));
    }
}