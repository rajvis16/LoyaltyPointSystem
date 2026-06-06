package com.mark43.loyalty.interfaces.dto;

import com.mark43.loyalty.domain.entity.Tier;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {

    @NotBlank(message = "First name is required and cannot be blank.")
    private String firstName;

    @NotBlank(message = "Last name is required and cannot be blank.")
    private String lastName;

    @NotBlank(message = "Email address is required.")
    private String email;

    @NotBlank(message = "Phone number is required.")
    private String phoneNo;

    private Tier currentTier;

    private BigDecimal pointsBalance;

    private BigDecimal rollingSpend;

    private AddressDTO address;
}