package com.mark43.loyalty.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_ledger_entries", indexes = {
        @Index(name = "idx_ledger_customer", columnList = "customerId"),
        @Index(name = "idx_ledger_expiry", columnList = "expiryDate")
})
@Getter
@Setter
@NoArgsConstructor
public class PointLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pointLedgerEntryId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(nullable = false)
    private BigDecimal points;

    private String purchaseId;
    private Long rewardId;
    private LocalDateTime expiryDate;

    // When the customer earned points, what tier was active
    // Handy when we have to do the clawback
    private BigDecimal tierPointUsed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_entry_id")
    private PointLedgerEntry parentEntry;
}