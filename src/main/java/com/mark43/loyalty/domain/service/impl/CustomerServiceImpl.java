package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.service.CustomerService;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Override
    public CustomerDTO createCustomer(CustomerDTO customerDTO) {

        if (customerRepository.findByEmail(customerDTO.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Customer with email " + customerDTO.getEmail() + " already exists.");
        }

        log.info("Creating new customer with email: {}", customerDTO.getEmail());

        Customer customerEntity = convertToEntity(customerDTO);
        Customer savedCustomer = customerRepository.save(customerEntity);

        return convertToDto(savedCustomer);
    }

    @Override
    public CustomerDTO getCustomerById(Long customerId) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + customerId));

        return convertToDto(customer);
    }

    @Override
    public CustomerDTO getCustomerByEmail(String email) {

        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with email: " + email));

        return convertToDto(customer);
    }

    @Override
    public CustomerDTO getCustomerByPhoneNo(String phoneNo) {

        Customer customer = customerRepository.findByPhoneNo(phoneNo)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with phone no: " + phoneNo));

        return convertToDto(customer);
    }

    @Override
    public List<CustomerDTO> getAllCustomers() {

        return customerRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public CustomerDTO updateCustomer(Long customerId, CustomerDTO customerDTO) {

        // Fetch the raw entity from the database internally
        Customer existingCustomer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + customerId));

        // Apply modifications from the incoming DTO payload
        existingCustomer.setFirstName(customerDTO.getFirstName());
        existingCustomer.setLastName(customerDTO.getLastName());
        existingCustomer.setPhoneNo(customerDTO.getPhoneNo());

        // Protect against email updates conflicts
        if (customerDTO.getEmail() != null && !customerDTO.getEmail().equals(existingCustomer.getEmail())) {
            if (customerRepository.findByEmail(customerDTO.getEmail()).isPresent()) {
                throw new IllegalArgumentException("Email " + customerDTO.getEmail() + " is already taken.");
            }
            existingCustomer.setEmail(customerDTO.getEmail());
        }

        log.info("Updating customer ID: {}", customerId);

        Customer updatedCustomer = customerRepository.save(existingCustomer);

        return convertToDto(updatedCustomer);
    }

    @Override
    public void deleteCustomer(Long customerId) {

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with ID: " + customerId));

        customerRepository.delete(customer);

        log.info("Deleted customer ID: {}", customerId);
    }

    private Customer convertToEntity(CustomerDTO dto) {

        Customer entity = new Customer();
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setPhoneNo(dto.getPhoneNo());

        return entity;
    }

    private CustomerDTO convertToDto(Customer entity) {

        CustomerDTO dto = new CustomerDTO();
        dto.setFirstName(entity.getFirstName());
        dto.setLastName(entity.getLastName());
        dto.setEmail(entity.getEmail());
        dto.setPhoneNo(entity.getPhoneNo());
        dto.setCurrentTier(entity.getCurrentTier());

        return dto;
    }
}