package com.mark43.loyalty.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EarnPointsDTO {

    @NotBlank(message = "Customer email is required and cannot be blank.")
    private String customerEmail;

    @NotBlank(message = "Purchase reference is required and cannot be blank.")
    private String purchaseReference; // Captures '123' or 'order-abc'

    @NotEmpty(message = "Product names list cannot be null or empty.")
    private List<String> productNames;

    private BigDecimal totalSpend;
}