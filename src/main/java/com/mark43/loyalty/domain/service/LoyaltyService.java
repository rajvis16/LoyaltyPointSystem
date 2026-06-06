package com.mark43.loyalty.domain.service;

import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import com.mark43.loyalty.interfaces.dto.EarnPointsDTO;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;

import java.util.List;

public interface LoyaltyService {

    /**
     * Registers a new customer within the loyalty platform.
     */
    void registerCustomer(CustomerDTO request);

    /**
     * Process an incoming transaction invoice to calculate tier multipliers,
     * calculate points earned, log the purchase spend, and update the ledger.
     */
    void earnPoints(EarnPointsDTO request);

    /**
     * Redeems points for an asset in the catalog using a strict FIFO matching
     * algorithm on active, unexpired point ledger accounts.
     */
    void redeemReward(RedeemRewardDTO request);

    /**
     * Handles processing returns or cancellations, deducting previously awarded points
     * and potentially triggering negative balance states.
     */
    void clawbackPoints(String purchaseReference, List<Long> productIds);

    /**
     * Aggregates dynamic point balances and evaluates rolling 12-month tier standing.
     */
    CustomerDTO getCustomerByEmail(String email);

    /**
     * Aggregates dynamic point balances and evaluates rolling 12-month tier standing.
     */
    CustomerDTO getCustomerByPhone(String phoneNo);
}