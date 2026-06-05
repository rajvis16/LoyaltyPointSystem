package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.infrastructure.repository.*;
import com.mark43.loyalty.interfaces.dto.CustomerBalanceDTO;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mark43.loyalty.domain.entity.ProductAction.BOUGHT;
import static com.mark43.loyalty.domain.entity.Tier.*;
import static com.mark43.loyalty.domain.entity.TransactionType.EARN;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerProductLedgerRepository customerProductLedgerRepository;

    @Autowired
    private PointLedgerEntryRepository pointLedgerRepository;

    @Autowired
    private RewardRepository rewardRepository;

    private Product laptop;
    private Product mouse;

    @BeforeEach
    void setup() {
        pointLedgerRepository.deleteAll();
        customerProductLedgerRepository.deleteAll();
        customerRepository.deleteAll();
        productRepository.deleteAll();
        rewardRepository.deleteAll();

        // Seed core catalog elements
        laptop = productRepository.save(new Product(null, "Premium Laptop", "High-end developer workspace", new BigDecimal("1000.00")));
        mouse = productRepository.save(new Product(null, "Ergonomic Mouse", "Wireless precision tracking", new BigDecimal("50.00")));

        rewardRepository.save(new Reward(null, "$25 Gift Card", "$25 Gift Card desc", new BigDecimal("300.00")));

        // Establish the baseline state - Customer registered explicitly under SILVER tier
        Customer customer = new Customer();
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail("raj.singh@example.com");
        customer.setPhoneNo("555-4343");
        customer.setCurrentTier(SILVER);

        customerRepository.save(customer);
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldViaSuccessfulApiPurchase() throws Exception {
        // Arrange: Build payload with enough volume to bridge into GOLD threshold ($1100.00 total spend)
        Map<String, Integer> itemsToPurchase = new HashMap<>();
        itemsToPurchase.put("Premium Laptop", 1);  // $1000.00
        itemsToPurchase.put("Ergonomic Mouse", 2);   // $100.00

        OrderRequestDTO requestPayload = new OrderRequestDTO("raj.singh@example.com", "TXN-GOLD-JOURNEY", itemsToPurchase);

        // Act: Execute the contextual API endpoint simulation over MockMvc HTTP pipeline
        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestPayload)))
                .andExpect(status().isCreated());

        // Assert Step 1: Deep Verification of Customer Entity state mutations
        Customer targetCustomer = customerRepository.findByEmail("raj.singh@example.com")
                .orElseThrow(() -> new AssertionError("Customer profile record was purged or missing standard hooks"));

        assertEquals(GOLD, targetCustomer.getCurrentTier(),
                "CRITICAL: Real-time transactional tier re-evaluation query failed to transition customer from SILVER to GOLD status!");

        // Assert Step 2: Verification of CustomerProductLedgerEntry (Inventory Trail Facts)
        List<CustomerProductLedgerEntry> productLedgerRows = customerProductLedgerRepository.findAll();
        assertEquals(2, productLedgerRows.size(), "Expected exactly two explicit database line items logged in product ledger table");

        CustomerProductLedgerEntry laptopRow = productLedgerRows.stream()
                .filter(row -> row.getProductId().equals(laptop.getProductId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing transactional line tracking item product identifier: Laptop"));

        assertEquals(targetCustomer.getCustomerId(), laptopRow.getCustomerId());
        assertEquals("TXN-GOLD-JOURNEY", laptopRow.getPurchaseId());
        assertEquals(BOUGHT, laptopRow.getAction(), "Lifecycle action state parameter must register explicitly as BOUGHT");
        assertEquals(1, laptopRow.getQuantity());
        assertEquals(new BigDecimal("1000.00"), laptopRow.getTotalSpendingPerProduct(), "Financial trace column mismatch on Laptop row entry");
        assertNotNull(laptopRow.getTransactionDate());

        CustomerProductLedgerEntry mouseRow = productLedgerRows.stream()
                .filter(row -> row.getProductId().equals(mouse.getProductId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing transactional line tracking item product identifier: Mouse"));

        assertEquals(targetCustomer.getCustomerId(), mouseRow.getCustomerId());
        assertEquals("TXN-GOLD-JOURNEY", mouseRow.getPurchaseId());
        assertEquals(BOUGHT, mouseRow.getAction(), "Lifecycle action state parameter must register explicitly as BOUGHT");
        assertEquals(2, mouseRow.getQuantity());
        assertEquals(new BigDecimal("100.00"), mouseRow.getTotalSpendingPerProduct(), "Financial trace column mismatch on Mouse row entry");
        assertNotNull(mouseRow.getTransactionDate());

        // Assert Step 3: Verification of PointLedgerEntry (Loyalty Allocation Balance Facts)
        List<PointLedgerEntry> loyaltyLedgerRows = pointLedgerRepository.findAll();
        assertEquals(1, loyaltyLedgerRows.size(), "Expected precisely one consolidated points earning row committed to point ledger entry table");

        PointLedgerEntry earnEntry = loyaltyLedgerRows.getFirst();
        assertEquals(targetCustomer.getCustomerId(), earnEntry.getCustomerId());
        assertEquals(EARN, earnEntry.getTransactionType(), "Transactional ledger variant flag must log explicitly as an EARN event");
        assertEquals("TXN-GOLD-JOURNEY", earnEntry.getPurchaseId());
        assertNull(earnEntry.getParentEntry(), "Primary purchase allocation line cannot map dependencies to historical parent references");

        // Base Multiplier Math Verification: $1100.00 total spend * 1.00 multiplier = 1100.00 loyalty points allocated
        assertEquals(new BigDecimal("1100.00"), earnEntry.getPoints(), "Loyalty point snapshot rate failed execution calculation balance checks");
        assertEquals(new BigDecimal("1.00"), earnEntry.getTierPointUsed(), "Historical audit multiplier metric column failed baseline state preservation capture checks");
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldToPlatinumAcrossSequentialPurchases() throws Exception {

        // =========================================================================
        // TRANSACTION 1: SILVER Baseline -> Cross into GOLD Threshold ($1,000.00 Spend)
        // =========================================================================
        Map<String, Integer> txn1Items = new HashMap<>();
        txn1Items.put("Premium Laptop", 1); // $1000.00 total line spend

        OrderRequestDTO txn1Payload = new OrderRequestDTO("raj.singh@example.com", "TXN-STEP-GOLD", txn1Items);

        // Execute first purchase via REST gateway
        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn1Payload)))
                .andExpect(status().isCreated());

        // Verify intermediate state: Customer must now be GOLD
        Customer customerAfterTxn1 = customerRepository.findByEmail("raj.singh@example.com")
                .orElseThrow(() -> new AssertionError("Customer profile missing after Txn 1"));
        assertEquals(GOLD, customerAfterTxn1.getCurrentTier(), "Customer should have transitioned to GOLD tier");

        // =========================================================================
        // TRANSACTION 2: Active GOLD State -> Cross into PLATINUM Threshold ($1,050.00 Spend)
        // =========================================================================
        // Accumulative rolling spend will become: $1000.00 (Txn 1) + $1050.00 (Txn 2) = $2050.00
        // This cleanly breaks past your $2000.00 PLATINUM catalog boundary.
        Map<String, Integer> txn2Items = new HashMap<>();
        txn2Items.put("Premium Laptop", 1); // $1000.00
        txn2Items.put("Ergonomic Mouse", 1);  // $50.00

        OrderRequestDTO txn2Payload = new OrderRequestDTO("raj.singh@example.com", "TXN-STEP-PLATINUM", txn2Items);

        // Execute second purchase via REST gateway
        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn2Payload)))
                .andExpect(status().isCreated());

        // =========================================================================
        // CRITICAL DEEP AUDIT VALUATION: SYSTEM VERIFICATION
        // =========================================================================

        // 1. Audit Customer Profile Status
        Customer finalCustomer = customerRepository.findByEmail("raj.singh@example.com")
                .orElseThrow(() -> new AssertionError("Customer profile missing after Txn 2"));
        assertEquals(PLATINUM, finalCustomer.getCurrentTier(),
                "CRITICAL: Accumulative rolling spend calculation failed to upgrade customer to PLATINUM tier status!");

        // 2. Audit Product Ledger Entries (Inventory Facts)
        List<CustomerProductLedgerEntry> customerProductLedgerEntries = customerProductLedgerRepository.findAll();
        assertEquals(3, customerProductLedgerEntries.size(), "Expected exactly 3 separate line items written across both transactions");

        // Validate Txn 1 Row (Laptop)
        CustomerProductLedgerEntry laptopRow1 = customerProductLedgerEntries.stream()
                .filter(row -> "TXN-STEP-GOLD".equals(row.getPurchaseId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing product ledger row for TXN-STEP-GOLD"));
        assertEquals(laptop.getProductId(), laptopRow1.getProductId());
        assertEquals(BOUGHT, laptopRow1.getAction());
        assertEquals(1, laptopRow1.getQuantity());
        assertEquals(new BigDecimal("1000.00"), laptopRow1.getTotalSpendingPerProduct());

        // Validate Txn 2 Rows (Laptop and Mouse)
        List<CustomerProductLedgerEntry> txn2ProductRows = customerProductLedgerEntries.stream()
                .filter(row -> "TXN-STEP-PLATINUM".equals(row.getPurchaseId()))
                .toList();
        assertEquals(2, txn2ProductRows.size(), "Txn 2 should have written exactly 2 rows to the product ledger");

        CustomerProductLedgerEntry laptopRow2 = txn2ProductRows.stream()
                .filter(row -> row.getProductId().equals(laptop.getProductId()))
                .findFirst().orElseThrow();
        assertEquals(BOUGHT, laptopRow2.getAction());
        assertEquals(1, laptopRow2.getQuantity());
        assertEquals(new BigDecimal("1000.00"), laptopRow2.getTotalSpendingPerProduct());

        CustomerProductLedgerEntry mouseRow2 = txn2ProductRows.stream()
                .filter(row -> row.getProductId().equals(mouse.getProductId()))
                .findFirst().orElseThrow();
        assertEquals(BOUGHT, mouseRow2.getAction());
        assertEquals(1, mouseRow2.getQuantity());
        assertEquals(new BigDecimal("50.00"), mouseRow2.getTotalSpendingPerProduct());

        // 3. Audit Point Ledger Entries (Loyalty Engine Allocation Facts)
        List<PointLedgerEntry> pointLedgerRows = pointLedgerRepository.findAll();
        assertEquals(2, pointLedgerRows.size(), "Expected exactly 2 point allocation ledger rows in the database");

        // Point Entry 1 Audit: Executed when the customer was still SILVER ($1000 spend * 1.00 multiplier = 1000.00 points)
        PointLedgerEntry earnEntry1 = pointLedgerRows.stream()
                .filter(row -> "TXN-STEP-GOLD".equals(row.getPurchaseId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing loyalty ledger record for TXN-STEP-GOLD"));
        assertEquals(EARN, earnEntry1.getTransactionType());
        assertEquals(new BigDecimal("1000.00"), earnEntry1.getPoints(), "Points calculation failed for Txn 1 (SILVER rate)");
        assertEquals(new BigDecimal("1.00"), earnEntry1.getTierPointUsed(), "Snapshot tier rate failed for Txn 1");

        // Point Entry 2 Audit: Executed when the customer was GOLD ($1050 spend * 1.50 multiplier = 1575.00 points)
        PointLedgerEntry earnEntry2 = pointLedgerRows.stream()
                .filter(row -> "TXN-STEP-PLATINUM".equals(row.getPurchaseId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing loyalty ledger record for TXN-STEP-PLATINUM"));
        assertEquals(EARN, earnEntry2.getTransactionType());

        // Math check: 1050.00 spend * 1.50 multiplier = 1575.00 allocated points
        assertEquals(new BigDecimal("1575.00"), earnEntry2.getPoints(), "Points calculation failed for Txn 2 (GOLD rate optimization leak!)");
        assertEquals(new BigDecimal("1.50"), earnEntry2.getTierPointUsed(), "Snapshot tier rate failed to lock in the historical 1.5x multiplier rate!");
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldToPlatinumAndStayingAtPlatinum() throws Exception {
        // =========================================================================
        // TRANSACTION 1: SILVER Baseline -> Cross into GOLD Threshold ($1,000.00 Spend)
        // =========================================================================
        Map<String, Integer> txn1Items = new HashMap<>();
        txn1Items.put("Premium Laptop", 1); // $1000.00 total line spend

        OrderRequestDTO txn1Payload = new OrderRequestDTO("raj.singh@example.com", "TXN-MILESTONE-GOLD", txn1Items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn1Payload)))
                .andExpect(status().isCreated());

        // Intermediate Validation 1: Confirm transition to GOLD
        Customer customerAfterTxn1 = customerRepository.findByEmail("raj.singh@example.com").orElseThrow();
        assertEquals(GOLD, customerAfterTxn1.getCurrentTier(), "Customer should be intermediate GOLD");

        // =========================================================================
        // TRANSACTION 2: Active GOLD State -> Cross into PLATINUM Threshold ($1,050.00 Spend)
        // =========================================================================
        // Accumulative rolling spend: $1000.00 (Txn 1) + $1050.00 (Txn 2) = $2050.00 (Breaks past $2000 limit)
        Map<String, Integer> txn2Items = new HashMap<>();
        txn2Items.put("Premium Laptop", 1); // $1000.00
        txn2Items.put("Ergonomic Mouse", 1);  // $50.00

        OrderRequestDTO txn2Payload = new OrderRequestDTO("raj.singh@example.com", "TXN-MILESTONE-PLATINUM", txn2Items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn2Payload)))
                .andExpect(status().isCreated());

        // Intermediate Validation 2: Confirm transition to PLATINUM
        Customer customerAfterTxn2 = customerRepository.findByEmail("raj.singh@example.com").orElseThrow();
        assertEquals(PLATINUM, customerAfterTxn2.getCurrentTier(), "Customer should be intermediate PLATINUM");

        // =========================================================================
        // TRANSACTION 3: Active PLATINUM State -> Maintenance Check ($50.00 Spend)
        // =========================================================================
        // Accumulative rolling spend: $2050.00 + $50.00 = $2100.00 (Remains solidly PLATINUM)
        Map<String, Integer> txn3Items = new HashMap<>();
        txn3Items.put("Ergonomic Mouse", 1); // $50.00 total line spend

        OrderRequestDTO txn3Payload = new OrderRequestDTO("raj.singh@example.com", "TXN-MILESTONE-STABLE", txn3Items);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txn3Payload)))
                .andExpect(status().isCreated());

        // =========================================================================
        // CRITICAL DEEP AUDIT VALUATION: SYSTEM VERIFICATION
        // =========================================================================

        // 1. Audit Customer Profile Status
        Customer finalCustomer = customerRepository.findByEmail("raj.singh@example.com")
                .orElseThrow(() -> new AssertionError("Customer profile record missing after transaction sequences"));
        assertEquals(PLATINUM, finalCustomer.getCurrentTier(),
                "CRITICAL: Customer status should remain securely locked at PLATINUM tier level!");

        // 2. Audit Product Ledger Entries (Inventory Facts)
        List<CustomerProductLedgerEntry> customerProductLedgerEntries = customerProductLedgerRepository.findAll();
        assertEquals(4, customerProductLedgerEntries.size(), "Expected exactly 4 separate line items written across all 3 transactions");

        // Validate Txn 1 Row (Laptop - $1000.00)
        CustomerProductLedgerEntry laptopRow1 = customerProductLedgerEntries.stream()
                .filter(row -> "TXN-MILESTONE-GOLD".equals(row.getPurchaseId())).findFirst().orElseThrow();
        assertEquals(laptop.getProductId(), laptopRow1.getProductId());
        assertEquals(BOUGHT, laptopRow1.getAction());
        assertEquals(1, laptopRow1.getQuantity());
        assertEquals(new BigDecimal("1000.00"), laptopRow1.getTotalSpendingPerProduct());

        // Validate Txn 2 Rows (Laptop - $1000.00 and Mouse - $50.00)
        List<CustomerProductLedgerEntry> txn2ProductRows = customerProductLedgerEntries.stream()
                .filter(row -> "TXN-MILESTONE-PLATINUM".equals(row.getPurchaseId())).toList();
        assertEquals(2, txn2ProductRows.size());

        CustomerProductLedgerEntry laptopRow2 = txn2ProductRows.stream().filter(row -> row.getProductId().equals(laptop.getProductId())).findFirst().orElseThrow();
        assertEquals(1, laptopRow2.getQuantity());
        assertEquals(new BigDecimal("1000.00"), laptopRow2.getTotalSpendingPerProduct());

        CustomerProductLedgerEntry mouseRow2 = txn2ProductRows.stream().filter(row -> row.getProductId().equals(mouse.getProductId())).findFirst().orElseThrow();
        assertEquals(1, mouseRow2.getQuantity());
        assertEquals(new BigDecimal("50.00"), mouseRow2.getTotalSpendingPerProduct());

        // Validate Txn 3 Row (Mouse - $50.00)
        CustomerProductLedgerEntry mouseRow3 = customerProductLedgerEntries.stream()
                .filter(row -> "TXN-MILESTONE-STABLE".equals(row.getPurchaseId())).findFirst().orElseThrow();
        assertEquals(mouse.getProductId(), mouseRow3.getProductId());
        assertEquals(BOUGHT, mouseRow3.getAction());
        assertEquals(1, mouseRow3.getQuantity());
        assertEquals(new BigDecimal("50.00"), mouseRow3.getTotalSpendingPerProduct());

        // 3. Audit Point Ledger Entries (Loyalty Engine Allocation Facts)
        List<PointLedgerEntry> pointLedgerRows = pointLedgerRepository.findAll();
        assertEquals(3, pointLedgerRows.size(), "Expected exactly 3 point allocation ledger rows in the database");

        // Point Entry 1 Audit: Executed under SILVER ($1000 spend * 1.00 multiplier = 1000.00 points)
        PointLedgerEntry earnEntry1 = pointLedgerRows.stream()
                .filter(row -> "TXN-MILESTONE-GOLD".equals(row.getPurchaseId())).findFirst().orElseThrow();
        assertEquals(EARN, earnEntry1.getTransactionType());
        assertEquals(new BigDecimal("1000.00"), earnEntry1.getPoints());
        assertEquals(new BigDecimal("1.00"), earnEntry1.getTierPointUsed());

        // Point Entry 2 Audit: Executed under GOLD ($1050 spend * 1.50 multiplier = 1575.00 points)
        PointLedgerEntry earnEntry2 = pointLedgerRows.stream()
                .filter(row -> "TXN-MILESTONE-PLATINUM".equals(row.getPurchaseId())).findFirst().orElseThrow();
        assertEquals(EARN, earnEntry2.getTransactionType());
        assertEquals(new BigDecimal("1575.00"), earnEntry2.getPoints());
        assertEquals(new BigDecimal("1.50"), earnEntry2.getTierPointUsed());

        // Point Entry 3 Audit: Executed under PLATINUM ($50 spend * 2.00 multiplier = 100.00 points)
        PointLedgerEntry earnEntry3 = pointLedgerRows.stream()
                .filter(row -> "TXN-MILESTONE-STABLE".equals(row.getPurchaseId())).findFirst().orElseThrow();
        assertEquals(EARN, earnEntry3.getTransactionType());

        // Critical Validation: $50.00 spend * 2.00 Platinum multiplier = 100.00 points
        assertEquals(new BigDecimal("100.00"), earnEntry3.getPoints(), "Points calculation failed for Txn 3 (PLATINUM peak rate rate loop leak!)");
        assertEquals(new BigDecimal("2.00"), earnEntry3.getTierPointUsed(), "Snapshot tier rate failed to lock in the peak 2.0x Platinum multiplier!");
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldAndFallingBackToSilverViaReturn() throws Exception {
        // Generate a distinct email context for this specific sequence
        String customerEmail = "fallback.raj@example.com";

        Customer customer = new Customer();
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail(customerEmail);
        customer.setPhoneNo("555-9999");
        customer.setCurrentTier(SILVER);
        customerRepository.save(customer);

        // =========================================================================
        // STEP 1: INITIAL PURCHASE -> Cross into GOLD Threshold ($1,100.00 Spend)
        // =========================================================================
        Map<String, Integer> buyItems = new HashMap<>();
        buyItems.put("Premium Laptop", 1);  // $1000.00
        buyItems.put("Ergonomic Mouse", 2);   // $100.00

        OrderRequestDTO buyPayload = new OrderRequestDTO(customerEmail, "TXN-FLOW-BUY", buyItems);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyPayload)))
                .andExpect(status().isCreated());

        // Validate intermediate State 1: Customer must be upgraded to GOLD
        Customer customerState1 = customerRepository.findByEmail(customerEmail).orElseThrow();
        assertEquals(GOLD, customerState1.getCurrentTier(), "Customer should have transitioned to GOLD tier status");

        // Point Verification 1: $1100.00 spend * 1.00 Silver multiplier = 1100.00 active points allocated
        List<PointLedgerEntry> pointsHistory1 = pointLedgerRepository.findAll().stream()
                .filter(row -> row.getCustomerId().equals(customerState1.getCustomerId())).toList();
        assertEquals(1, pointsHistory1.size());
        assertEquals(new BigDecimal("1100.00"), pointsHistory1.getFirst().getPoints());

        // =========================================================================
        // STEP 2: RETURN TRANSACTION -> Fall Back to SILVER Status (Return Laptop)
        // =========================================================================
        // Returning 1 Laptop removes $1000.00 from their 12-month rolling balance.
        // Rolling spend becomes: $1100.00 - $1000.00 = $100.00 (Drops far below the $1000 GOLD threshold)
        Map<String, Integer> returnItems = new HashMap<>();
        returnItems.put("Premium Laptop", 1); // Requesting return of 1 unit

        OrderRequestDTO returnPayload = new OrderRequestDTO(customerEmail, "TXN-FLOW-BUY", returnItems);

        mockMvc.perform(post("/api/v1/orders/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnPayload)))
                .andExpect(status().isOk());

        // =========================================================================
        // CRITICAL DEEP AUDIT VALUATION: SYSTEM FALLBACK VERIFICATION
        // =========================================================================

        // 1. Audit Final Customer Profile Status & Tier Downgrade Validation
        Customer finalCustomer = customerRepository.findByEmail(customerEmail).orElseThrow();
        assertEquals(SILVER, finalCustomer.getCurrentTier(),
                "CRITICAL: Customer rolling spend calculation failed to downgrade profile back to SILVER status after return processing!");

        // 2. Audit Product Ledger Entries (Inventory Facts)
        List<CustomerProductLedgerEntry> customerProductLedgerEntries = customerProductLedgerRepository.findAll().stream()
                .filter(row -> row.getCustomerId().equals(finalCustomer.getCustomerId())).toList();
        assertEquals(3, customerProductLedgerEntries.size(), "Expected 2 BOUGHT entries and 1 RETURNED entry logged in database");

        // Locate and confirm the immutable RETURNED ledger entry row details
        CustomerProductLedgerEntry returnRow = customerProductLedgerEntries.stream()
                .filter(row -> row.getAction() == ProductAction.RETURNED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing custom inventory trail history block for RETURNED row item"));

        assertEquals(laptop.getProductId(), returnRow.getProductId());
        assertEquals(1, returnRow.getQuantity());
        // Core Feature Check: The entry value is explicitly negated to show an outward balance trail (-1000.00)
        assertEquals(new BigDecimal("-1000.00"), returnRow.getTotalSpendingPerProduct(), "Financial trace column failed to reflect signed negated value reduction!");

        // 3. Audit Point Ledger Entries & Clawback Mathematics Validation
        List<PointLedgerEntry> pointLedgerRows = pointLedgerRepository.findAll().stream()
                .filter(row -> row.getCustomerId().equals(finalCustomer.getCustomerId())).toList();
        assertEquals(2, pointLedgerRows.size(), "Expected precisely two separate entries tracking point changes (1 EARN, 1 CLAWBACK)");

        PointLedgerEntry clawbackRow = pointLedgerRows.stream()
                .filter(row -> row.getTransactionType() == TransactionType.CLAWBACK)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Point ledger failed to write explicit CLAWBACK row line event item"));

        // Critical Point Audit Calculations:
        // The return matches the 'TXN-FLOW-BUY' invoice anchor.
        // Original multiplier when buying was 1.00 (Silver). 1 Laptop unit returned = $1000.00 * 1.00 = 1000.00 points clawed back.
        assertEquals(new BigDecimal("-1000.00"), clawbackRow.getPoints(), "Loyalty engine deducted an incorrect point balance volume during clawback!");

        // Summary Assessment: User earned 1100.00 points, lost 1000.00 points -> Net remaining points profile balance must equal 100.00
        BigDecimal netAvailablePoints = pointLedgerRows.stream()
                .map(PointLedgerEntry::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("100.00"), netAvailablePoints, "Net accumulated customer points balance calculation mismatched engine logic checks!");
    }

    @Test
    void verifyCustomerJourneyFromSilverToGoldAndRedeemingTwentyFivePercentOfPoints() throws Exception {

        String customerEmail = "redeem.raj@example.com";

        Customer customer = new Customer();
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail(customerEmail);
        customer.setPhoneNo("555-7711");
        customer.setCurrentTier(SILVER);
        customerRepository.save(customer);

        // =========================================================================
        // STEP 1: INITIAL PURCHASE -> Cross into GOLD Threshold ($1,200.00 Spend)
        // =========================================================================
        Map<String, Integer> buyItems = new HashMap<>();
        buyItems.put("Premium Laptop", 1);  // $1000.00
        buyItems.put("Ergonomic Mouse", 4);   // $200.00

        OrderRequestDTO buyPayload = new OrderRequestDTO(customerEmail, "TXN-REDEEM-BUY", buyItems);

        mockMvc.perform(post("/api/v1/orders/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buyPayload)))
                .andExpect(status().isCreated());

        Customer customerState1 = customerRepository.findByEmail(customerEmail).orElseThrow();
        assertEquals(GOLD, customerState1.getCurrentTier());

        // =========================================================================
        // STEP 2: REWARD REDEMPTION -> Deduct exactly 25% of point pool (300.00 points)
        // =========================================================================
        RedeemRewardDTO redeemPayload = new RedeemRewardDTO(customerEmail, "$25 Gift Card");

        mockMvc.perform(post("/api/v1/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemPayload)))
                .andExpect(status().isOk());

        // =========================================================================
        // CRITICAL DEEP AUDIT VALUATION: REDEMPTION STATE VERIFICATION
        // =========================================================================
        Customer finalCustomer = customerRepository.findByEmail(customerEmail).orElseThrow();
        assertEquals(GOLD, finalCustomer.getCurrentTier());

        List<CustomerProductLedgerEntry> productLedgerRows = customerProductLedgerRepository.findAll().stream()
                .filter(row -> row.getCustomerId().equals(finalCustomer.getCustomerId())).toList();
        assertEquals(2, productLedgerRows.size());

        List<PointLedgerEntry> pointLedgerRows = pointLedgerRepository.findAll().stream()
                .filter(row -> row.getCustomerId().equals(finalCustomer.getCustomerId())).toList();
        assertEquals(2, pointLedgerRows.size());

        PointLedgerEntry redeemRow = pointLedgerRows.stream()
                .filter(row -> row.getTransactionType() == TransactionType.REDEEM).findFirst().orElseThrow();
        assertEquals(new BigDecimal("-300.00"), redeemRow.getPoints());

        BigDecimal netAvailablePoints = pointLedgerRows.stream()
                .map(PointLedgerEntry::getPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("900.00"), netAvailablePoints);

        // =========================================================================
        // STEP 3: API CONTRACT CHECK -> Audit updated CustomerBalanceDTO fields
        // =========================================================================
        String jsonResponse = mockMvc.perform(get("/api/v1/loyalty/balance/email")
                        .param("email", customerEmail)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        CustomerBalanceDTO balanceDto = objectMapper.readValue(jsonResponse, CustomerBalanceDTO.class);

        assertNotNull(balanceDto);
        assertEquals("Raj", balanceDto.getFirstName());
        assertEquals(GOLD, balanceDto.getCurrentTier());
        assertEquals(new BigDecimal("900.00"), balanceDto.getPointsBalance());
        assertEquals(new BigDecimal("1200.00"), balanceDto.getRollingSpend());
    }
}