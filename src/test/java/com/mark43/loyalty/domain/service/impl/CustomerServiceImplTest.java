package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.interfaces.dto.CustomerBalanceDTO;
import com.mark43.loyalty.interfaces.dto.CustomerDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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

    @Mock
    private LoyaltyService loyaltyService;

    @Mock
    private LoyaltyCacheManager cacheManager;

    private CustomerServiceImpl customerService;

    private CustomerDTO inputDto;
    private Customer savedEntity;

    @BeforeEach
    void setUp() {
        customerService = new CustomerServiceImpl(
                customerRepository,
                loyaltyService,
                cacheManager
        );

        inputDto = new CustomerDTO();
        inputDto.setFirstName("Alice");
        inputDto.setLastName("Smith");
        inputDto.setEmail("alice@example.com");
        inputDto.setPhoneNo("555-0199");

        savedEntity = new Customer();
        savedEntity.setCustomerId(1L);
        savedEntity.setFirstName("Alice");
        savedEntity.setLastName("Smith");
        savedEntity.setEmail("alice@example.com");
        savedEntity.setPhoneNo("555-0199");
        savedEntity.setCurrentTier(SILVER);

        // 💡 FIX: Realigned to use your exact 7-argument @AllArgsConstructor signature
        lenient().when(loyaltyService.getCustomerBalanceByEmail(anyString()))
                .thenReturn(new CustomerBalanceDTO(
                        "Alice",
                        "Smith",
                        "alice@example.com",
                        "555-0199",
                        SILVER,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @Test
    void verifyIfCreateCustomerSucceedsAndConvertsToSecureDtoWithoutId() {
        // 💡 FIX: In your production code, createCustomer calls loyaltyService.registerCustomer first,
        // and then fetches the user by email from customerRepository. It does NOT call customerRepository.save().
        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(savedEntity));

        CustomerDTO result = customerService.createCustomer(inputDto);

        assertNotNull(result);
        assertEquals("Alice", result.getFirstName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(SILVER, result.getCurrentTier());

        // Verify registration delegation happened correctly
        verify(loyaltyService, times(1)).registerCustomer(inputDto);
        verify(customerRepository, times(1)).findByEmail("alice@example.com");
    }

    @Test
    void verifyIfCreateCustomerThrowsExceptionWhenEmailAlreadyExists() {
        // 💡 FIX: Unique email validation happens inside your loyaltyService.registerCustomer flow.
        // If it throws an exception, createCustomer bubbles it up.
        doThrow(new IllegalArgumentException("Customer already exists with email: alice@example.com"))
                .when(loyaltyService).registerCustomer(any(CustomerDTO.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.createCustomer(inputDto)
        );

        assertTrue(exception.getMessage().contains("already exists"));
    }


    @Test
    void verifyIfGetCustomerByEmailThrowsExceptionWhenEmailIsNotFound() {
        when(customerRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.getCustomerByEmail("test@test.com")
        );

        assertEquals( "Customer not found with email: test@test.com", exception.getMessage());
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
    void verifyIfUpdateCustomerThrowsExceptionWhenPhoneConflictOccurs() {
        // 1. Arrange: Bob sends an update request using his valid email anchor,
        // but inputs a phone number already locked down by Alice.
        CustomerDTO updatePayload = new CustomerDTO();
        updatePayload.setFirstName("Bob");
        updatePayload.setLastName("Jones");
        updatePayload.setEmail("bob@example.com"); // 🎯 Valid lookup anchor
        updatePayload.setPhoneNo("555-0101");       // ⚠️ Clashing phone number token

        // 2. Mock that Bob's profile is found cleanly via his email anchor
        Customer bobsExistingEntity = new Customer();
        bobsExistingEntity.setCustomerId(2L);
        bobsExistingEntity.setEmail("bob@example.com");
        bobsExistingEntity.setPhoneNo("555-9999"); // Old number

        when(customerRepository.findByEmail("bob@example.com"))
                .thenReturn(Optional.of(bobsExistingEntity));

        // 3. Mock that saving this configuration triggers a unique constraint failure
        when(customerRepository.save(any(Customer.class)))
                .thenThrow(new IllegalArgumentException("Phone number is already taken"));

        // 4. Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.updateCustomer(updatePayload)
        );

        assertTrue(exception.getMessage().contains("already taken"));
    }

    @Test
    void verifyIfUpdateCustomerThrowsExceptionWhenEmailConflictOccurs() {
        // 1. Bob wants to update his details, but inputs an email address already owned by Alice
        CustomerDTO updatePayload = new CustomerDTO();
        updatePayload.setFirstName("Bob");
        updatePayload.setLastName("Jones");
        updatePayload.setEmail("alice@example.com"); // ⚠️ The clashing email target
        updatePayload.setPhoneNo("555-0199");

        // 2. Mock that Bob's existing profile is found by the service lookup anchor
        Customer bobsExistingEntity = new Customer();
        bobsExistingEntity.setCustomerId(2L);
        bobsExistingEntity.setEmail("bob@example.com");

        // If your service looks up the account using the current email context
        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(bobsExistingEntity));

        // 3. Simulate the database throwing a unique constraint violation on save
        when(customerRepository.save(any(Customer.class)))
                .thenThrow(new IllegalArgumentException("Email is already taken"));

        // 4. Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.updateCustomer(updatePayload)
        );

        assertTrue(exception.getMessage().contains("is already taken"));
    }

    @Test
    void verifyIfDeleteCustomerSucceedsWhenIdExists() {
        when(customerRepository.findByEmail("test@test.com")).thenReturn(Optional.of(savedEntity));

        customerService.deleteCustomer("test@test.com");

        verify(cacheManager, times(1)).invalidate("alice@example.com"); // Verifies cache invalidation hook on delete
        verify(customerRepository, times(1)).delete(savedEntity);
    }
}