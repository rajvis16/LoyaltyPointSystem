package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.service.CustomerService;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@Log4j2
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final LoyaltyService loyaltyService;

    /**
     * POST /api/v1/customers
     * Registers a new customer into the system matrix ledger.
     */
    @PostMapping
    public ResponseEntity<CustomerDTO> createCustomer(@Valid @RequestBody CustomerDTO customerDTO) {

        log.info("REST request to create new customer profile with email: {}", customerDTO.getEmail());

        CustomerDTO createdCustomer = customerService.createCustomer(customerDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCustomer);
    }

    /**
     * GET /api/v1/customers/{email}/balance
     * Matches true REST sub-resource naming standards: /customers/{identity}/balance
     * Resolves a single customer's points ledger statement and active tier.
     */
    @GetMapping("/{email}/balance")
    public ResponseEntity<CustomerDTO> getCustomerBalance(@PathVariable String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email path variable cannot be null or blank.");
        }

        log.info("REST request to get customer ledger balance profile by email anchor: {}", email);

        CustomerDTO balanceDTO = loyaltyService.getCustomerByEmail(email);
        return ResponseEntity.ok(balanceDTO);
    }

    /**
     * GET /api/v1/customers
     * Fetches all registered customer profiles from the master registry ledger.
     */
    @GetMapping
    public ResponseEntity<List<CustomerDTO>> getAllCustomers() {

        log.info("REST request to fetch all customers");

        List<CustomerDTO> customers = customerService.getAllCustomers();
        return ResponseEntity.ok(customers);
    }

    /**
     * PUT /api/v1/customers/{email}
     * Updates an existing customer profile record, securely anchored by their email path identifier.
     */
    @PutMapping("/{email}")
    public ResponseEntity<CustomerDTO> updateCustomer(
            @PathVariable String email,
            @Valid @RequestBody CustomerDTO customerDTO) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email path variable cannot be null or empty.");
        }

        log.info("REST request to update profile telemetry records for customer email target: {}", email);

        if (!email.equalsIgnoreCase(customerDTO.getEmail())) {
            throw new IllegalArgumentException("Path email identifier does not match the payload body context.");
        }

        CustomerDTO updatedCustomer = customerService.updateCustomer(customerDTO);
        return ResponseEntity.ok(updatedCustomer);
    }

    /**
     * DELETE /api/v1/customers/{email}
     * Aligned URI Uniformity: Uses path tracking to remain fully symmetric with the PUT method footprint.
     */
    @DeleteMapping("/{email}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email path variable cannot be null or blank.");
        }

        log.info("REST request to withdraw customer account registration via path email lookup: {}", email);

        customerService.deleteCustomer(email);
        return ResponseEntity.noContent().build();
    }
}