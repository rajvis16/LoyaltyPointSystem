package com.mark43.loyalty.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_product_ledger", indexes = {
        @Index(name = "idx_customer_purchase_lookup", columnList = "customerId, purchaseId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProductLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String purchaseId; // Maps 1:1 to the purchaseReference string used in PointLedgerEntry

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductAction action; // BOUGHT or RETURNED

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDateTime transactionDate;
}