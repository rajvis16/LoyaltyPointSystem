package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.PointLedgerEntry;
import com.mark43.loyalty.domain.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /**
     * Sum all of the remaining points for EARN that are unexpired
     */
    @Query("SELECT COALESCE(SUM(p.remainingPoints), 0.00) FROM PointLedgerEntry p " +
            "WHERE p.customerId = :customerId " +
            "AND p.transactionType = com.mark43.loyalty.domain.entity.TransactionType.EARN " +
            "AND p.expiryDate > :now")
    BigDecimal calculateActivePointsBalance(@Param("customerId") Long customerId, @Param("now") LocalDateTime now);

    /**
     * Finds all unexpired, active EARN point buckets for a customer
     * that still have spendable balances, ordered by expiration date (closest first).
     */
    @Query("SELECT p FROM PointLedgerEntry p " +
            "WHERE p.customerId = :customerId " +
            "AND p.transactionType = com.mark43.loyalty.domain.entity.TransactionType.EARN " +
            "AND p.remainingPoints > 0 " +
            "AND p.expiryDate > :now " +
            "ORDER BY p.expiryDate ASC")
    List<PointLedgerEntry> findAvailableEarnEntries(@Param("customerId") Long customerId, @Param("now") LocalDateTime now);

}