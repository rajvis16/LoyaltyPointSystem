package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.infrastructure.repository.PointLedgerEntryRepository;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.infrastructure.repository.RewardCatalogRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.mark43.loyalty.domain.entity.TransactionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integrates JUnit 5 with Mockito.
 * Automatically initializes @Mock fields and constructs @InjectMocks
 * dependencies via constructor injection before each test runs.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PointLedgerEntryRepository pointLedgerEntryRepository;

    @Mock
    private RewardCatalogRepository rewardCatalogRepository;

    @InjectMocks
    private LoyaltyServiceImpl loyaltyService;

    @Test
    void verifyIfCustomerCanBeRegisteredSuccessfully() {

        CustomerDTO dto = new CustomerDTO("Raj", "Singh",
                "raj@example.com", "555-1234", null);

        // Act
        loyaltyService.registerCustomer(dto);

        // Assert: Capture what was passed to the repository save method
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository, times(1)).save(customerCaptor.capture());

        Customer savedCustomer = customerCaptor.getValue();
        assertEquals("Raj", savedCustomer.getFirstName());
        assertEquals("Singh", savedCustomer.getLastName());
        assertEquals("raj@example.com", savedCustomer.getEmail());
        assertEquals("555-1234", savedCustomer.getPhoneNo());
        assertEquals(Tier.SILVER, savedCustomer.getCurrentTier());

        verify(customerRepository, times(1)).findByEmail(dto.getEmail());
        verify(customerRepository, times(1)).findByPhoneNo(dto.getPhoneNo());
    }

    @Test
    void verifyIfAnExceptionIsThrownWhenEmailAlreadyExist() {
        CustomerDTO dto = new CustomerDTO("Raj", "Singh", "duplicate@example.com",
                "555-1234", null);
        Customer existingCustomer = new Customer();

        when(customerRepository.findByEmail(dto.getEmail())).thenReturn(Optional.of(existingCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.registerCustomer(dto));

        assertEquals("A customer with email duplicate@example.com already exists.", exception.getMessage());

        // Ensure database writes were completely blocked
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void verifyIfAnExceptionIsThrownWhenPhoneNoAlreadyExist() {
        CustomerDTO dto = new CustomerDTO("Raj", "Singh", "duplicate@example.com",
                "555-1234", null);
        Customer existingCustomer = new Customer();

        when(customerRepository.findByPhoneNo(dto.getPhoneNo())).thenReturn(Optional.of(existingCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.registerCustomer(dto));

        assertEquals("A customer with phone no 555-1234 already exists.", exception.getMessage());

        // Ensure database writes were completely blocked
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void verifyIfPointsAreEarnedSuccessfullyWithTierMultiplier() {
        EarnPointsDTO earnPointsDTO = new EarnPointsDTO(
                "raj@example.com", "order-123", List.of("Designer Wool Coat")
        );

        Customer customer = new Customer();
        customer.setCustomerId(99L);
        customer.setEmail("raj@example.com");
        customer.setCurrentTier(Tier.GOLD); // 💡 1.5x Multiplier

        Product product = new Product(1L, "Designer Wool Coat", "Warm coat", new BigDecimal("100.00"));

        when(customerRepository.findByEmail(earnPointsDTO.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(pointLedgerEntryRepository.existsByPurchaseId(earnPointsDTO.getPurchaseReference())).thenReturn(false);
        when(productRepository.findByName("Designer Wool Coat")).thenReturn(Optional.of(product));

        loyaltyService.earnPoints(earnPointsDTO);

        // Capture the explicit object generated inside earnPoints
        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(1)).save(entryCaptor.capture());

        PointLedgerEntry savedEntry = entryCaptor.getValue();
        assertEquals(99L, savedEntry.getCustomerId());
        assertEquals(EARN, savedEntry.getTransactionType());
        assertEquals(new BigDecimal("150.00"), savedEntry.getPoints());
        assertEquals("order-123", savedEntry.getPurchaseId());
        assertNotNull(savedEntry.getExpiryDate());
        assertNull(savedEntry.getParentEntry());

        verify(productRepository, times(1)).findByName("Designer Wool Coat");
    }

    @Test
    void verifyIfEarnPointsIsSkippedWhenPurchaseReferenceIsDuplicate() {
        EarnPointsDTO dto = new EarnPointsDTO(
                "raj@example.com", "duplicate-order", List.of("Premium Leather Jacket")
        );

        Customer customer = new Customer();
        customer.setEmail("raj@example.com");

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(pointLedgerEntryRepository.existsByPurchaseId(dto.getPurchaseReference())).thenReturn(true);

        loyaltyService.earnPoints(dto);

        verify(productRepository, never()).findByName(anyString());
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfEarnPointsThrowsExceptionWhenCustomerDoesNotExist() {
        EarnPointsDTO dto = new EarnPointsDTO(
                "missing@example.com", "order-999", List.of("Wireless Earbuds")
        );

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.earnPoints(dto));

        assertEquals("Customer not found with email: missing@example.com", exception.getMessage());

        verify(pointLedgerEntryRepository, never()).existsByPurchaseId(anyString());
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfRedemptionSucceedsWithSingleSufficientEarnEntry() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Free Coffee");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        RewardCatalog reward = new RewardCatalog();
        reward.setRewardId(50L);
        reward.setName("Free Coffee");
        reward.setPointsRequired(new BigDecimal("30.00"));

        PointLedgerEntry earnEntry = new PointLedgerEntry();
        earnEntry.setTransactionType(EARN);
        earnEntry.setPoints(new BigDecimal("100.00"));
        earnEntry.setExpiryDate(LocalDateTime.now().plusMonths(6));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(customer.getCustomerId()))
                .thenReturn(List.of(earnEntry));

        loyaltyService.redeemReward(dto);

        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(1)).save(entryCaptor.capture());

        PointLedgerEntry savedEntry = entryCaptor.getValue();
        assertEquals(1L, savedEntry.getCustomerId());
        assertEquals(REDEEM, savedEntry.getTransactionType());
        assertEquals(new BigDecimal("-30.00"), savedEntry.getPoints());
        assertEquals(50L, savedEntry.getRewardId());
        assertNull(savedEntry.getParentEntry());
    }

    @Test
    void verifyIfRedemptionSucceedsWithMixedActiveLedgerBalances() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Movie Ticket");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        RewardCatalog reward = new RewardCatalog();
        reward.setRewardId(51L);
        reward.setName("Movie Ticket");
        reward.setPointsRequired(new BigDecimal("100.00"));

        PointLedgerEntry earn = new PointLedgerEntry();
        earn.setTransactionType(EARN);
        earn.setPoints(new BigDecimal("200.00"));
        earn.setExpiryDate(LocalDateTime.now().plusMonths(6));

        PointLedgerEntry redeem = new PointLedgerEntry();
        redeem.setTransactionType(REDEEM);
        redeem.setPoints(new BigDecimal("50.00"));

        PointLedgerEntry clawback = new PointLedgerEntry();
        clawback.setTransactionType(CLAWBACK);
        clawback.setPoints(new BigDecimal("30.00"));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(customer.getCustomerId()))
                .thenReturn(List.of(earn, redeem, clawback));

        loyaltyService.redeemReward(dto);

        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(1)).save(entryCaptor.capture());

        PointLedgerEntry savedEntry = entryCaptor.getValue();
        assertEquals(1L, savedEntry.getCustomerId());
        assertEquals(REDEEM, savedEntry.getTransactionType());
        assertEquals(new BigDecimal("-100.00"), savedEntry.getPoints());
        assertEquals(51L, savedEntry.getRewardId());
    }

    @Test
    void verifyIfRedemptionThrowsExceptionWhenLedgerContainsOnlyDeductions() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Gift Card");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        RewardCatalog reward = new RewardCatalog();
        reward.setName("Gift Card");
        reward.setPointsRequired(new BigDecimal("50.00"));

        PointLedgerEntry redeem = new PointLedgerEntry();
        redeem.setTransactionType(REDEEM);
        redeem.setPoints(new BigDecimal("20.00"));

        PointLedgerEntry clawback = new PointLedgerEntry();
        clawback.setTransactionType(CLAWBACK);
        clawback.setPoints(new BigDecimal("15.00"));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(customer.getCustomerId()))
                .thenReturn(List.of(redeem, clawback));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.redeemReward(dto));

        assertTrue(exception.getMessage().contains("Insufficient points balance"));
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfRedemptionFailsWhenSingleEarnEntryIsInsufficient() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Luxury Hoodie");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        RewardCatalog reward = new RewardCatalog();
        reward.setName("Luxury Hoodie");
        reward.setPointsRequired(new BigDecimal("50.00"));

        PointLedgerEntry earnEntry = new PointLedgerEntry();
        earnEntry.setTransactionType(EARN);
        earnEntry.setPoints(new BigDecimal("20.00"));
        earnEntry.setExpiryDate(LocalDateTime.now().plusMonths(6));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(customer.getCustomerId()))
                .thenReturn(List.of(earnEntry));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.redeemReward(dto));

        assertEquals("Insufficient points balance. Required: 50.00, Active Available: 20.00", exception.getMessage());
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfRedemptionIsRejectedUnderHeavyComplexInsufficientLedgerState() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Tech Gadget");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        RewardCatalog reward = new RewardCatalog();
        reward.setName("Tech Gadget");
        reward.setPointsRequired(new BigDecimal("50.00"));

        PointLedgerEntry e1 = createMockEntry("EARN", "100.00", 12);
        PointLedgerEntry e2 = createMockEntry("EARN", "50.00", 12);
        PointLedgerEntry e3 = createMockEntry("EARN", "30.00", 12);
        PointLedgerEntry e4 = createMockEntry("EARN", "20.00", 12);

        PointLedgerEntry r1 = createMockEntry("REDEEM", "80.00", 0);
        PointLedgerEntry r2 = createMockEntry("REDEEM", "40.00", 0);
        PointLedgerEntry r3 = createMockEntry("REDEEM", "20.00", 0);

        PointLedgerEntry c1 = createMockEntry("CLAWBACK", "30.00", 0);
        PointLedgerEntry c2 = createMockEntry("CLAWBACK", "20.00", 0);

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(customer.getCustomerId()))
                .thenReturn(List.of(e1, r1, e2, r2, e3, c1, e4, r3, c2));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> loyaltyService.redeemReward(dto));

        assertTrue(exception.getMessage().contains("Insufficient points balance"));
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfRedemptionThrowsExceptionWhenRewardCatalogNameIsMissing() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "NonExistentReward");

        Customer customer = new Customer();
        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.redeemReward(dto));

        assertEquals("Reward not found in catalog: NonExistentReward", exception.getMessage());
    }

    @Test
    void verifyIfRedemptionThrowsExceptionWhenPointsAreExpired() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Free Pass");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        RewardCatalog reward = new RewardCatalog();
        reward.setName("Free Pass");
        reward.setPointsRequired(new BigDecimal("100.00"));

        PointLedgerEntry expiredEarn = new PointLedgerEntry();
        expiredEarn.setTransactionType(EARN);
        expiredEarn.setPoints(new BigDecimal("150.00"));
        expiredEarn.setExpiryDate(LocalDateTime.now().minusMonths(1));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(customer.getCustomerId()))
                .thenReturn(List.of(expiredEarn));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> loyaltyService.redeemReward(dto));

        assertTrue(exception.getMessage().contains("Insufficient points balance"));
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfRedemptionIsRejectedWhenCustomerHasMassivePointsButAllAreExpired() {
        RedeemRewardDTO dto = new RedeemRewardDTO("raj@example.com", "Premium Jacket");

        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setEmail("raj@example.com");

        RewardCatalog reward = new RewardCatalog();
        reward.setName("Premium Jacket");
        reward.setPointsRequired(new BigDecimal("100.00"));

        PointLedgerEntry oldEarn1 = createMockEntry("EARN", "150.00", 0);
        oldEarn1.setExpiryDate(LocalDateTime.now().minusMonths(3));

        PointLedgerEntry oldEarn2 = createMockEntry("EARN", "100.00", 0);
        oldEarn2.setExpiryDate(LocalDateTime.now().minusMonths(2));

        PointLedgerEntry oldEarn3 = createMockEntry("EARN", "50.00", 0);
        oldEarn3.setExpiryDate(LocalDateTime.now().minusDays(1));

        when(customerRepository.findByEmail(dto.getCustomerEmail())).thenReturn(Optional.of(customer));
        when(rewardCatalogRepository.findByName(dto.getRewardName())).thenReturn(Optional.of(reward));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(customer.getCustomerId()))
                .thenReturn(List.of(oldEarn1, oldEarn2, oldEarn3));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> loyaltyService.redeemReward(dto));

        assertEquals("Insufficient points balance. Required: 100.00, Active Available: 0", exception.getMessage());

        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfClawbackSucceedsAndCreatesOffsettingEntryLinkedToParent() {
        String purchaseRef = "purchase-999";
        List<Long> returnedProductIds = Arrays.asList(101L, 102L);

        // 1. Setup original earning entry with your new tierPointUse snapshot column
        PointLedgerEntry originalEarn = new PointLedgerEntry();
        originalEarn.setPointLedgerEntryId(777L);
        originalEarn.setCustomerId(43L);
        originalEarn.setTransactionType(EARN);
        originalEarn.setPoints(new BigDecimal("150.00"));
        originalEarn.setPurchaseId(purchaseRef);
        originalEarn.setTierPointUsed(new BigDecimal("1.5")); // Gold snapshot rate

        // 2. Setup items inside the product catalog mock with explicit prices
        Product runningShoes = new Product(101L, "Running Shoes", "It is a running shoes", new BigDecimal("60.00"));
        Product socks = new Product(102L, "Socks", "It is a nice socks", new BigDecimal("10.00"));

        when(pointLedgerEntryRepository.findByPurchaseIdAndTransactionType(purchaseRef, EARN))
                .thenReturn(Optional.of(originalEarn));
        when(productRepository.findById(101L)).thenReturn(Optional.of(runningShoes));
        when(productRepository.findById(102L)).thenReturn(Optional.of(socks));

        // 3. Trigger the method under test with the new signature
        // Total refund value = $60 + $10 = $70. Proportional point deduction: $70 * 1.5 = 105.00 points
        loyaltyService.clawbackPoints(purchaseRef, returnedProductIds);

        // 4. Capture and verify the append-only record assertions
        ArgumentCaptor<PointLedgerEntry> entryCaptor = ArgumentCaptor.forClass(PointLedgerEntry.class);
        verify(pointLedgerEntryRepository, times(1)).save(entryCaptor.capture());

        PointLedgerEntry savedClawback = entryCaptor.getValue();
        assertEquals(43L, savedClawback.getCustomerId());
        assertEquals(CLAWBACK, savedClawback.getTransactionType());
        assertEquals(new BigDecimal("-105.00"), savedClawback.getPoints()); // Exact historical penalty value
        assertEquals(purchaseRef, savedClawback.getPurchaseId());
        assertEquals(originalEarn, savedClawback.getParentEntry()); // Linked cleanly
    }

    @Test
    void verifyIfClawbackThrowsExceptionWhenOriginalEarnEntryIsMissing() {
        String unknownPurchaseRef = "invalid-ref-000";
        List<Long> productIds = Collections.singletonList(101L);

        when(pointLedgerEntryRepository.findByPurchaseIdAndTransactionType(unknownPurchaseRef, EARN))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                loyaltyService.clawbackPoints(unknownPurchaseRef, productIds)
        );

        assertTrue(exception.getMessage().contains("No original earning entry found for purchase reference"));
        verify(productRepository, never()).findById(anyLong());
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfClawbackThrowsExceptionWhenProductIsMissingFromCatalog() {
        String purchaseRef = "purchase-999";
        List<Long> productIds = Collections.singletonList(404L); // Missing ID

        PointLedgerEntry originalEarn = new PointLedgerEntry();
        originalEarn.setCustomerId(43L);
        originalEarn.setTransactionType(EARN);
        originalEarn.setTierPointUsed(new BigDecimal("1.0"));

        when(pointLedgerEntryRepository.findByPurchaseIdAndTransactionType(purchaseRef, EARN))
                .thenReturn(Optional.of(originalEarn));
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                loyaltyService.clawbackPoints(purchaseRef, productIds)
        );

        assertTrue(exception.getMessage().contains("Product not found in catalog with ID: 404"));
        verify(pointLedgerEntryRepository, never()).save(any());
    }

    @Test
    void verifyIfClawbackIsGracefullySkippedWhenPurchaseReferenceIsNull() {
        List<Long> productIds = Collections.singletonList(101L);

        loyaltyService.clawbackPoints(null, productIds);

        verify(pointLedgerEntryRepository, never()).save(any(PointLedgerEntry.class));
    }

    @Test
    void verifyIfClawbackIsGracefullySkippedWhenPurchaseReferenceIsEmpty() {
        List<Long> productIds = Collections.singletonList(101L);

        loyaltyService.clawbackPoints("", productIds);

        verify(pointLedgerEntryRepository, never()).save(any(PointLedgerEntry.class));
    }

    @Test
    void verifyIfClawbackIsGracefullySkippedWhenProductListIsNull() {
        loyaltyService.clawbackPoints("purchase-999", null);

        verify(pointLedgerEntryRepository, never()).save(any(PointLedgerEntry.class));
    }

    @Test
    void verifyIfClawbackIsGracefullySkippedWhenProductListIsEmpty() {
        loyaltyService.clawbackPoints("purchase-999", Collections.emptyList());

        verify(pointLedgerEntryRepository, never()).save(any(PointLedgerEntry.class));
    }

    @Test
    void verifyIfCustomerBalanceCanBeRetrievedByEmailSuccessfully() {

        String email = "raj@example.com";
        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail(email);
        customer.setPhoneNo("555-1234");
        customer.setCurrentTier(Tier.PLATINUM);

        PointLedgerEntry earnEntry = new PointLedgerEntry();
        earnEntry.setTransactionType(EARN);
        earnEntry.setPoints(new BigDecimal("500.00"));
        earnEntry.setExpiryDate(LocalDateTime.now().plusMonths(6));

        when(customerRepository.findByEmail(email)).thenReturn(Optional.of(customer));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(1L))
                .thenReturn(List.of(earnEntry));

        CustomerBalanceDTO result = loyaltyService.getCustomerBalanceByEmail(email);

        assertNotNull(result);
        assertEquals("Raj", result.getFirstName());
        assertEquals("Singh", result.getLastName());
        assertEquals(email, result.getEmail());
        assertEquals("555-1234", result.getPhoneNo());
        assertEquals(Tier.PLATINUM, result.getCurrentTier());
        assertEquals(new BigDecimal("500.00"), result.getPointsBalance());

        verify(customerRepository, times(1)).findByEmail(email);
        verify(customerRepository, never()).findByPhoneNo(anyString());
    }

    @Test
    void verifyIfCustomerBalanceCanBeRetrievedByPhoneSuccessfully() {

        String phone = "555-1234";
        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setFirstName("Raj");
        customer.setLastName("Singh");
        customer.setEmail("raj@example.com");
        customer.setPhoneNo(phone);
        customer.setCurrentTier(Tier.GOLD);

        PointLedgerEntry earn = new PointLedgerEntry();
        earn.setTransactionType(EARN);
        earn.setPoints(new BigDecimal("200.00"));
        earn.setExpiryDate(LocalDateTime.now().plusMonths(6));

        PointLedgerEntry redeem = new PointLedgerEntry();
        redeem.setTransactionType(REDEEM);
        redeem.setPoints(new BigDecimal("50.00"));

        when(customerRepository.findByPhoneNo(phone)).thenReturn(Optional.of(customer));
        when(pointLedgerEntryRepository.findByCustomerIdOrderByPointLedgerEntryIdAsc(1L))
                .thenReturn(List.of(earn, redeem));

        CustomerBalanceDTO result = loyaltyService.getCustomerBalanceByPhone(phone);

        assertNotNull(result);
        assertEquals("Raj", result.getFirstName());
        assertEquals("raj@example.com", result.getEmail());
        assertEquals(phone, result.getPhoneNo());
        assertEquals(Tier.GOLD, result.getCurrentTier());
        assertEquals(new BigDecimal("150.00"), result.getPointsBalance());

        verify(customerRepository, times(1)).findByPhoneNo(phone);
        verify(customerRepository, never()).findByEmail(anyString());
    }

    @Test
    void verifyIfGetBalanceByEmailThrowsExceptionWhenCustomerNotFound() {

        String missingEmail = "notfound@example.com";
        when(customerRepository.findByEmail(missingEmail)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                loyaltyService.getCustomerBalanceByEmail(missingEmail)
        );

        assertEquals("Customer not found with email: " + missingEmail, exception.getMessage());
        verify(pointLedgerEntryRepository, never()).findByCustomerIdOrderByPointLedgerEntryIdAsc(anyLong());
    }

    @Test
    void verifyIfGetBalanceByPhoneThrowsExceptionWhenCustomerNotFound() {

        String missingPhone = "000-0000";
        when(customerRepository.findByPhoneNo(missingPhone)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                loyaltyService.getCustomerBalanceByPhone(missingPhone)
        );

        assertEquals("Customer not found with phone number: " + missingPhone, exception.getMessage());
        verify(pointLedgerEntryRepository, never()).findByCustomerIdOrderByPointLedgerEntryIdAsc(anyLong());
    }

    private PointLedgerEntry createMockEntry(String type, String pointsValue, int plusMonths) {
        PointLedgerEntry entry = new PointLedgerEntry();
        entry.setTransactionType(TransactionType.valueOf(type));
        entry.setPoints(new BigDecimal(pointsValue));
        if (plusMonths > 0) {
            entry.setExpiryDate(LocalDateTime.now().plusMonths(plusMonths));
        }
        return entry;
    }
}