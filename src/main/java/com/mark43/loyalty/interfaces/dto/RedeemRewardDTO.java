package com.mark43.loyalty.interfaces.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RedeemRewardDTO {
    private String customerEmail;
    private String rewardName;
}