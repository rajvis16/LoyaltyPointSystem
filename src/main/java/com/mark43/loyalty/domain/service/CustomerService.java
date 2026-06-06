package com.mark43.loyalty.domain.service;

import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import java.util.List;

public interface CustomerService {

    /**
     * Registers a new customer in the loyalty system using incoming payload state.
     *
     * @param customerDTO The data transfer object holding customer registration properties.
     * @return The fully populated CustomerDTO including its newly generated database ID.
     */
    CustomerDTO createCustomer(CustomerDTO customerDTO);

    /**
     * Retrieves a single customer profile by their unique email anchor.
     */
    CustomerDTO getCustomerByEmail(String email);

    /**
     * Retrieves a single customer profile by their unique telephone number.
     */
    CustomerDTO getCustomerByPhoneNo(String phoneNo);

    /**
     * Fetches all registered customer records converted to DTO formats.
     */
    List<CustomerDTO> getAllCustomers();

    /**
     * Updates an existing customer profile's matching attributes.
     */
    CustomerDTO updateCustomer(CustomerDTO customerDTO);

    /**
     * Removes a customer profile completely from the persistence layer.
     */
    void deleteCustomer(String email);
}