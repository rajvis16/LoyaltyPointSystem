package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.*;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.infrastructure.repository.*;
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
import java.util.Map;
import java.util.stream.Collectors;

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
    private final LoyaltyCacheManager cacheManager;

    public LoyaltyServiceImpl(CustomerRepository customerRepository,
                              CustomerProductLedgerRepository customerProductLedgerRepository,
                              PointLedgerEntryRepository pointLedgerEntryRepository,
                              ProductRepository productRepository,
                              RewardRepository rewardRepository,
                              LoyaltyCacheManager cacheManager) {
        this.customerRepository = customerRepository;
        this.customerProductLedgerRepository = customerProductLedgerRepository;
        this.pointLedgerEntryRepository = pointLedgerEntryRepository;
        this.productRepository = productRepository;
        this.rewardRepository = rewardRepository;
        this.cacheManager = cacheManager;
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

        if (pointLedgerEntryRepository.existsByPurchaseId(earnPointsDTO.getPurchaseReference())) {
            log.warn("Duplicate processing attempt detected for purchase reference: {}. " +
                    "Skipping allocation.", earnPointsDTO.getPurchaseReference());
            return;
        }

        BigDecimal multiplier = switch (customer.getCurrentTier()) {
            case SILVER -> new BigDecimal("1.0");
            case GOLD -> new BigDecimal("1.5");
            case PLATINUM -> new BigDecimal("2.0");
        };

        BigDecimal pointsToAward = earnPointsDTO.getTotalSpend().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        PointLedgerEntry earnEntry = new PointLedgerEntry();
        earnEntry.setCustomerId(customer.getCustomerId());
        earnEntry.setTransactionType(EARN);
        earnEntry.setPoints(pointsToAward);
        // The remaining point is same as earned point for this new bucket
        // Any points spent, in future, from this bucket will be deducted from this field
        earnEntry.setRemainingPoints(pointsToAward);
        earnEntry.setCreatedAtDate(LocalDateTime.now());
        earnEntry.setTierPointUsed(multiplier); // Take snapshot of the current tier, useful when clawing back
        earnEntry.setPurchaseId(earnPointsDTO.getPurchaseReference());
        earnEntry.setExpiryDate(LocalDateTime.now().plusYears(1));
        earnEntry.setParentEntry(null);

        pointLedgerEntryRepository.save(earnEntry);

        customerProductLedgerRepository.flush();

        // Get net rolling spend for the past 1 year
        // We need this as we need to assign the customer
        // to the new tier, if needed.
        BigDecimal netRollingSpend = customerProductLedgerRepository.calculateNetRollingSpend(
                customer.getCustomerId(), LocalDateTime.now().minusYears(1)
        );

        // update customer tier, if applicable
        updateCustomerTier(customer, netRollingSpend);

        cacheManager.invalidate(customer.getEmail());

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

        BigDecimal totalRemainingPoints = pointLedgerEntryRepository.calculateActivePointsBalance(customer.getCustomerId(),
                LocalDateTime.now());

        if (totalRemainingPoints == null || totalRemainingPoints.compareTo(pointsNeeded) < 0) {
            throw new IllegalArgumentException("Insufficient points balance. Required: " + pointsNeeded +
                    ", Active Available: " + (totalRemainingPoints == null ? BigDecimal.ZERO : totalRemainingPoints));
        }

        BigDecimal pointsLeftToDeduct = pointsNeeded;

        // Oldest EARN bucket at the top of the list
        List<PointLedgerEntry> activeEarnBuckets = pointLedgerEntryRepository
                .findAvailableEarnEntries(customer.getCustomerId(), LocalDateTime.now());

        for (PointLedgerEntry earnBucket : activeEarnBuckets) {

            if (pointsLeftToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal availableInBucket = earnBucket.getRemainingPoints();

            if (availableInBucket.compareTo(pointsLeftToDeduct) >= 0) {
                // The earn bucket remaining point is still not consumed.
                // Subtract how much was consumed and then set the
                // remaining point for the future use.
                earnBucket.setRemainingPoints(availableInBucket.subtract(pointsLeftToDeduct));
                pointsLeftToDeduct = BigDecimal.ZERO;
            } else {
                pointsLeftToDeduct = pointsLeftToDeduct.subtract(availableInBucket);
                // Earn bucket remaining point is consumed. Set it to zero.
                earnBucket.setRemainingPoints(BigDecimal.ZERO);
            }

            // Keep saving the record with updated remaining points immediately
            // In other complex application, saving immediately informs the other
            // thread about the latest points left and hence, no working
            // on the stale data
            pointLedgerEntryRepository.save(earnBucket);
        }

        // At this moment, pointsLeftToDeduct should be exactly zero because
        // the loop above is expected to satisfy the total debt.
        // If pointsLeftToDeduct remains greater than zero, it means the bucket
        // loop ran out of active points and we must throw an exception to abort.
        if (pointsLeftToDeduct.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException("Ledger anomaly: Aggregate check passed but bucket loop failed to satisfy deduction.");
        }

        PointLedgerEntry redemptionEntry = new PointLedgerEntry();
        redemptionEntry.setCustomerId(customer.getCustomerId());
        redemptionEntry.setTransactionType(REDEEM);
        redemptionEntry.setPoints(pointsNeeded.negate());
        redemptionEntry.setRemainingPoints(BigDecimal.ZERO);
        redemptionEntry.setCreatedAtDate(LocalDateTime.now());
        redemptionEntry.setRewardId(reward.getRewardId());
        redemptionEntry.setParentEntry(null);

        pointLedgerEntryRepository.save(redemptionEntry);

        cacheManager.invalidate(customer.getEmail());

        log.info("Successfully redeemed reward '{}' for customer ID: {}. Deducted {} points using FIFO expiry logic.",
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

        // Check if the customer has any entry for the purchase reference (bill no)
        PointLedgerEntry originalEarnEntry = pointLedgerEntryRepository
                .findByPurchaseIdAndTransactionType(purchaseReference, TransactionType.EARN)
                .orElseThrow(() -> new IllegalArgumentException("No original earning entry found for purchase reference: "
                        + purchaseReference));

        Customer customer = customerRepository.findById(originalEarnEntry.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found for ID: " + originalEarnEntry.getCustomerId()));

        BigDecimal historicalMultiplier = originalEarnEntry.getTierPointUsed();
        if (historicalMultiplier == null) {
            throw new IllegalStateException("Original ledger entry is missing its historical tierPointUse multiplier.");
        }

        // Fetch all required product data in EXACTLY ONE database query
        List<Product> products = productRepository.findByProductIdIn(returnedProductIds);

        // Transform the result list into map
        Map<Long, BigDecimal> priceMap = products.stream()
                .collect(Collectors.toMap(Product::getProductId, Product::getPrice));

        // Map the original raw list (including duplicates) against the memory map
        BigDecimal totalRefundValue = calculateTotalRefund(returnedProductIds, priceMap);

        BigDecimal pointsToClawback = totalRefundValue.multiply(historicalMultiplier).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingInParent = originalEarnEntry.getRemainingPoints();

        if (remainingInParent.compareTo(pointsToClawback) >= 0) {
            // If remaining points are greater than the points to be clawed back
            // then subtract it so that we reflect the latest remaining point for
            // future use
            originalEarnEntry.setRemainingPoints(remainingInParent.subtract(pointsToClawback));
        } else {
            // Cap at zero if points have already been mostly redeemed
            originalEarnEntry.setRemainingPoints(BigDecimal.ZERO);
        }
        pointLedgerEntryRepository.save(originalEarnEntry);

        // Add new Clawback entry for future auditing and net point calculation
        PointLedgerEntry clawbackEntry = new PointLedgerEntry();
        clawbackEntry.setCustomerId(customer.getCustomerId());
        clawbackEntry.setTransactionType(TransactionType.CLAWBACK);
        clawbackEntry.setPoints(pointsToClawback.negate());
        clawbackEntry.setRemainingPoints(BigDecimal.ZERO);
        clawbackEntry.setPurchaseId(purchaseReference);
        clawbackEntry.setParentEntry(originalEarnEntry);

        pointLedgerEntryRepository.save(clawbackEntry);

        customerProductLedgerRepository.flush();

        // Customer has return the products
        // Need to check if they need to be demoted
        // to the less prestigious tier
        BigDecimal netRollingSpend = customerProductLedgerRepository.calculateNetRollingSpend(
                customer.getCustomerId(),
                LocalDateTime.now().minusYears(1)
        );

        updateCustomerTier(customer, netRollingSpend);

        cacheManager.invalidate(customer.getEmail());

        log.info("Successfully clawed back {} points from customer ID: {} for purchase: {}.",
                pointsToClawback, customer.getCustomerId(), purchaseReference);
    }

    public CustomerDTO getCustomerByEmail(String email) {

        CustomerDTO cached = cacheManager.get(email);

        if (cached != null) {
            log.debug("Cache HIT for customer email: {}", email);
            return cached;
        }

        log.debug("Cache MISS for customer email: {}. Computing fresh state.", email);
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + email));

        CustomerDTO freshBalance = calculateActiveBalance(customer);
        cacheManager.put(email, freshBalance);
        return freshBalance;
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerDTO getCustomerByPhone(String phoneNo) {

        Customer customer = customerRepository.findByPhoneNo(phoneNo)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with phone number: " + phoneNo));

        return getCustomerByEmail(customer.getEmail());
    }

    private CustomerDTO calculateActiveBalance(Customer customer) {

        BigDecimal totalAvailablePoints = pointLedgerEntryRepository.calculateActivePointsBalance(
                customer.getCustomerId(),
                LocalDateTime.now());

        // 🛡️ THE RETAIL ENGINE DEFENSE: Intercept negative sums and clamp tightly to a 0.00 floor
        if (totalAvailablePoints == null || totalAvailablePoints.compareTo(BigDecimal.ZERO) < 0) {
            totalAvailablePoints = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal netRollingSpend = customerProductLedgerRepository.calculateNetRollingSpend(
                customer.getCustomerId(),
                LocalDateTime.now().minusYears(1)
        );

        if (netRollingSpend == null) {
            netRollingSpend = BigDecimal.ZERO;
        }

        CustomerDTO balanceDTO = new CustomerDTO();

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

    private BigDecimal calculateTotalRefund(List<Long> returnedProductIds, Map<Long, BigDecimal> priceMap) {

        BigDecimal total = BigDecimal.ZERO;

        for (Long id : returnedProductIds) {
            BigDecimal price = priceMap.get(id);

            // Fail-fast fraud protection safety check
            if (price == null) {
                throw new IllegalArgumentException("Product catalog lookup missing for ID: " + id);
            }

            // Sum up the price to our running total sum
            total = total.add(price);
        }

        return total;
    }
}