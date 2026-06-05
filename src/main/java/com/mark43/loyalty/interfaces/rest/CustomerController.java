package com.mark43.loyalty.interfaces.rest;

import com.mark43.loyalty.domain.entity.Address;
import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
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

    private final LoyaltyService loyaltyService;
    private final CustomerRepository customerRepository;

    /**
     * Registers a brand new customer into the loyalty platform.
     * * POST /api/v1/customers
     * Request Body: Validated CustomerDTO payload
     */
    @PostMapping
    public ResponseEntity<Void> registerCustomer(@Valid @RequestBody CustomerDTO customerDTO) {

        log.info("REST request to register new customer with email: {}", customerDTO.getEmail());

        // Use loyalty service to handle registrations so tier defaults (SILVER) apply properly
        loyaltyService.registerCustomer(customerDTO);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Retrieves a single customer entity by their primary database ID.
     * * GET /api/v1/customers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {

        log.info("REST request to get customer by ID: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + id));

        return ResponseEntity.ok(customer);
    }

    /**
     * Fetches all registered profiles currently stored in the system.
     * * GET /api/v1/customers
     */
    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers() {
        log.info("REST request to fetch all customer profiles");

        List<Customer> customers = customerRepository.findAll();

        return ResponseEntity.ok(customers);
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

        // Map updated properties cleanly
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

        return ResponseEntity.ok(updatedCustomer);
    }

    /**
     * Permanently removes a customer profile from the persistence store layer.
     * * DELETE /api/v1/customers/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        log.info("REST request to delete customer profile ID: {}", id);

        if (!customerRepository.existsById(id)) {
            throw new IllegalArgumentException("Customer not found with ID: " + id);
        }

        customerRepository.deleteById(id);

        return ResponseEntity.ok().build();
    }
}