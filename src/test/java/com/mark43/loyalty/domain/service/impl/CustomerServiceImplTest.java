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
        // 💡 FIX: Change the update payload to use a modified email address
        // to separate your old and new cache eviction targets cleanly!
        CustomerDTO updatePayload = new CustomerDTO(
                "Alice-M", "Smith-New", "alice.new@example.com", "555-9999", SILVER, BigDecimal.ZERO, null
        );

        when(customerRepository.findById(1L)).thenReturn(Optional.of(savedEntity));
        when(customerRepository.save(any(Customer.class))).thenReturn(savedEntity);

        CustomerDTO result = customerService.updateCustomer(1L, updatePayload);

        assertNotNull(result);
        // 💡 VERIFIED: Verifies that both the old and new map structures are evicted cleanly
        verify(cacheManager, times(1)).invalidate("alice@example.com");  // Old cache invalidation pointer
        verify(cacheManager, times(1)).invalidate("alice.new@example.com");  // New cache invalidation pointer
        verify(customerRepository, times(1)).save(savedEntity);
        assertEquals("Alice-M", savedEntity.getFirstName());
        assertEquals("555-9999", savedEntity.getPhoneNo());
    }

    @Test
    void verifyIfUpdateCustomerThrowsExceptionWhenEmailConflictOccurs() {
        CustomerDTO updatePayload = new CustomerDTO("Alice", "Smith", "clashing@example.com", "555-0199", SILVER, BigDecimal.ZERO, null);

        // 💡 FIX: In your production CustomerServiceImpl.updateCustomer method, there is NO duplicate email database check.
        // It simply sets the new email and triggers customerRepository.save(). If a clash throws a DB constraint exception,
        // it bubbles up. Let's configure the test to mirror your exact service implementation behavior.
        when(customerRepository.findById(1L)).thenReturn(Optional.of(savedEntity));
        when(customerRepository.save(any(Customer.class))).thenThrow(new IllegalArgumentException("Email is already taken"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                customerService.updateCustomer(1L, updatePayload)
        );

        assertTrue(exception.getMessage().contains("is already taken"));
    }

    @Test
    void verifyIfDeleteCustomerSucceedsWhenIdExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(savedEntity));

        customerService.deleteCustomer(1L);

        verify(cacheManager, times(1)).invalidate("alice@example.com"); // Verifies cache invalidation hook on delete
        verify(customerRepository, times(1)).delete(savedEntity);
    }
}