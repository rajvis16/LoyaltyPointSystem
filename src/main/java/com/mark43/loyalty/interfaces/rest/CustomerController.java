package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.entity.Address;
import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.domain.service.impl.LoyaltyCacheManager; // 💡 Injecting our new component
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.interfaces.dto.CustomerBalanceDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
@Log4j2
@RequiredArgsConstructor
public class CustomerController {

    private final LoyaltyService loyaltyService;
    private final CustomerRepository customerRepository;
    private final LoyaltyCacheManager cacheManager; // 💡 Wired cache gateway

    /**
     * Registers a brand new customer into the loyalty platform.
     * POST /api/v1/customers
     */
    @PostMapping
    public ResponseEntity<Void> registerCustomer(@Valid @RequestBody CustomerDTO customerDTO) {

        log.info("REST request to register new customer with email: {}", customerDTO.getEmail());

        loyaltyService.registerCustomer(customerDTO);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Retrieves a single customer profile along with their real-time point balance and tier.
     * GET /api/v1/customers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomerBalanceDTO> getCustomerById(@PathVariable Long id) {

        log.info("REST request to get customer balance profile by ID: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + id));

        CustomerBalanceDTO balanceDTO = loyaltyService.getCustomerBalanceByEmail(customer.getEmail());
        return ResponseEntity.ok(balanceDTO);
    }

    /**
     * Fetches all registered profiles aggregated with their respective live balances and tiers.
     * GET /api/v1/customers
     */
    @GetMapping
    public ResponseEntity<List<CustomerBalanceDTO>> getAllCustomers() {

        log.info("REST request to fetch all customer profiles with live ledger balances");

        List<Customer> customers = customerRepository.findAll();
        List<CustomerBalanceDTO> responseList = new ArrayList<>();

        for (Customer customer : customers) {
            responseList.add(loyaltyService.getCustomerBalanceByEmail(customer.getEmail()));
        }

        return ResponseEntity.ok(responseList);
    }

    /**
     * Updates the core profile contact metadata of an existing customer entity.
     * * PUT /api/v1/customers/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerDTO customerDTO) {

        log.info("REST request to update customer metadata for ID: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + id));

        // 💡 CACHE PROTECTION: Evict the existing cache key BEFORE updating the database fields.
        // We evict the old email key just in case the email value itself is being modified.
        cacheManager.invalidate(customer.getEmail());

        customer.setFirstName(customerDTO.getFirstName());
        customer.setLastName(customerDTO.getLastName());
        customer.setEmail(customerDTO.getEmail());
        customer.setPhoneNo(customerDTO.getPhoneNo());

        if (customerDTO.getAddress() != null) {
            Address address = new Address();
            address.setStreetNo(customerDTO.getAddress().getStreetNo());
            address.setStreet(customerDTO.getAddress().getStreet());
            address.setCity(customerDTO.getAddress().getCity());
            address.setState(customerDTO.getAddress().getState());
            address.setZipCode(customerDTO.getAddress().getZipCode());
            address.setCountry(customerDTO.getAddress().getCountry());
            customer.setAddress(address);
        }

        Customer updatedCustomer = customerRepository.save(customer);

        // 💡 CACHE PROTECTION: Evict the new email key as well to ensure the next GET forces a fresh computation.
        cacheManager.invalidate(updatedCustomer.getEmail());

        return ResponseEntity.ok(updatedCustomer);
    }

    /**
     * Permanently removes a customer profile from the persistence store layer.
     * * DELETE /api/v1/customers/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        log.info("REST request to delete customer profile ID: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + id));

        // 💡 CACHE PROTECTION: Purge the cache entry before destroying the database record.
        cacheManager.invalidate(customer.getEmail());

        customerRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}