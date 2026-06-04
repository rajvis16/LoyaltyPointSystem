package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.infrastructure.repository.*;
import com.mark43.loyalty.interfaces.dto.CustomerBalanceDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import com.mark43.loyalty.interfaces.dto.EarnPointsDTO;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @InjectMocks
    private LoyaltyServiceImpl loyaltyService;

    @Test
    void verifyIfCustomerCanBeRegisteredSuccessfully() {

        CustomerDTO dto = new CustomerDTO("Raj", "Singh",
                "raj@example.com", "555-1234", SILVER, null);

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

        CustomerDTO dto = new CustomerDTO("Raj", "Singh", "duplicate@example.com", "555-1234", SILVER, null);
        when(customerRepository.findByEmail(dto.getEmail())).thenReturn(Optional.of(new Customer()));

        assertThrows(IllegalArgumentException.class, () -> loyaltyService.registerCustomer(dto));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void verifyIfAnExceptionIsThrownWhenPhoneNoAlreadyExist() {

        CustomerDTO dto = new CustomerDTO("Raj", "Singh", "duplicate@example.com", "555-1234", SILVER, null);
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
        assertEquals(new BigDecimal("1.5"), savedEntry.getTierPointUsed()); // Secured historical snapshot check
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
    void verifyIfRedemptionSucceedsWithFastDatabaseSummationCheck() {

        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Free Coffee");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        Reward reward = new Reward();
        reward.setRewardId(50L);
        reward.setName("Free Coffee");
        reward.setPointsRequired(new BigDecimal("30.00"));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));

        when(pointLedgerEntryRepository.calculateActivePointsBalance(customer.getCustomerId()))
                .thenReturn(new BigDecimal("100.00"));

        loyaltyService.redeemReward(dto);

        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(1)).save(entryCaptor.capture());

        PointLedgerEntry savedEntry = entryCaptor.getValue();
        assertEquals(1L, savedEntry.getCustomerId());
        assertEquals(REDEEM, savedEntry.getTransactionType());
        assertEquals(new BigDecimal("-30.00"), savedEntry.getPoints());
        assertEquals(50L, savedEntry.getRewardId());
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

        when(pointLedgerEntryRepository.calculateActivePointsBalance(customer.getCustomerId()))
                .thenReturn(new BigDecimal("20.00"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.redeemReward(dto));

        assertEquals("Insufficient points balance. Required: 50.00, Active Available: 20.00", exception.getMessage());
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfClawbackSucceedsUsingIntelligentSqlSummationAndHistoricalMultiplier() {

        String purchaseRef = "purchase-999";
        List<Long> returnedProductIds = Arrays.asList(101L, 102L);

        PointLedgerEntry originalEarn = new PointLedgerEntry();
        originalEarn.setPointLedgerEntryId(777L);
        originalEarn.setCustomerId(43L);
        originalEarn.setTransactionType(EARN);
        originalEarn.setTierPointUsed(new BigDecimal("1.5")); // Gold snapshot rate

        Customer customer = new Customer();
        customer.setCustomerId(43L);
        customer.setCurrentTier(GOLD);

        when(pointLedgerEntryRepository.findByPurchaseIdAndTransactionType(purchaseRef, EARN))
                .thenReturn(Optional.of(originalEarn));
        when(customerRepository.findById(43L)).thenReturn(Optional.of(customer));

        when(productRepository.calculateTotalSumByIds(returnedProductIds)).thenReturn(new BigDecimal("70.00"));

        when(customerProductLedgerRepository.calculateNetRollingSpend(eq(43L), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("600.00"));

        loyaltyService.clawbackPoints(purchaseRef, returnedProductIds);

        verify(customerProductLedgerRepository, times(1)).flush();

        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(1)).save(entryCaptor.capture());

        PointLedgerEntry savedClawback = entryCaptor.getValue();
        assertEquals(43L, savedClawback.getCustomerId());
        assertEquals(CLAWBACK, savedClawback.getTransactionType());
        assertEquals(new BigDecimal("-105.00"), savedClawback.getPoints()); // $70.00 * 1.5 historical rate
        assertEquals(originalEarn, savedClawback.getParentEntry());
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
        when(pointLedgerEntryRepository.calculateActivePointsBalance(1L)).thenReturn(new BigDecimal("500.00"));

        CustomerBalanceDTO result = loyaltyService.getCustomerBalanceByEmail(email);

        assertNotNull(result);
        assertEquals("Raj", result.getFirstName());
        assertEquals(Tier.PLATINUM, result.getCurrentTier());
        assertEquals(new BigDecimal("500.00"), result.getPointsBalance());

        verify(pointLedgerEntryRepository, never()).findByCustomerIdOrderByPointLedgerEntryIdAsc(anyLong());
    }
}