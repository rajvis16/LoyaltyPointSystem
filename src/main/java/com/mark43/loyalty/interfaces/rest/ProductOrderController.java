package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.service.ProductOrderService;
import com.mark43.loyalty.interfaces.dto.OrderRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@Log4j2
@RequiredArgsConstructor
public class ProductOrderController {

    private final ProductOrderService productOrderService;

    /**
     * Executes a product purchase scenario under the v1 API contract.
     * Appends BOUGHT records to the physical inventory ledger and
     * allocates points to the customer's account.

     * POST /api/v1/orders/buy
     */
    @PostMapping("/buy")
    public ResponseEntity<Void> buyProducts(@Valid @RequestBody OrderRequestDTO orderRequestDTO) {

        log.info("V1 REST request received to purchase products for customer: {} under reference: {}",
                orderRequestDTO.getCustomerEmail(), orderRequestDTO.getPurchaseReference());

        productOrderService.buyProducts(orderRequestDTO);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Executes a product return scenario under the v1 API contract.
     * Validates transaction limits using the in-memory balance sheet,
     * appends a RETURNED record, and triggers a point clawback penalty.

     * POST /api/v1/orders/return
     */
    @PostMapping("/return")
    public ResponseEntity<Void> returnProducts(@Valid @RequestBody OrderRequestDTO orderRequestDTO) {

        log.info("V1 REST request received to return products for customer: {} under reference: {}",
                orderRequestDTO.getCustomerEmail(), orderRequestDTO.getPurchaseReference());

        productOrderService.returnProducts(orderRequestDTO);

        return ResponseEntity.ok().build();
    }
}