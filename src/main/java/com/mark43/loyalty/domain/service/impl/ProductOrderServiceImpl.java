package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.entity.CustomerProductLedgerEntry;
import com.mark43.loyalty.domain.entity.Product;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.domain.service.ProductOrderService;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.infrastructure.repository.CustomerProductLedgerRepository;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.interfaces.dto.EarnPointsDTO;
import com.mark43.loyalty.interfaces.dto.OrderRequestDTO;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mark43.loyalty.domain.entity.ProductAction.BOUGHT;
import static com.mark43.loyalty.domain.entity.ProductAction.RETURNED;

@Transactional
@Log4j2
@RequiredArgsConstructor
@Service
public class ProductOrderServiceImpl implements ProductOrderService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CustomerProductLedgerRepository customerProductLedgerRepository;
    private final LoyaltyService loyaltyService;

    public void buyProducts(OrderRequestDTO orderRequestDTO) {

        String customerEmail = orderRequestDTO.getCustomerEmail();
        String purchaseReference = orderRequestDTO.getPurchaseReference();
        Map<String, Integer> productQuantities = orderRequestDTO.getProductQuantities();

        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("Customer email cannot be Null or empty ");
        }

        Customer customer = customerRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + customerEmail));

        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new IllegalArgumentException("Cannot process a purchase with empty product selection.");
        }

        BigDecimal totalOrderSpend = BigDecimal.ZERO;

        List<String> productNames = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : productQuantities.entrySet()) {

            String productName = entry.getKey();
            Integer quantity = entry.getValue();

            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero for product: " + productName);
            }

            Product product = productRepository.findByName(productName)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found in catalog: " + productName));

            BigDecimal lineItemSpend = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            totalOrderSpend = totalOrderSpend.add(lineItemSpend);

            for (int i = 0; i < quantity; i++) {
                productNames.add(productName);
            }

            CustomerProductLedgerEntry ledgerEntry = new CustomerProductLedgerEntry();
            ledgerEntry.setCustomerId(customer.getCustomerId());
            ledgerEntry.setProductId(product.getProductId());
            ledgerEntry.setPurchaseId(purchaseReference);
            ledgerEntry.setAction(BOUGHT);
            ledgerEntry.setQuantity(quantity);
            ledgerEntry.setTotalSpendingPerProduct(lineItemSpend);
            ledgerEntry.setTransactionDate(LocalDateTime.now());

            customerProductLedgerRepository.save(ledgerEntry);
        }

        EarnPointsDTO earnPointsDTO = new EarnPointsDTO();
        earnPointsDTO.setCustomerEmail(customerEmail);
        earnPointsDTO.setPurchaseReference(purchaseReference);
        earnPointsDTO.setTotalSpend(totalOrderSpend);

        earnPointsDTO.setProductNames(productNames);

        loyaltyService.earnPoints(earnPointsDTO);

        log.info("Purchase successfully committed to inventory ledger for reference: {}", purchaseReference);
    }

    @Override
    public void returnProducts(OrderRequestDTO orderRequestDTO) {

        String customerEmail = orderRequestDTO.getCustomerEmail();
        String purchaseReference = orderRequestDTO.getPurchaseReference();
        Map<String, Integer> returnedProductQuantities = orderRequestDTO.getProductQuantities();

        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException("Customer email cannot be Null or empty ");
        }

        Customer customer = customerRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + customerEmail));

        if (returnedProductQuantities == null || returnedProductQuantities.isEmpty()) {
            throw new IllegalArgumentException("Return payload cannot be empty.");
        }

        // Fetch the entire append-only ledger history for this purchaseReference (bill)
        List<CustomerProductLedgerEntry> history = customerProductLedgerRepository
                .findByCustomerIdAndPurchaseId(customer.getCustomerId(), purchaseReference);

        if (history.isEmpty()) {
            throw new IllegalArgumentException("No original purchase ledger record found for reference: " + purchaseReference);
        }

        // STEP 1: Build the Inventory Balance Sheet (Flatten the history)
        // Key: Product ID, Value: Net units currently owned (Bought minus previously Returned)
        Map<Long, Integer> netOwnedInventory = new java.util.HashMap<>();

        for (CustomerProductLedgerEntry record : history) {
            if (BOUGHT == record.getAction()) {
                netOwnedInventory.put(record.getProductId(),
                        netOwnedInventory.getOrDefault(record.getProductId(), 0) + record.getQuantity());
            } else if (RETURNED == record.getAction()) {
                netOwnedInventory.put(record.getProductId(),
                        netOwnedInventory.getOrDefault(record.getProductId(), 0) - record.getQuantity());
            }
        }

        // List to collect flat product IDs for the downstream loyalty engine hook
        List<Long> returnedProductIds = new ArrayList<>();

        // STEP 2: Validate the Return Request against our clean Balance Sheet
        for (Map.Entry<String, Integer> entry : returnedProductQuantities.entrySet()) {
            String productName = entry.getKey();
            Integer returnQuantity = entry.getValue();

            if (returnQuantity <= 0) {
                throw new IllegalArgumentException("Return quantity must be greater than zero for product: " + productName);
            }

            Product product = productRepository.findByName(productName)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found in catalog: " + productName));

            // Pull exactly how many units they currently own from this bill right out of the map
            int currentNetOwned = netOwnedInventory.getOrDefault(product.getProductId(), 0);

            // Fail-fast fraud protection check
            if (currentNetOwned <= 0) {
                throw new IllegalArgumentException("Customer does not own any valid units of product '" + productName + "' under this order reference.");
            }
            if (returnQuantity > currentNetOwned) {
                throw new IllegalArgumentException("Invalid return request. Customer attempting to return " + returnQuantity +
                        " unit(s) of '" + productName + "' but only net owns " + currentNetOwned + " unit(s) from this order.");
            }

            // Append the clean, immutable RETURNED row to our ledger
            CustomerProductLedgerEntry returnEntry = new CustomerProductLedgerEntry();
            returnEntry.setCustomerId(customer.getCustomerId());
            returnEntry.setProductId(product.getProductId());
            returnEntry.setPurchaseId(purchaseReference);
            returnEntry.setAction(RETURNED);
            returnEntry.setQuantity(returnQuantity);
            returnEntry.setTransactionDate(LocalDateTime.now());
            returnEntry.setTotalSpendingPerProduct(product.getPrice().multiply(BigDecimal.valueOf(returnQuantity)));

            customerProductLedgerRepository.save(returnEntry);

            // Populate the flat list for the loyalty engine (1 entry per unit item)
            for (int i = 0; i < returnQuantity; i++) {
                returnedProductIds.add(product.getProductId());
            }

            log.info("Logged return of {}x '{}' under transaction {}", returnQuantity, productName, purchaseReference);
        }

        // THE FINAL BRIDGE: Call our point clawback
        loyaltyService.clawbackPoints(purchaseReference, returnedProductIds);

        log.info("All returned line-items and point clawbacks committed successfully.");
    }
}