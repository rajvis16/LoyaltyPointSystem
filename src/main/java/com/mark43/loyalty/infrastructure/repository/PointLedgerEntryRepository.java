package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.PointLedgerEntry;
import com.mark43.loyalty.domain.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PointLedgerEntryRepository extends JpaRepository<PointLedgerEntry, Long> {

    /**
     * Fetches transaction records sequentially to power the FIFO
     * point matching algorithm in memory, sorted by ID to maintain a strict audit timeline
     */
    List<PointLedgerEntry> findByCustomerIdOrderByPointLedgerEntryIdAsc(Long customerId);

    /**
     * Checks if an EARN event has already been targeted by a CLAWBACK entry.
     * Blocks duplicate refund/clawback attempts at the database gateway level.
     */
    boolean existsByParentEntry(PointLedgerEntry parentEntry);

    /**
     * Idempotency Check: Ensures an order is never processed twice for points.
     */
    boolean existsByPurchaseId(String purchaseId);

    /**
     * Fetches entry by purchaseReference and transaction type
     */
    Optional<PointLedgerEntry> findByPurchaseIdAndTransactionType(String purchaseReference, TransactionType transactionType);

}