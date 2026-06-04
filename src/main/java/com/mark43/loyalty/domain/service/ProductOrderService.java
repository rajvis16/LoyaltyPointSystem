package com.mark43.loyalty.domain.service;

import java.util.Map;

public interface ProductOrderService {

    /**
     * Processes a customer purchase. Logs the items bought to the product ledger
     * and triggers the corresponding downstream point allocations.
     *
     * @param customerEmail The unique identifier of the buying customer.
     * @param purchaseReference The unique order ID string tracking this purchase transaction boundary.
     * @param productQuantities A map matching product names to the explicit quantities purchased.
     */
    void buyProducts(String customerEmail, String purchaseReference, Map<String, Integer> productQuantities);

    /**
     * Processes a partial or full return of specific items. Validates that the customer
     * possesses historical ownership of the products under this purchase boundary,
     * logs the returned items, and flags fractional point clawbacks.
     *
     * @param customerEmail The unique identifier of the returning customer.
     * @param purchaseReference The original order ID string matching the purchase anchor.
     * @param productQuantities A map matching product names to the specific quantities being returned.
     */
    void returnProducts(String customerEmail, String purchaseReference, Map<String, Integer> productQuantities);
}