package com.mark43.loyalty.interfaces.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewardDTO {

    private Long rewardId;
    private String name;
    private String description;
    private BigDecimal pointsRequired;
}