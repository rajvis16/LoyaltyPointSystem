package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.infrastructure.repository.PointLedgerEntryRepository;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.infrastructure.repository.RewardCatalogRepository;
import com.mark43.loyalty.interfaces.dto.CustomerBalanceDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import com.mark43.loyalty.interfaces.dto.EarnPointsDTO;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;
import jakarta.transaction.Transactional;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import static com.mark43.loyalty.domain.entity.ProductAction.BOUGHT;
import static com.mark43.loyalty.domain.entity.Tier.SILVER;
import static com.mark43.loyalty.domain.entity.TransactionType.*;

@Transactional
@Log4j2
@Service
public class LoyaltyServiceImpl implements LoyaltyService {

    private final CustomerRepository customerRepository;
    private final PointLedgerEntryRepository pointLedgerEntryRepository;
    private final ProductRepository productRepository;
    private final RewardCatalogRepository rewardCatalogRepository;

    public LoyaltyServiceImpl(CustomerRepository customerRepository,
                              PointLedgerEntryRepository pointLedgerEntryRepository,
                              ProductRepository productRepository,
                              RewardCatalogRepository rewardCatalogRepository) {
        this.customerRepository = customerRepository;
        this.pointLedgerEntryRepository = pointLedgerEntryRepository;
        this.productRepository = productRepository;
        this.rewardCatalogRepository = rewardCatalogRepository;
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

        // Idempotency Check
        if (pointLedgerEntryRepository.existsByPurchaseId(earnPointsDTO.getPurchaseReference())) {
            log.warn("Duplicate processing attempt detected for purchase reference: {}. " +
                    "Skipping allocation.", earnPointsDTO.getPurchaseReference());
            return;
        }

        // Calculate transaction spend
        // NOTE: Any product missing will trigger a fail fast exception
        // and the user will not be allowed to earn points on
        // partial update
        BigDecimal totalSpend = BigDecimal.ZERO;
        if (earnPointsDTO.getProductNames() != null) {
            for (String productName : earnPointsDTO.getProductNames()) {
                Product product = productRepository.findByName(productName)
                        .orElseThrow(() -> new IllegalArgumentException("Product not found in catalog: " + productName));
                totalSpend = totalSpend.add(product.getPrice());
            }
        }

        // Resolve multi-tier multiplier rules
        BigDecimal multiplier = switch (customer.getCurrentTier()) {
            case SILVER -> new BigDecimal("1.0");
            case GOLD -> new BigDecimal("1.5");
            case PLATINUM -> new BigDecimal("2.0");
        };

        // Calculate rounded points to award
        BigDecimal pointsToAward = totalSpend.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        PointLedgerEntry earnEntry = new PointLedgerEntry();
        earnEntry.setCustomerId(customer.getCustomerId());
        earnEntry.setTransactionType(EARN);
        earnEntry.setPoints(pointsToAward);
        earnEntry.setPurchaseId(earnPointsDTO.getPurchaseReference());

        // Set expiration boundary (e.g., points valid for 1 year from today)
        earnEntry.setExpiryDate(LocalDateTime.now().plusYears(1));

        // Explicitly set parent to null since this is the root transaction event
        earnEntry.setParentEntry(null);

        // 6. Commit immutable row to database
        pointLedgerEntryRepository.save(earnEntry);

        log.info("Successfully allocated {} points to customer ID: {}.",
                pointsToAward, customer.getCustomerId());

    }

