package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import com.mark43.loyalty.interfaces.dto.RedeemRewardDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loyalty")
public class LoyaltyController {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyController.class);
    private final LoyaltyService loyaltyService;

    public LoyaltyController(LoyaltyService loyaltyService) {
        this.loyaltyService = loyaltyService;
    }

    /**
     * REST Endpoint to handle customer point redemptions for catalog rewards.
     * Receives a consolidated payload matching the strict service layer interface signature.
     */
    @PostMapping("/redeem")
    public ResponseEntity<Void> redeemReward(@RequestBody RedeemRewardDTO requestPayload) {

        log.info("REST request received to redeem reward for customer: {}", requestPayload.getCustomerEmail());

        loyaltyService.redeemReward(requestPayload);

        return ResponseEntity.ok().build();
    }

    /**
     * REST Endpoint to fetch a customer's current available point pool balance and tier standing by Email.
     * Leverages the unified CustomerBalanceDTO summary response object.
     */
    @GetMapping("/balance/email")
    public ResponseEntity<CustomerDTO> getAvailableBalanceByEmail(@RequestParam String email) {

        log.info("REST request received to check available balance summary for email: {}", email);

        CustomerDTO balanceSummary = loyaltyService.getCustomerBalanceByEmail(email);

        return ResponseEntity.ok(balanceSummary);
    }

    /**
     * REST Endpoint to fetch a customer's current available point pool balance and tier standing by Phone Number.
     * Leverages the unified CustomerBalanceDTO summary response object.
     */
    @GetMapping("/balance/phone")
    public ResponseEntity<CustomerDTO> getAvailableBalanceByPhone(@RequestParam String phoneNo) {

        log.info("REST request received to check available balance summary for phone: {}", phoneNo);

        CustomerDTO balanceSummary = loyaltyService.getCustomerBalanceByPhone(phoneNo);

        return ResponseEntity.ok(balanceSummary);
    }
}