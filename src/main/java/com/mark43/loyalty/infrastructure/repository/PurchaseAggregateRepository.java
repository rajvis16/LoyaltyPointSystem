package com.mark43.loyalty.infrastructure.repository;

import com.mark43.loyalty.domain.entity.PurchaseAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface PurchaseAggregateRepository extends JpaRepository<PurchaseAggregate, String> {

    /**
     * Dynamically aggregates total dollar spend inside the rolling 12-month window.
     * Avoids N+1 query overhead by running a highly optimized SUM aggregation on indexed columns.
     */
    @Query("SELECT COALESCE(SUM(p.totalAmountSpent), 0) FROM PurchaseAggregate p " +
            "WHERE p.customerId = :customerId AND p.purchaseDate >= :cutoffDate")
    BigDecimal calculateRollingSpend(@Param("customerId") Long customerId,
                                     @Param("cutoffDate") LocalDateTime cutoffDate);
}