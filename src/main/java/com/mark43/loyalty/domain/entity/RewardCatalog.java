package com.mark43.loyalty.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "reward_catalog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RewardCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rewardId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal pointsRequired;
}