    @Override
    public void redeemReward(RedeemRewardDTO redeemRewardDTO) {

        Customer customer = customerRepository.findByEmail(redeemRewardDTO.getCustomerEmail())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: "
                        + redeemRewardDTO.getCustomerEmail()));

        RewardCatalog reward = rewardCatalogRepository.findByName(redeemRewardDTO.getRewardName())
                .orElseThrow(() -> new IllegalArgumentException("Reward not found in catalog: "
                        + redeemRewardDTO.getRewardName()));

        BigDecimal pointsNeeded = reward.getPointsRequired();

        BigDecimal totalAvailablePoints = computeActiveBalanceFromLedger(customer.getCustomerId());

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

        // Fetch the original transaction using the purchase reference string
        PointLedgerEntry originalEarnEntry = pointLedgerEntryRepository
                .findByPurchaseIdAndTransactionType(purchaseReference, EARN)
                .orElseThrow(() -> new IllegalArgumentException("No original earning entry found for purchase reference: "
                        + purchaseReference));

        // Read the snapshot tier multiplier directly from the row
        BigDecimal historicalMultiplier = originalEarnEntry.getTierPointUsed();

        if (historicalMultiplier == null) {
            throw new IllegalStateException("Original ledger entry is missing its historical tierPointUse multiplier.");
        }

        // Sum up the prices of the products currently being returned out of the catalog
        BigDecimal totalRefundValue = BigDecimal.ZERO;
        for (Long returnedProductId : returnedProductIds) {
            Product returnedProduct = productRepository.findById(returnedProductId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found in catalog with ID: " + returnedProductId));
            totalRefundValue = totalRefundValue.add(returnedProduct.getPrice());
        }

        // Calculate exact points to claw back using the immutable historical multiplier
        BigDecimal pointsToClawback = totalRefundValue.multiply(historicalMultiplier).setScale(2, RoundingMode.HALF_UP);

        PointLedgerEntry clawbackEntry = new PointLedgerEntry();
        clawbackEntry.setCustomerId(originalEarnEntry.getCustomerId());
        clawbackEntry.setTransactionType(CLAWBACK);
        clawbackEntry.setPoints(pointsToClawback.negate()); // Stored as a negative value
        clawbackEntry.setPurchaseId(purchaseReference);
        clawbackEntry.setParentEntry(originalEarnEntry); // Explicit reference chain mapping back to origin

        pointLedgerEntryRepository.save(clawbackEntry);

        log.info("Successfully clawed back {} points from customer ID: {} for products {} in purchase: {}. (Using snapshot rate: {}x)",
                pointsToClawback, originalEarnEntry.getCustomerId(), returnedProductIds, purchaseReference, historicalMultiplier);
    }

    @Override
    public CustomerBalanceDTO getCustomerBalanceByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + email));

        return calculateActiveBalance(customer);
    }

    @Override
    public CustomerBalanceDTO getCustomerBalanceByPhone(String phoneNo) {
        Customer customer = customerRepository.findByPhoneNo(phoneNo)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with phone number: " + phoneNo));

        return calculateActiveBalance(customer);
    }

    private CustomerBalanceDTO calculateActiveBalance(Customer customer) {

        BigDecimal totalAvailablePoints = computeActiveBalanceFromLedger(customer.getCustomerId());

        CustomerBalanceDTO balanceDTO = new CustomerBalanceDTO();
        balanceDTO.setFirstName(customer.getFirstName());
        balanceDTO.setLastName(customer.getLastName());
        balanceDTO.setEmail(customer.getEmail());
        balanceDTO.setPhoneNo(customer.getPhoneNo());
        balanceDTO.setCurrentTier(customer.getCurrentTier());
        balanceDTO.setPointsBalance(totalAvailablePoints);
        balanceDTO.setRollingSpend(BigDecimal.ZERO);
        return balanceDTO;
    }

    private BigDecimal computeActiveBalanceFromLedger(Long customerId) {

        List<PointLedgerEntry> ledger = pointLedgerEntryRepository
                .findByCustomerIdOrderByPointLedgerEntryIdAsc(customerId);

        BigDecimal totalAvailablePoints = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();
        BigDecimal historicalDeductions = BigDecimal.ZERO;

        // We iterate first to get total of the debt that we owe
        // We ignore the award point as we are only interested in debt
        for (PointLedgerEntry entry : ledger) {
            if (REDEEM == entry.getTransactionType() || CLAWBACK == entry.getTransactionType()) {
                historicalDeductions = historicalDeductions.add(entry.getPoints().abs());
            }
        }

        // Next, we iterate again and in this loop
        // we try to find if we have sufficient net points to redeem
        for (PointLedgerEntry pointLedgerEntry : ledger) {
            if (EARN == pointLedgerEntry.getTransactionType()) {
                // Expired points should be skipped
                if (pointLedgerEntry.getExpiryDate() != null && pointLedgerEntry.getExpiryDate().isBefore(now)) {
                    continue;
                }

                //  Points available within this active earning bucket
                BigDecimal bucketPoints = pointLedgerEntry.getPoints();

                // If there are negative points (debt) from the past..,
                if (historicalDeductions.compareTo(BigDecimal.ZERO) > 0) {
                    // ... and is greater or equal than the active earning bucket...
                    if (historicalDeductions.compareTo(bucketPoints) >= 0) {
                        // then reduce the historicalDeduction as we paid some of the debt
                        historicalDeductions = historicalDeductions.subtract(bucketPoints);
                        continue;
                    } else {
                        // if the debt was less than the active earning bucket then
                        // deduct the debt from the earning bucket which will
                        // reduce our earning point
                        bucketPoints = bucketPoints.subtract(historicalDeductions);
                        historicalDeductions = BigDecimal.ZERO;
                    }
                }

                // Whatever we earned in this loop add it to our total points
                // and then go to the next earning bucket
                totalAvailablePoints = totalAvailablePoints.add(bucketPoints);
            }
        }

        return totalAvailablePoints;
    }
}
