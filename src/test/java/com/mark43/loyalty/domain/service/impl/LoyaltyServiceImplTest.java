package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.infrastructure.repository.*;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import com.mark43.loyalty.interfaces.dto.EarnPointsDTO;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.mark43.loyalty.domain.entity.Tier.GOLD;
import static com.mark43.loyalty.domain.entity.Tier.SILVER;
import static com.mark43.loyalty.domain.entity.TransactionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerProductLedgerRepository customerProductLedgerRepository;

    @Mock
    private PointLedgerEntryRepository pointLedgerEntryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RewardRepository rewardRepository;

    @Mock
    private LoyaltyCacheManager cacheManager;

    @InjectMocks
    private LoyaltyServiceImpl loyaltyService;

    @BeforeEach
    void setUp() {
        loyaltyService = new LoyaltyServiceImpl(
                customerRepository,
                customerProductLedgerRepository,
                pointLedgerEntryRepository,
                productRepository,
                rewardRepository,
                cacheManager
        );

    }

    @Test
    void verifyIfCustomerCanBeRegisteredSuccessfully() {

        CustomerDTO dto = new CustomerDTO("Raj", "Singh",
                "raj@example.com", "555-1234", SILVER, new BigDecimal(0), new BigDecimal(0), null);

        loyaltyService.registerCustomer(dto);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository, times(1)).save(customerCaptor.capture());

        Customer savedCustomer = customerCaptor.getValue();
        assertEquals("Raj", savedCustomer.getFirstName());
        assertEquals("Singh", savedCustomer.getLastName());
        assertEquals("raj@example.com", savedCustomer.getEmail());
        assertEquals("555-1234", savedCustomer.getPhoneNo());
        assertEquals(SILVER, savedCustomer.getCurrentTier());

        verify(customerRepository, times(1)).findByEmail(dto.getEmail());
        verify(customerRepository, times(1)).findByPhoneNo(dto.getPhoneNo());
    }

    @Test
    void verifyIfAnExceptionIsThrownWhenEmailAlreadyExist() {

        CustomerDTO dto = new CustomerDTO("Raj", "Singh", "duplicate@example.com", "555-1234", SILVER, new BigDecimal(0), new BigDecimal(0), null);
        when(customerRepository.findByEmail(dto.getEmail())).thenReturn(Optional.of(new Customer()));

        assertThrows(IllegalArgumentException.class, () -> loyaltyService.registerCustomer(dto));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void verifyIfAnExceptionIsThrownWhenPhoneNoAlreadyExist() {

        CustomerDTO dto = new CustomerDTO("Raj", "Singh", "duplicate@example.com", "555-1234", SILVER, new BigDecimal(0), new BigDecimal(0), null);

        when(customerRepository.findByPhoneNo(dto.getPhoneNo())).thenReturn(Optional.of(new Customer()));

        assertThrows(IllegalArgumentException.class, () -> loyaltyService.registerCustomer(dto));

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void verifyIfPointsAreEarnedSuccessfullyWithTierMultiplierAndTriggersTierCheck() {

        EarnPointsDTO earnPointsDTO = new EarnPointsDTO(
                "raj@example.com", "order-123", List.of("Designer Wool Coat"), new BigDecimal("100.00")
        );

        Customer customer = new Customer();
        customer.setCustomerId(99L);
        customer.setEmail("raj@example.com");
        customer.setCurrentTier(GOLD); // 1.5x Multiplier

        when(customerRepository.findByEmail(earnPointsDTO.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(pointLedgerEntryRepository.existsByPurchaseId(earnPointsDTO.getPurchaseReference())).thenReturn(false);

        when(customerProductLedgerRepository.calculateNetRollingSpend(eq(99L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("600.00"));

        loyaltyService.earnPoints(earnPointsDTO);

        verify(customerProductLedgerRepository, times(1)).flush();

        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(1)).save(entryCaptor.capture());

        PointLedgerEntry savedEntry = entryCaptor.getValue();
        assertEquals(99L, savedEntry.getCustomerId());
        assertEquals(EARN, savedEntry.getTransactionType());
        assertEquals(new BigDecimal("150.00"), savedEntry.getPoints()); // 100.00 * 1.5
        assertEquals(new BigDecimal("150.00"), savedEntry.getRemainingPoints()); // 💡 Asserts Option A initialization match
        assertEquals(new BigDecimal("1.5"), savedEntry.getTierPointUsed());
        assertEquals("order-123", savedEntry.getPurchaseId());
    }

    @Test
    void verifyIfEarnPointsIsSkippedWhenPurchaseReferenceIsDuplicate() {

        EarnPointsDTO dto = new EarnPointsDTO("raj@example.com", "duplicate-order", List.of("Premium Jacket"), new BigDecimal(10));
        Customer customer = new Customer();
        customer.setEmail("raj@example.com");

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(pointLedgerEntryRepository.existsByPurchaseId(dto.getPurchaseReference())).thenReturn(true);

        loyaltyService.earnPoints(dto);

        verify(pointLedgerEntryRepository, never()).save(any());
        verify(customerProductLedgerRepository, never()).flush();
    }

    @Test
    void verifyIfRedemptionSucceedsWithFIFOExpiryBucketPeeling() {

        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "$15 Store Checkout Credit");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        Reward reward = new Reward();
        reward.setRewardId(50L);
        reward.setName("$15 Store Checkout Credit");
        reward.setPointsRequired(new BigDecimal("150.00")); // Needs 150 points

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));

        when(pointLedgerEntryRepository.calculateActivePointsBalance(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("200.00"));

        // Setup mock pools to evaluate the sequential FIFO algorithm loops
        PointLedgerEntry bucket1 = new PointLedgerEntry();
        bucket1.setTransactionType(EARN);
        bucket1.setRemainingPoints(new BigDecimal("100.00")); // Oldest bucket has 100 points

        PointLedgerEntry bucket2 = new PointLedgerEntry();
        bucket2.setTransactionType(EARN);
        bucket2.setRemainingPoints(new BigDecimal("100.00")); // Second bucket has 100 points

        List<PointLedgerEntry> mockActiveBuckets = new ArrayList<>(List.of(bucket1, bucket2));
        when(pointLedgerEntryRepository.findAvailableEarnEntries(eq(1L), any(LocalDateTime.class)))
                .thenReturn(mockActiveBuckets);

        loyaltyService.redeemReward(dto);

        // Verify that Bucket 1 was completely emptied
        assertEquals(BigDecimal.ZERO, bucket1.getRemainingPoints());
        // Verify that Bucket 2 covered the remaining 50 points needed (100 - 50)
        assertEquals(new BigDecimal("50.00"), bucket2.getRemainingPoints());

        // Verify that both active buckets were updated and saved during the loop iterations
        verify(pointLedgerEntryRepository, times(1)).save(bucket1);
        verify(pointLedgerEntryRepository, times(1)).save(bucket2);

        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(3)).save(entryCaptor.capture());

        List<PointLedgerEntry> capturedEntries = entryCaptor.getAllValues();
        PointLedgerEntry savedRedeemRecord = capturedEntries.get(2);

        assertEquals(1L, savedRedeemRecord.getCustomerId());
        assertEquals(REDEEM, savedRedeemRecord.getTransactionType());
        assertEquals(new BigDecimal("-150.00"), savedRedeemRecord.getPoints());
        assertEquals(BigDecimal.ZERO, savedRedeemRecord.getRemainingPoints());
        assertEquals(50L, savedRedeemRecord.getRewardId());
    }

    @Test
    void verifyIfRedemptionFailsWhenDatabaseSummationIsInsufficient() {

        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Luxury Hoodie");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        Reward reward = new Reward();
        reward.setName("Luxury Hoodie");
        reward.setPointsRequired(new BigDecimal("50.00"));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));

        when(pointLedgerEntryRepository.calculateActivePointsBalance(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("20.00"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.redeemReward(dto));

        assertEquals("Insufficient points balance. Required: 50.00, Active Available: 20.00", exception.getMessage());
        verify(pointLedgerEntryRepository, never()).findAvailableEarnEntries(anyLong(), any());
    }

    @Test
    void verifyIfClawbackSucceedsUsingOptionADirectReductionAndHistoricalMultiplier() {

        String purchaseRef = "purchase-999";
        List<Long> returnedProductIds = Arrays.asList(101L, 102L);

        PointLedgerEntry originalEarn = new PointLedgerEntry();
        originalEarn.setPointLedgerEntryId(777L);
        originalEarn.setCustomerId(43L);
        originalEarn.setTransactionType(EARN);
        originalEarn.setRemainingPoints(new BigDecimal("150.00")); // Parent currently has 150 points remaining
        originalEarn.setTierPointUsed(new BigDecimal("1.5")); // Gold snapshot rate

        Customer customer = new Customer();
        customer.setCustomerId(43L);
        customer.setCurrentTier(GOLD);

        when(pointLedgerEntryRepository.findByPurchaseIdAndTransactionType(purchaseRef, EARN))
                .thenReturn(Optional.of(originalEarn));
        when(customerRepository.findById(43L)).thenReturn(Optional.of(customer));

        Product product1 = new Product();
        product1.setProductId(101L);
        product1.setPrice(new BigDecimal("40.00"));

        Product product2 = new Product();
        product2.setProductId(102L);
        product2.setPrice(new BigDecimal("30.00")); // $40.00 + $30.00 = $70.00 cumulative refund total

        List<Product> mockCatalogBatch = Arrays.asList(product1, product2);
        when(productRepository.findByProductIdIn(returnedProductIds)).thenReturn(mockCatalogBatch);

        when(customerProductLedgerRepository.calculateNetRollingSpend(eq(43L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("600.00"));

        loyaltyService.clawbackPoints(purchaseRef, returnedProductIds);

        // 1. Verify that the parent entry's remaining points were dropped by 105.00 (150 - 105 = 45)
        assertEquals(new BigDecimal("45.00"), originalEarn.getRemainingPoints());
        verify(pointLedgerEntryRepository, times(1)).save(originalEarn);

        // 2. Capture both total invocations that hit the repository save gateway
        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(2)).save(entryCaptor.capture());

        // 3. Extract the 2nd invocation (index 1), which represents the final CLAWBACK audit record
        List<PointLedgerEntry> capturedEntries = entryCaptor.getAllValues();
        PointLedgerEntry savedClawback = capturedEntries.get(1);

        assertEquals(43L, savedClawback.getCustomerId());
        assertEquals(CLAWBACK, savedClawback.getTransactionType());
        assertEquals(new BigDecimal("-105.00"), savedClawback.getPoints()); // $70.00 * 1.5 historical rate
        assertEquals(BigDecimal.ZERO, savedClawback.getRemainingPoints()); // Clawbacks can't be spend pools
        assertEquals(originalEarn, savedClawback.getParentEntry());

        verify(customerProductLedgerRepository, times(1)).flush();
    }

    @Test
    void verifyIfCustomerBalanceCanBeRetrievedByEmailUsingOptimizedQuery() {

        String email = "raj@example.com";
        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail(email);
        customer.setCurrentTier(Tier.PLATINUM);

        when(customerRepository.findByEmail(email)).thenReturn(Optional.of(customer));
        when(pointLedgerEntryRepository.calculateActivePointsBalance(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("500.00"));

        CustomerDTO result = loyaltyService.getCustomerByEmail(email);

        assertNotNull(result);
        assertEquals("Raj", result.getFirstName());
        assertEquals(Tier.PLATINUM, result.getCurrentTier());
        assertEquals(new BigDecimal("500.00"), result.getPointsBalance());

        verify(pointLedgerEntryRepository, never()).findByCustomerIdOrderByPointLedgerEntryIdAsc(anyLong());
    }
}