package com.mark43.loyalty.interfaces.dto;

import com.mark43.loyalty.domain.entity.Tier;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerBalanceDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNo;
    private Tier currentTier;      // "Silver", or "Gold" or "Platinum"
    private BigDecimal pointsBalance;
    private BigDecimal rollingSpend;
}