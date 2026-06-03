package com.mark43.loyalty.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_aggregates", indexes = {
        @Index(name = "idx_purchase_calc", columnList = "customerId, purchaseDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAggregate {

    @Id
    private String purchaseId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private BigDecimal totalAmountSpent;

    @Column(nullable = false)
    private LocalDateTime purchaseDate;
}