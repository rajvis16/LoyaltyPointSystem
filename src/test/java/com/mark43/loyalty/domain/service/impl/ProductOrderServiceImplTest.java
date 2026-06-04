package com.mark43.loyalty.domain.service.impl;

import com.mark43.loyalty.domain.entity.Customer;
import com.mark43.loyalty.domain.entity.CustomerProductLedgerEntry;
import com.mark43.loyalty.domain.entity.Product;
import com.mark43.loyalty.domain.entity.ProductAction;
import com.mark43.loyalty.domain.service.LoyaltyService;
import com.mark43.loyalty.infrastructure.repository.CustomerProductLedgerRepository;
import com.mark43.loyalty.infrastructure.repository.CustomerRepository;
import com.mark43.loyalty.infrastructure.repository.ProductRepository;
import com.mark43.loyalty.interfaces.dto.EarnPointsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductOrderServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CustomerProductLedgerRepository productLedgerRepository;

    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private ProductOrderServiceImpl productOrderService;

    private Customer mockCustomer;
    private Product mockProduct1;
    private Product mockProduct2;

    @BeforeEach
    void setUp() {
        mockCustomer = new Customer();
        mockCustomer.setCustomerId(1L);
        mockCustomer.setEmail("alice@example.com");

        mockProduct1 = new Product(101L, "Laptop", "", new BigDecimal("1000.00"));
        mockProduct2 = new Product(102L, "Mouse", "", new BigDecimal("50.00"));
    }

    @Test
    void verifyIfBuyProductsSucceedsAndLogsMultipleItemsToLedgerAndTriggersLoyaltyEngine() {

        String purchaseRef = "TXN-777";
        Map<String, Integer> orderPayload = new LinkedHashMap<>();
        orderPayload.put("Laptop", 1);
        orderPayload.put("Mouse", 2);

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));
        when(productRepository.findByName("Laptop")).thenReturn(Optional.of(mockProduct1));
        when(productRepository.findByName("Mouse")).thenReturn(Optional.of(mockProduct2));

        productOrderService.buyProducts("alice@example.com", purchaseRef, orderPayload);

        ArgumentCaptor<CustomerProductLedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(CustomerProductLedgerEntry.class);
        verify(productLedgerRepository, times(2)).save(ledgerCaptor.capture());
        List<CustomerProductLedgerEntry> savedEntries = ledgerCaptor.getAllValues();

        CustomerProductLedgerEntry laptopEntry = savedEntries.getFirst();
        assertEquals(1L, laptopEntry.getCustomerId());
        assertEquals(101L, laptopEntry.getProductId());
        assertEquals(purchaseRef, laptopEntry.getPurchaseId());
        assertEquals(ProductAction.BOUGHT, laptopEntry.getAction());
        assertEquals(1, laptopEntry.getQuantity());

        CustomerProductLedgerEntry mouseEntry = savedEntries.get(1);
        assertEquals(102L, mouseEntry.getProductId());
        assertEquals(ProductAction.BOUGHT, mouseEntry.getAction());
        assertEquals(2, mouseEntry.getQuantity());

        ArgumentCaptor<EarnPointsDTO> loyaltyCaptor = ArgumentCaptor.forClass(EarnPointsDTO.class);
        verify(loyaltyService, times(1)).earnPoints(loyaltyCaptor.capture());

        EarnPointsDTO transmittedDto = loyaltyCaptor.getValue();
        assertEquals("alice@example.com", transmittedDto.getCustomerEmail());
        assertEquals(purchaseRef, transmittedDto.getPurchaseReference());

        List<String> expectedItems = Arrays.asList("Laptop", "Mouse", "Mouse");
        assertEquals(expectedItems, transmittedDto.getProductNames());
    }

    @Test
    void verifyIfBuyProductsThrowsExceptionWhenCustomerIsMissingFromDatabase() {

        Map<String, Integer> payload = Collections.singletonMap("Laptop", 1);
        when(customerRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.buyProducts("missing@example.com", "TXN-000", payload)
        );

        assertTrue(exception.getMessage().contains("Customer not found with email: missing@example.com"));
        verify(productLedgerRepository, never()).save(any());
        verify(loyaltyService, never()).earnPoints(any());
    }

    @Test
    void verifyIfBuyProductsThrowsExceptionWhenProductNotFoundInCatalog() {

        Map<String, Integer> payload = Collections.singletonMap("GhostItem", 1);
        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));
        when(productRepository.findByName("GhostItem")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.buyProducts("alice@example.com", "TXN-000", payload)
        );

        assertTrue(exception.getMessage().contains("Product not found in catalog: GhostItem"));
        verify(productLedgerRepository, never()).save(any());
        verify(loyaltyService, never()).earnPoints(any());
    }

    @Test
    void verifyIfBuyProductsThrowsExceptionWhenQuantityIsZeroOrNegative() {

        Map<String, Integer> payload = Collections.singletonMap("Laptop", -5);
        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.buyProducts("alice@example.com", "TXN-000", payload)
        );

        assertTrue(exception.getMessage().contains("Quantity must be greater than zero for product: Laptop"));
        verify(productLedgerRepository, never()).save(any());
        verify(loyaltyService, never()).earnPoints(any());
    }

    @Test
    void verifyIfBuyProductsThrowsExceptionWhenPayloadIsNull() {

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.buyProducts("alice@example.com", "TXN-000", null)
        );

        assertTrue(exception.getMessage().contains("Cannot process a purchase with empty product selection."));
    }

    @Test
    void verifyIfBuyProductsThrowsExceptionWhenPayloadIsEmpty() {

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.buyProducts("alice@example.com", "TXN-000", Collections.emptyMap())
        );

        assertTrue(exception.getMessage().contains("Cannot process a purchase with empty product selection."));
    }

    @Test
    void verifyIfReturnProductsSucceedsForSingleProductWithNoPriorReturns() {

        String purchaseRef = "TXN-100";
        Map<String, Integer> returnPayload = Collections.singletonMap("Laptop", 1);

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));
        when(productRepository.findByName("Laptop")).thenReturn(Optional.of(mockProduct1));

        CustomerProductLedgerEntry initialPurchase = new CustomerProductLedgerEntry();
        initialPurchase.setProductId(101L);
        initialPurchase.setAction(ProductAction.BOUGHT);
        initialPurchase.setQuantity(2);

        when(productLedgerRepository.findByCustomerIdAndPurchaseId(1L, purchaseRef))
                .thenReturn(Collections.singletonList(initialPurchase));

        productOrderService.returnProducts("alice@example.com", purchaseRef, returnPayload);

        ArgumentCaptor<CustomerProductLedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(CustomerProductLedgerEntry.class);
        verify(productLedgerRepository, times(1)).save(ledgerCaptor.capture());

        CustomerProductLedgerEntry savedReturn = ledgerCaptor.getValue();
        assertEquals(ProductAction.RETURNED, savedReturn.getAction());
        assertEquals(1, savedReturn.getQuantity());
        assertEquals(101L, savedReturn.getProductId());

        verify(loyaltyService, times(1)).clawbackPoints(purchaseRef, Collections.singletonList(101L));
    }

    @Test
    void verifyIfReturnProductsSucceedsForComplexHistoryWithMixOfBoughtAndReturnedRows() {

        String purchaseRef = "TXN-200";
        Map<String, Integer> returnPayload = new LinkedHashMap<>();
        returnPayload.put("Laptop", 1); // Had 2, returned 1 previously -> net 1 remaining. Returning the last 1.
        returnPayload.put("Mouse", 2);  // Had 5, returned 1 previously -> net 4 remaining. Returning 2.

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));
        when(productRepository.findByName("Laptop")).thenReturn(Optional.of(mockProduct1));
        when(productRepository.findByName("Mouse")).thenReturn(Optional.of(mockProduct2));

        CustomerProductLedgerEntry buyLaptop = new CustomerProductLedgerEntry(null, 1L, 101L, purchaseRef, ProductAction.BOUGHT, 2, null);
        CustomerProductLedgerEntry returnLaptop = new CustomerProductLedgerEntry(null, 1L, 101L, purchaseRef, ProductAction.RETURNED, 1, null);

        CustomerProductLedgerEntry buyMouse = new CustomerProductLedgerEntry(null, 1L, 102L, purchaseRef, ProductAction.BOUGHT, 5, null);
        CustomerProductLedgerEntry returnMouse = new CustomerProductLedgerEntry(null, 1L, 102L, purchaseRef, ProductAction.RETURNED, 1, null);

        List<CustomerProductLedgerEntry> complexHistory = Arrays.asList(buyLaptop, returnLaptop, buyMouse, returnMouse);
        when(productLedgerRepository.findByCustomerIdAndPurchaseId(1L, purchaseRef)).thenReturn(complexHistory);

        productOrderService.returnProducts("alice@example.com", purchaseRef, returnPayload);

        ArgumentCaptor<CustomerProductLedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(CustomerProductLedgerEntry.class);
        verify(productLedgerRepository, times(2)).save(ledgerCaptor.capture());
        List<CustomerProductLedgerEntry> savedEntries = ledgerCaptor.getAllValues();

        assertEquals(1, savedEntries.get(0).getQuantity()); // 1 Laptop logged
        assertEquals(2, savedEntries.get(1).getQuantity()); // 2 Mice logged

        ArgumentCaptor<List<Long>> clawbackCaptor = ArgumentCaptor.forClass(List.class);
        verify(loyaltyService, times(1)).clawbackPoints(eq(purchaseRef), clawbackCaptor.capture());

        List<Long> capturedProductIds = clawbackCaptor.getValue();
        assertEquals(3, capturedProductIds.size());
        assertEquals(Arrays.asList(101L, 102L, 102L), capturedProductIds);
    }

    @Test
    void verifyIfReturnProductsThrowsExceptionWhenPriorReturnsAlreadyEqualOriginalPurchases() {

        String purchaseRef = "TXN-300";
        Map<String, Integer> returnPayload = Collections.singletonMap("Laptop", 1); // Attempting to return 1 more

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));
        when(productRepository.findByName("Laptop")).thenReturn(Optional.of(mockProduct1));

        CustomerProductLedgerEntry buyRecord = new CustomerProductLedgerEntry(null, 1L, 101L, purchaseRef, ProductAction.BOUGHT, 1, null);
        CustomerProductLedgerEntry returnRecord = new CustomerProductLedgerEntry(null, 1L, 101L, purchaseRef, ProductAction.RETURNED, 1, null);

        when(productLedgerRepository.findByCustomerIdAndPurchaseId(1L, purchaseRef))
                .thenReturn(Arrays.asList(buyRecord, returnRecord));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.returnProducts("alice@example.com", purchaseRef, returnPayload)
        );

        assertTrue(exception.getMessage().contains("Customer does not own any valid units of product 'Laptop'"));
        verify(productLedgerRepository, never()).save(any());
        verify(loyaltyService, never()).clawbackPoints(any(), any());
    }

    @Test
    void verifyIfReturnProductsThrowsExceptionWhenReturnQuantityExceedsNetOwnedBalance() {

        String purchaseRef = "TXN-400";
        Map<String, Integer> returnPayload = Collections.singletonMap("Laptop", 5); // Owning 2, trying to return 5

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));
        when(productRepository.findByName("Laptop")).thenReturn(Optional.of(mockProduct1));

        CustomerProductLedgerEntry buyRecord = new CustomerProductLedgerEntry(null, 1L, 101L, purchaseRef, ProductAction.BOUGHT, 2, null);
        when(productLedgerRepository.findByCustomerIdAndPurchaseId(1L, purchaseRef))
                .thenReturn(Collections.singletonList(buyRecord));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.returnProducts("alice@example.com", purchaseRef, returnPayload)
        );

        assertTrue(exception.getMessage().contains("Customer attempting to return 5 unit(s) of 'Laptop' but only net owns 2"));
        verify(productLedgerRepository, never()).save(any());
        verify(loyaltyService, never()).clawbackPoints(any(), any());
    }

    @Test
    void verifyIfReturnProductsThrowsExceptionWhenPurchaseReferenceIsNotFound() {

        String emptyRef = "TXN-EMPTY";
        Map<String, Integer> returnPayload = Collections.singletonMap("Laptop", 1);

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(mockCustomer));
        when(productLedgerRepository.findByCustomerIdAndPurchaseId(1L, emptyRef)).thenReturn(Collections.emptyList());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                productOrderService.returnProducts("alice@example.com", emptyRef, returnPayload)
        );

        assertTrue(exception.getMessage().contains("No original purchase ledger record found for reference: TXN-EMPTY"));
        verify(productRepository, never()).findByName(anyString());
        verify(productLedgerRepository, never()).save(any());
    }

    @Test
    void verifyIfReturnProductsThrowsExceptionWhenEmailContextIsBlankOrNull() {
        Map<String, Integer> returnPayload = Collections.singletonMap("Laptop", 1);

        assertThrows(IllegalArgumentException.class, () ->
                productOrderService.returnProducts(null, "TXN-100", returnPayload)
        );

        assertThrows(IllegalArgumentException.class, () ->
                productOrderService.returnProducts("   ", "TXN-100", returnPayload)
        );
    }
}