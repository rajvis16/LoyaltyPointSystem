package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.CustomerProductLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerProductLedgerRepository extends JpaRepository<CustomerProductLedgerEntry, Long> {

    /**
     * Retrieves the entire product ledger history for a specific order boundary.
     * Essential for validating ownership and quantities during partial or full return workflows.
     */
    List<CustomerProductLedgerEntry> findByCustomerIdAndPurchaseId(Long customerId, String purchaseId);
}