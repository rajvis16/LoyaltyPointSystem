package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.mark43.loyalty.domain.entity.Tier.SILVER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private CustomerDTO inputDto;
    private Customer savedEntity;

    @BeforeEach
    void setUp() {

        inputDto = new CustomerDTO();
        inputDto.setFirstName("Alice");
        inputDto.setLastName("Smith");
        inputDto.setEmail("alice@example.com");
        inputDto.setPhoneNo("555-0199");

        savedEntity = new Customer();
        savedEntity.setCustomerId(1L); // Database assigned ID
        savedEntity.setFirstName("Alice");
        savedEntity.setLastName("Smith");
        savedEntity.setEmail("alice@example.com");
        savedEntity.setPhoneNo("555-0199");
        savedEntity.setCurrentTier(SILVER);
    }

    @Test
    void verifyIfCreateCustomerSucceedsAndConvertsToSecureDtoWithoutId() {

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenReturn(savedEntity);

        CustomerDTO result = customerService.createCustomer(inputDto);

        assertNotNull(result);
        assertEquals("Alice", result.getFirstName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(SILVER, result.getCurrentTier());

        // Verify internal save mapping logic did not send an identity anchor upstream
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository, times(1)).save(customerCaptor.capture());
        assertNull(customerCaptor.getValue().getCustomerId());
    }

    @Test
    void verifyIfCreateCustomerThrowsExceptionWhenEmailAlreadyExists() {

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(savedEntity));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.createCustomer(inputDto)
        );

        assertTrue(exception.getMessage().contains("already exists"));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void verifyIfGetCustomerByIdSucceedsAndConvertsToSecureDto() {

        when(customerRepository.findById(1L)).thenReturn(Optional.of(savedEntity));

        CustomerDTO result = customerService.getCustomerById(1L);

        assertNotNull(result);
        assertEquals("Alice", result.getFirstName());
        assertEquals("alice@example.com", result.getEmail());
    }

    @Test
    void verifyIfGetCustomerByIdThrowsExceptionWhenIdIsNotFound() {

        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.getCustomerById(99L)
        );

        assertTrue(exception.getMessage().contains("Customer not found with ID: 99"));
    }

    @Test
    void verifyIfGetCustomerByEmailSucceedsAndConvertsToSecureDto() {

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(savedEntity));

        CustomerDTO result = customerService.getCustomerByEmail("alice@example.com");

        assertNotNull(result);
        assertEquals("alice@example.com", result.getEmail());
    }

    @Test
    void verifyIfGetCustomerByPhoneNoSucceedsAndConvertsToSecureDto() {

        when(customerRepository.findByPhoneNo("555-0199")).thenReturn(Optional.of(savedEntity));

        CustomerDTO result = customerService.getCustomerByPhoneNo("555-0199");

        assertNotNull(result);
        assertEquals("555-0199", result.getPhoneNo());
    }

    @Test
    void verifyIfGetAllCustomersReturnsFullyMappedSecureDtoCollection() {

        Customer bob = new Customer(2L, "Bob", "Jones", "bob@example.com", "555-0200", SILVER, null);
        when(customerRepository.findAll()).thenReturn(Arrays.asList(savedEntity, bob));

        List<CustomerDTO> results = customerService.getAllCustomers();

        assertEquals(2, results.size());
        assertEquals("Alice", results.get(0).getFirstName());
        assertEquals("Bob", results.get(1).getFirstName());
    }

    @Test
    void verifyIfUpdateCustomerSucceedsWhenModifyingPermittedFields() {

        CustomerDTO updatePayload = new CustomerDTO("Alice-M", "Smith-New", "alice@example.com", "555-9999", SILVER, null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(savedEntity));
        when(customerRepository.save(any(Customer.class))).thenReturn(savedEntity);

        CustomerDTO result = customerService.updateCustomer(1L, updatePayload);

        assertNotNull(result);
        verify(customerRepository, times(1)).save(savedEntity);
        assertEquals("Alice-M", savedEntity.getFirstName());
        assertEquals("555-9999", savedEntity.getPhoneNo());
    }

    @Test
    void verifyIfUpdateCustomerThrowsExceptionWhenEmailConflictOccurs() {

        CustomerDTO updatePayload = new CustomerDTO("Alice", "Smith", "clashing@example.com", "555-0199", SILVER, null);
        Customer clashingCustomer = new Customer(2L, "Bob", "Jones", "clashing@example.com", "555-0200", SILVER, null);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(savedEntity));
        when(customerRepository.findByEmail("clashing@example.com")).thenReturn(Optional.of(clashingCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.updateCustomer(1L, updatePayload)
        );

        assertTrue(exception.getMessage().contains("is already taken"));
        verify(customerRepository, never()).save(savedEntity);
    }

    @Test
    void verifyIfDeleteCustomerSucceedsWhenIdExists() {

        when(customerRepository.findById(1L)).thenReturn(Optional.of(savedEntity));

        customerService.deleteCustomer(1L);

        verify(customerRepository, times(1)).delete(savedEntity);
    }
}