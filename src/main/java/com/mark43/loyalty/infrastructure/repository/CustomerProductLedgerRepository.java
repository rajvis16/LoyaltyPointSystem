package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.CustomerProductLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CustomerProductLedgerRepository extends JpaRepository<CustomerProductLedgerEntry, Long> {

    /**
     * Retrieves the entire product ledger history for a specific order boundary.
     * Essential for validating ownership and quantities during partial or full return workflows.
     */
    List<CustomerProductLedgerEntry> findByCustomerIdAndPurchaseId(Long customerId, String purchaseId);

    /**
     * Calculates the net rolling financial spend over a specified window.
     * Because BOUGHT rows are stored positive and RETURNED rows are stored negative,
     * the query aggregates the net spending.
     */
    @Query("SELECT COALESCE(SUM(c.totalSpendingPerProduct), 0.00) " +
            "FROM CustomerProductLedgerEntry c " +
            "WHERE c.customerId = :customerId " +
            "AND c.transactionDate >= :startDate")
    BigDecimal calculateNetRollingSpend(
            @Param("customerId") Long customerId,
            @Param("startDate") LocalDateTime startDate
    );

    /**
     * Groups and computes the absolute net product quantity remaining with the customer
     * for a given purchase context reference to prevent cross-order return exploitation.
     */
    @Query("SELECT l.productId, " +
            "SUM(CASE WHEN l.action = com.mark43.loyalty.domain.entity.ProductAction.BOUGHT THEN l.quantity " +
            "         WHEN l.action = com.mark43.loyalty.domain.entity.ProductAction.RETURNED THEN -l.quantity " +
            "         ELSE 0 END) " +
            "FROM CustomerProductLedgerEntry l " +
            "WHERE l.customerId = :customerId AND l.purchaseId = :purchaseId " +
            "GROUP BY l.productId")
    List<Object[]> findNetOwnedInventory(@Param("customerId") Long customerId, @Param("purchaseId") String purchaseId);
}