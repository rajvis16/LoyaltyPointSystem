package com.mark43.loyalty.interfaces.rest;

import tools.jackson.databind.ObjectMapper;
import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.entity.PointLedgerEntry;
import com.mark43.loyalty.domain.entity.Reward;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.infrastructure.repository.PointLedgerEntryRepository;
import com.mark43.loyalty.infrastructure.repository.RewardRepository;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static com.mark43.loyalty.domain.entity.TransactionType.EARN;
import static com.mark43.loyalty.domain.entity.TransactionType.REDEEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoyaltyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PointLedgerEntryRepository pointLedgerEntryRepository;

    @Autowired
    private RewardRepository rewardRepository;

    private Customer testCustomer;
    private Reward testReward;

    private Long row1Id;
    private Long row2Id;
    private Long row3Id;
    private Long row4Id;

    @BeforeEach
    void setupIntegrationTestData() {

        pointLedgerEntryRepository.deleteAll();
        rewardRepository.deleteAll();
        customerRepository.deleteAll();

        Customer customer = new Customer();
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail("raj.singh@example.com");
        customer.setPhoneNo("555-9876");
        customer.setCurrentTier(com.mark43.loyalty.domain.entity.Tier.SILVER);
        testCustomer = customerRepository.save(customer);

        Reward reward = new Reward();
        reward.setName("Premium Gadget Voucher");
        reward.setPointsRequired(new BigDecimal("50.00"));
        reward.setDescription("Valid for premium system gadget redemptions.");
        testReward = rewardRepository.save(reward);

        LocalDateTime now = LocalDateTime.now();

        // Row 1: 30 points, closest to expiry (expires in 1 hour)
        PointLedgerEntry row1 = createEarnBucket(testCustomer.getCustomerId(), new BigDecimal("30.00"), now.plusHours(1), "BILL-001");
        row1Id = row1.getPointLedgerEntryId();

        // Row 2: 15 points, slightly longer expiry (expires in 1 day)
        PointLedgerEntry row2 = createEarnBucket(testCustomer.getCustomerId(), new BigDecimal("15.00"), now.plusDays(1), "BILL-002");
        row2Id = row2.getPointLedgerEntryId();

        // Row 3: 15 points, recent collection tier (expires in 1 month)
        PointLedgerEntry row3 = createEarnBucket(testCustomer.getCustomerId(), new BigDecimal("15.00"), now.plusMonths(1), "BILL-003");
        row3Id = row3.getPointLedgerEntryId();

        // Row 4: 100 points, created today with long-term timeline (expires in 1 year)
        PointLedgerEntry row4 = createEarnBucket(testCustomer.getCustomerId(), new BigDecimal("100.00"), now.plusYears(1), "BILL-004");
        row4Id = row4.getPointLedgerEntryId();
    }

    @Test
    void verifyFIFOBucketPeelingLogicAcrossMultipleLedgerRowsDuringRedemption() throws Exception {

        RedeemRewardDTO redeemPayload = new RedeemRewardDTO("raj.singh@example.com", "Premium Gadget Voucher");

        // 💡 FIXED ENDPOINT MAPPING
        mockMvc.perform(post("/api/v1/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemPayload)))
                .andExpect(status().isOk());

        PointLedgerEntry updatedRow1 = pointLedgerEntryRepository.findById(row1Id).orElseThrow();
        PointLedgerEntry updatedRow2 = pointLedgerEntryRepository.findById(row2Id).orElseThrow();
        PointLedgerEntry updatedRow3 = pointLedgerEntryRepository.findById(row3Id).orElseThrow();
        PointLedgerEntry updatedRow4 = pointLedgerEntryRepository.findById(row4Id).orElseThrow();

        assertEquals(new BigDecimal("0.00"), updatedRow1.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.00"), updatedRow2.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("10.00"), updatedRow3.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("100.00"), updatedRow4.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));

        List<PointLedgerEntry> globalLedger = pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(testCustomer.getCustomerId());
        assertEquals(5, globalLedger.size());

        PointLedgerEntry finalAuditLine = globalLedger.get(4);
        assertEquals(REDEEM, finalAuditLine.getTransactionType());
        assertEquals(new BigDecimal("-50.00"), finalAuditLine.getPoints());
        assertEquals(BigDecimal.ZERO, finalAuditLine.getRemainingPoints());
        assertEquals(testReward.getRewardId(), finalAuditLine.getRewardId());
    }

    @Test
    void verifyExactMatchBoundaryConditionClearsDebtPerfectly() throws Exception {
        Reward boundaryReward = new Reward();
        boundaryReward.setName("Exact Cost Reward");
        boundaryReward.setPointsRequired(new BigDecimal("45.00"));
        boundaryReward.setDescription("Tests clean exact-zero loops.");
        rewardRepository.save(boundaryReward);

        RedeemRewardDTO redeemPayload = new RedeemRewardDTO("raj.singh@example.com", "Exact Cost Reward");

        // 💡 FIXED ENDPOINT MAPPING
        mockMvc.perform(post("/api/v1/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemPayload)))
                .andExpect(status().isOk());

        PointLedgerEntry updatedRow1 = pointLedgerEntryRepository.findById(row1Id).orElseThrow();
        PointLedgerEntry updatedRow2 = pointLedgerEntryRepository.findById(row2Id).orElseThrow();
        PointLedgerEntry updatedRow3 = pointLedgerEntryRepository.findById(row3Id).orElseThrow();

        assertEquals(new BigDecimal("0.00"), updatedRow1.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.00"), updatedRow2.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("15.00"), updatedRow3.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void verifyMidLoopSkipIgnoresHistoricallySpentEarningRows() throws Exception {
        PointLedgerEntry row1 = pointLedgerEntryRepository.findById(row1Id).orElseThrow();
        row1.setRemainingPoints(BigDecimal.ZERO);
        pointLedgerEntryRepository.save(row1);

        PointLedgerEntry row2 = pointLedgerEntryRepository.findById(row2Id).orElseThrow();
        row2.setRemainingPoints(BigDecimal.ZERO);
        pointLedgerEntryRepository.save(row2);

        Reward quickReward = new Reward();
        quickReward.setName("Quick Item");
        quickReward.setPointsRequired(new BigDecimal("10.00"));
        quickReward.setDescription("Quick ten point test.");
        rewardRepository.save(quickReward);

        RedeemRewardDTO redeemPayload = new RedeemRewardDTO("raj.singh@example.com", "Quick Item");

        // 💡 FIXED ENDPOINT MAPPING
        mockMvc.perform(post("/api/v1/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemPayload)))
                .andExpect(status().isOk());

        PointLedgerEntry updatedRow3 = pointLedgerEntryRepository.findById(row3Id).orElseThrow();
        PointLedgerEntry updatedRow4 = pointLedgerEntryRepository.findById(row4Id).orElseThrow();

        assertEquals(new BigDecimal("5.00"), updatedRow3.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("100.00"), updatedRow4.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void verifyExcludingExpiredBucketsBypassesDeadAssets() throws Exception {
        PointLedgerEntry row1 = pointLedgerEntryRepository.findById(row1Id).orElseThrow();
        row1.setExpiryDate(LocalDateTime.now().minusDays(5));
        pointLedgerEntryRepository.save(row1);

        Reward bypassReward = new Reward();
        bypassReward.setName("Bypass Reward");
        bypassReward.setPointsRequired(new BigDecimal("20.00"));
        bypassReward.setDescription("Testing expiration filter.");
        rewardRepository.save(bypassReward);

        RedeemRewardDTO redeemPayload = new RedeemRewardDTO("raj.singh@example.com", "Bypass Reward");

        // 💡 FIXED ENDPOINT MAPPING
        mockMvc.perform(post("/api/v1/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemPayload)))
                .andExpect(status().isOk());

        PointLedgerEntry updatedRow1 = pointLedgerEntryRepository.findById(row1Id).orElseThrow();
        PointLedgerEntry updatedRow2 = pointLedgerEntryRepository.findById(row2Id).orElseThrow();
        PointLedgerEntry updatedRow3 = pointLedgerEntryRepository.findById(row3Id).orElseThrow();

        assertEquals(new BigDecimal("30.00"), updatedRow1.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.00"), updatedRow2.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("10.00"), updatedRow3.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void verifyTransactionRollbackWhenInsufficientFundsSimulatedViaHighDemand() throws Exception {
        Reward expensiveReward = new Reward();
        expensiveReward.setName("Ultra Luxury Experience");
        expensiveReward.setPointsRequired(new BigDecimal("500.00"));
        expensiveReward.setDescription("Requires a massive balance pool.");
        rewardRepository.save(expensiveReward);

        RedeemRewardDTO redeemPayload = new RedeemRewardDTO("raj.singh@example.com", "Ultra Luxury Experience");

        // 💡 FIXED ENDPOINT MAPPING
        mockMvc.perform(post("/api/v1/loyalty/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redeemPayload)))
                .andExpect(status().isBadRequest());

        PointLedgerEntry rolledBackRow1 = pointLedgerEntryRepository.findById(row1Id).orElseThrow();
        PointLedgerEntry rolledBackRow2 = pointLedgerEntryRepository.findById(row2Id).orElseThrow();
        PointLedgerEntry rolledBackRow3 = pointLedgerEntryRepository.findById(row3Id).orElseThrow();
        PointLedgerEntry rolledBackRow4 = pointLedgerEntryRepository.findById(row4Id).orElseThrow();

        assertEquals(new BigDecimal("30.00"), rolledBackRow1.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("15.00"), rolledBackRow2.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("15.00"), rolledBackRow3.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("100.00"), rolledBackRow4.getRemainingPoints().setScale(2, RoundingMode.HALF_UP));

        List<PointLedgerEntry> globalLedger = pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(testCustomer.getCustomerId());
        assertEquals(4, globalLedger.size());
    }

    private PointLedgerEntry createEarnBucket(Long customerId, BigDecimal points, LocalDateTime expiry, String purchaseId) {
        PointLedgerEntry entry = new PointLedgerEntry();
        entry.setCustomerId(customerId);
        entry.setTransactionType(EARN);
        entry.setPoints(points);
        entry.setRemainingPoints(points);
        entry.setExpiryDate(expiry);
        entry.setCreatedAtDate(LocalDateTime.now());
        entry.setPurchaseId(purchaseId);
        entry.setTierPointUsed(new BigDecimal("1.0"));
        return pointLedgerEntryRepository.save(entry);
    }
}