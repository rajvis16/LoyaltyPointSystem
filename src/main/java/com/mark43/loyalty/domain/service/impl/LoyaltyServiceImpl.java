package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.infrastructure.repository.*;
import com.mark43.loyalty.interfaces.dto.CustomerBalanceDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import com.mark43.loyalty.interfaces.dto.EarnPointsDTO;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static com.mark43.loyalty.domain.entity.Tier.SILVER;
import static com.mark43.loyalty.domain.entity.TransactionType.*;

@Transactional
@Log4j2
@Service
public class LoyaltyServiceImpl implements LoyaltyService {

    private final CustomerRepository customerRepository;
    private final CustomerProductLedgerRepository customerProductLedgerRepository;
    private final PointLedgerEntryRepository pointLedgerEntryRepository;
    private final ProductRepository productRepository;
    private final RewardRepository rewardRepository;

    public LoyaltyServiceImpl(CustomerRepository customerRepository,
                              CustomerProductLedgerRepository customerProductLedgerRepository,
                              PointLedgerEntryRepository pointLedgerEntryRepository,
                              ProductRepository productRepository,
                              RewardRepository rewardRepository) {
        this.customerRepository = customerRepository;
        this.customerProductLedgerRepository = customerProductLedgerRepository;
        this.pointLedgerEntryRepository = pointLedgerEntryRepository;
        this.productRepository = productRepository;
        this.rewardRepository = rewardRepository;
    }

    @Override
    public void registerCustomer(CustomerDTO customerDTO) {

        if (customerRepository.findByEmail(customerDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("A customer with email " + customerDTO.getEmail() +
                    " already exists.");
        }

        if (customerRepository.findByPhoneNo(customerDTO.getPhoneNo()).isPresent()) {
            throw new IllegalArgumentException("A customer with phone no " + customerDTO.getPhoneNo() +
                    " already exists.");
        }

        Customer customer = new Customer();
        customer.setFirstName(customerDTO.getFirstName());
        customer.setLastName(customerDTO.getLastName());
        customer.setEmail(customerDTO.getEmail());
        customer.setPhoneNo(customerDTO.getPhoneNo());

        customer.setCurrentTier(SILVER); // Default to SILVER tier for new customer

        customerRepository.save(customer);
    }

    @Override
    public void earnPoints(EarnPointsDTO earnPointsDTO) {

        Customer customer = customerRepository.findByEmail(earnPointsDTO.getCustomerEmail())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: "
                        + earnPointsDTO.getCustomerEmail()));

        // Idempotency Check - Points should be earned only once against a purchase reference (bill)
        if (pointLedgerEntryRepository.existsByPurchaseId(earnPointsDTO.getPurchaseReference())) {
            log.warn("Duplicate processing attempt detected for purchase reference: {}. " +
                    "Skipping allocation.", earnPointsDTO.getPurchaseReference());
            return;
        }

        // Resolve multi-tier multiplier rules
        BigDecimal multiplier = switch (customer.getCurrentTier()) {
            case SILVER -> new BigDecimal("1.0");
            case GOLD -> new BigDecimal("1.5");
            case PLATINUM -> new BigDecimal("2.0");
        };

        // Calculate new points earned
        BigDecimal pointsToAward = earnPointsDTO.getTotalSpend().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        PointLedgerEntry earnEntry = new PointLedgerEntry();
        earnEntry.setCustomerId(customer.getCustomerId());
        earnEntry.setTransactionType(EARN);
        earnEntry.setPoints(pointsToAward);
        earnEntry.setTierPointUsed(multiplier);
        earnEntry.setPurchaseId(earnPointsDTO.getPurchaseReference());
        earnEntry.setExpiryDate(LocalDateTime.now().plusYears(1)); // Points valid for 1 year
        earnEntry.setParentEntry(null);

        pointLedgerEntryRepository.save(earnEntry);

