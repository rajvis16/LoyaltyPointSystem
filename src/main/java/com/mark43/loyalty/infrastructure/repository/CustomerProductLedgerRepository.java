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

    @Query("SELECT COALESCE(SUM(" +
            "  CASE WHEN c.action = com.mark43.loyalty.domain.entity.ProductAction.BOUGHT THEN c.totalSpendingPerProduct " +
            "       WHEN c.action = com.mark43.loyalty.domain.entity.ProductAction.RETURNED THEN -c.totalSpendingPerProduct " +
            "       ELSE 0.00 END), 0.00) " +
            "FROM CustomerProductLedgerEntry c " +
            "WHERE c.customerId = :customerId " +
            "AND c.transactionDate >= :startDate")
    BigDecimal calculateNetRollingSpend(
            @Param("customerId") Long customerId,
            @Param("startDate") LocalDateTime startDate
    );

    @Query("SELECT l.productId, SUM(l.quantity) " +
            "FROM CustomerProductLedgerEntry l " +
            "WHERE l.customerId = :customerId AND l.purchaseId = :purchaseId " +
            "GROUP BY l.productId")
    List<Object[]> findNetOwnedInventory(@Param("customerId") Long customerId, @Param("purchaseId") String purchaseId);
}