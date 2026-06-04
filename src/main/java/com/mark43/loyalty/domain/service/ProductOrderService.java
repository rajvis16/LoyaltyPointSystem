package com.mark43.loyalty.domain.service;

import com.mark43.loyalty.interfaces.dto.OrderRequestDTO;

import java.util.Map;

public interface ProductOrderService {

    /**
     * Processes a customer purchase. Logs the items bought to the product ledger
     * and triggers the corresponding downstream point allocations.
     *
     * @param orderRequestDTO DTO that contains important info to place an order
     */
    void buyProducts(OrderRequestDTO orderRequestDTO);

    /**
     * Processes a partial or full return of specific items. Validates that the customer
     * possesses historical ownership of the products under this purchase boundary,
     * logs the returned items, and flags fractional point clawbacks.
     *
     * @param orderRequestDTO DTO that contains important info to place an order
     */
    void returnProducts(OrderRequestDTO orderRequestDTO);
}