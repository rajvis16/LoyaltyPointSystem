package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Address;
import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.service.CustomerService;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.interfaces.dto.AddressDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Log4j2
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final LoyaltyService loyaltyService;
    private final LoyaltyCacheManager cacheManager;

    @Override
    public CustomerDTO createCustomer(CustomerDTO customerDTO) {

        log.debug("Processing service-level registration for email: {}", customerDTO.getEmail());

        // 1. Delegate core unique checks to your loyalty platform rules
        loyaltyService.registerCustomer(customerDTO);

        // 2. Fetch the newly saved database entity to extract its auto-generated ID
        Customer savedCustomer = customerRepository.findByEmail(customerDTO.getEmail())
                .orElseThrow(() -> new IllegalStateException("Registration succeeded but record retrieval failed."));

        return mapToDTO(savedCustomer);
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerDTO getCustomerByEmail(String email) {

        log.debug("Processing DTO lookup for customer email: {}", email);
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + email));

        return mapToDTO(customer);
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerDTO getCustomerByPhoneNo(String phoneNo) {

        log.debug("Processing DTO lookup for customer phone: {}", phoneNo);

        Customer customer = customerRepository.findByPhoneNo(phoneNo)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with phone number: " + phoneNo));

        return mapToDTO(customer);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CustomerDTO> getAllCustomers() {

        log.debug("Processing DTO lookup for all system customers");

        List<Customer> customers = customerRepository.findAll();
        List<CustomerDTO> dtoList = new ArrayList<>();

        for (Customer customer : customers) {
            dtoList.add(mapToDTO(customer));
        }
        return dtoList;
    }

    @Override
    public CustomerDTO updateCustomer(CustomerDTO customerDTO) {

        log.debug("Processing service update for customer email: {}", customerDTO.getEmail());

        Customer customer = customerRepository.findByEmail(customerDTO.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + customerDTO.getEmail()));

        // 💡 CACHE PROTECTION: Evict stale records under the old email map pointer
        cacheManager.invalidate(customer.getEmail());

        customer.setFirstName(customerDTO.getFirstName());
        customer.setLastName(customerDTO.getLastName());
        customer.setEmail(customerDTO.getEmail());
        customer.setPhoneNo(customerDTO.getPhoneNo());

        if (customerDTO.getAddress() != null) {

            Address address = customer.getAddress() != null ? customer.getAddress() : new Address();
            address.setStreetNo(customerDTO.getAddress().getStreetNo());
            address.setStreet(customerDTO.getAddress().getStreet());
            address.setCity(customerDTO.getAddress().getCity());
            address.setState(customerDTO.getAddress().getState());
            address.setZipCode(customerDTO.getAddress().getZipCode());
            address.setCountry(customerDTO.getAddress().getCountry());
            customer.setAddress(address);
        }

        Customer updatedCustomer = customerRepository.save(customer);

        // 💡 CACHE PROTECTION: Evict records under the new email to force fresh dynamic calculations
        cacheManager.invalidate(updatedCustomer.getEmail());

        return mapToDTO(updatedCustomer);
    }

    @Override
    public void deleteCustomer(String email) {

        log.debug("Processing service deletion for customer email: {}", email);

        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + email));

        cacheManager.invalidate(customer.getEmail());
        customerRepository.delete(customer);
    }

    /**
     * Helper Mapper: Intercepts entity fields and enriches the DTO with the
     * dynamic point balance and current tier from your cache-backed loyalty engine.
     */
    private CustomerDTO mapToDTO(Customer customer) {

        CustomerDTO balanceInfo = loyaltyService.getCustomerByEmail(customer.getEmail());

        AddressDTO addressDTO = null;
        if (customer.getAddress() != null) {
            addressDTO = new AddressDTO(
                    customer.getAddress().getStreetNo(),
                    customer.getAddress().getStreet(),
                    customer.getAddress().getCity(),
                    customer.getAddress().getState(),
                    customer.getAddress().getZipCode(),
                    customer.getAddress().getCountry()
            );
        }

        return new CustomerDTO(
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getPhoneNo(),
                balanceInfo.getCurrentTier(),
                balanceInfo.getPointsBalance(),
                balanceInfo.getRollingSpend(),
                addressDTO
        );
    }
}