        // =========================================================================
        // HIBERNATE FLUSH OPTIMIZATION:
        // Force Hibernate to sync its cache with the database storage engine.
        // This pushes any 'BOUGHT' rows saved by the calling service into the
        // database indexes immediately, guaranteeing our JPQL aggregate query
        // doesn't read stale data.
        // =========================================================================
        customerProductLedgerRepository.flush();

        // Now check if the customer tier could rolled to a new tier
        // First, get the last 12 month net spending of the customer
        BigDecimal netRollingSpend = customerProductLedgerRepository.calculateNetRollingSpend(
                customer.getCustomerId(), LocalDateTime.now().minusYears(1)
        );

        // See if the customer goes to the new tier
        updateCustomerTier(customer, netRollingSpend);

        log.info("Successfully allocated {} points to customer ID: {}.",
                pointsToAward, customer.getCustomerId());

    }

    @Override
    public void redeemReward(RedeemRewardDTO redeemRewardDTO) {

        Customer customer = customerRepository.findByEmail(redeemRewardDTO.getCustomerEmail())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: "
                        + redeemRewardDTO.getCustomerEmail()));

        Reward reward = rewardRepository.findByName(redeemRewardDTO.getRewardName())
                .orElseThrow(() -> new IllegalArgumentException("Reward not found in catalog: "
                        + redeemRewardDTO.getRewardName()));

        BigDecimal pointsNeeded = reward.getPointsRequired();

        // Fast, single-row database summation!
        BigDecimal totalAvailablePoints = pointLedgerEntryRepository.calculateActivePointsBalance(customer.getCustomerId());

        if (totalAvailablePoints.compareTo(pointsNeeded) < 0) {
            throw new IllegalArgumentException("Insufficient points balance. Required: " + pointsNeeded +
                    ", Active Available: " + totalAvailablePoints);
        }

        // At this point, we have sufficient earning point to redeem them.
        // So, after redeeming we create a brand new row in the point_ledger_entries
        // table and in this row we have assign negative point value.
        // Check how we don't mess with the older records. They exist as they are!
        PointLedgerEntry redemptionEntry = new PointLedgerEntry();
        redemptionEntry.setCustomerId(customer.getCustomerId());
        redemptionEntry.setTransactionType(REDEEM);
        redemptionEntry.setPoints(pointsNeeded.negate()); //Negative point value
        redemptionEntry.setRewardId(reward.getRewardId());
        // Explicitly set parent to null since this is an original spending event, not an offset
        redemptionEntry.setParentEntry(null);

        pointLedgerEntryRepository.save(redemptionEntry);

        log.info("Successfully redeemed reward '{}' for customer ID: {}. Deducted {} points.",
                reward.getName(), customer.getCustomerId(), pointsNeeded);
    }

    @Override
    public void clawbackPoints(String purchaseReference, List<Long> returnedProductIds) {

        if (purchaseReference == null || purchaseReference.isEmpty()) {
            log.warn("Illegal purchaseReference ({}) sent! No points will be clawed back!", purchaseReference);
            return;
        }

        if (returnedProductIds == null || returnedProductIds.isEmpty()) {
            log.warn("No product IDs provided for clawback on purchase reference: {}. Skipping.", purchaseReference);
            return;
        }

        PointLedgerEntry originalEarnEntry = pointLedgerEntryRepository
                .findByPurchaseIdAndTransactionType(purchaseReference, TransactionType.EARN)
                .orElseThrow(() -> new IllegalArgumentException("No original earning entry found for purchase reference: "
                        + purchaseReference));

        BigDecimal historicalMultiplier = originalEarnEntry.getTierPointUsed();
        if (historicalMultiplier == null) {
            throw new IllegalStateException("Original ledger entry is missing its historical tierPointUse multiplier.");
        }

        // Sum up the refund value using the list passed from the order service
        BigDecimal totalRefundValue = productRepository.calculateTotalSumByIds(returnedProductIds);

        // Calculate points to claw back and commit the negative point ledger entry
        BigDecimal pointsToClawback = totalRefundValue.multiply(historicalMultiplier).setScale(2, RoundingMode.HALF_UP);

        Customer customer = customerRepository.findById(originalEarnEntry.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found for ID: " + originalEarnEntry.getCustomerId()));

        PointLedgerEntry clawbackEntry = new PointLedgerEntry();
        clawbackEntry.setCustomerId(customer.getCustomerId());
        clawbackEntry.setTransactionType(TransactionType.CLAWBACK);
        clawbackEntry.setPoints(pointsToClawback.negate()); // Negative point value
        clawbackEntry.setPurchaseId(purchaseReference);
        clawbackEntry.setParentEntry(originalEarnEntry);

        pointLedgerEntryRepository.save(clawbackEntry);

        // =========================================================================
        // HIBERNATE FLUSH OPTIMIZATION:
        // Force Hibernate to sync its cache with the database storage engine.
        // This pushes the 'RETURNED' rows saved by the calling service into the
        // database indexes immediately, guaranteeing our JPQL aggregate query
        // doesn't read stale data.
        // =========================================================================
        customerProductLedgerRepository.flush();

        // Real-time Tier Re-evaluation using your magical SQL Query
        BigDecimal netRollingSpend = customerProductLedgerRepository.calculateNetRollingSpend(
                customer.getCustomerId(),
                LocalDateTime.now().minusYears(1)
        );

        // Downgrade the cached tier status instantly if their 12-month net spend dropped below threshold
        updateCustomerTier(customer, netRollingSpend);

        log.info("Successfully clawed back {} points from customer ID: {} for purchase: {}.",
                pointsToClawback, customer.getCustomerId(), purchaseReference);
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerBalanceDTO getCustomerBalanceByEmail(String email) {

        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + email));

        return calculateActiveBalance(customer);
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerBalanceDTO getCustomerBalanceByPhone(String phoneNo) {

        Customer customer = customerRepository.findByPhoneNo(phoneNo)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with phone number: " + phoneNo));

        return calculateActiveBalance(customer);
    }

    private CustomerBalanceDTO calculateActiveBalance(Customer customer) {

        BigDecimal totalAvailablePoints = pointLedgerEntryRepository.calculateActivePointsBalance(customer.getCustomerId());
        if (totalAvailablePoints == null) {
            totalAvailablePoints = BigDecimal.ZERO;
        }

        BigDecimal netRollingSpend = customerProductLedgerRepository.calculateNetRollingSpend(
                customer.getCustomerId(),
                LocalDateTime.now().minusYears(1)
        );

        if (netRollingSpend == null) {
            netRollingSpend = BigDecimal.ZERO;
        }

        CustomerBalanceDTO balanceDTO = new CustomerBalanceDTO();
        balanceDTO.setFirstName(customer.getFirstName());
        balanceDTO.setLastName(customer.getLastName());
        balanceDTO.setEmail(customer.getEmail());
        balanceDTO.setPhoneNo(customer.getPhoneNo());
        balanceDTO.setCurrentTier(customer.getCurrentTier());
        balanceDTO.setPointsBalance(totalAvailablePoints.setScale(2, RoundingMode.HALF_UP));
        balanceDTO.setRollingSpend(netRollingSpend.setScale(2, RoundingMode.HALF_UP));

        return balanceDTO;
    }

    private void updateCustomerTier(Customer customer, BigDecimal netRollingSpend) {
        Tier appropriateTier = Tier.determineTierFromSpend(netRollingSpend);
        if (customer.getCurrentTier() != appropriateTier) {
            log.info("Customer ID: {} changing tier from {} to {} based on rolling spend of ${}",
                    customer.getCustomerId(), customer.getCurrentTier(), appropriateTier, netRollingSpend);
            customer.setCurrentTier(appropriateTier);
            customerRepository.save(customer);
        }
    }
}
