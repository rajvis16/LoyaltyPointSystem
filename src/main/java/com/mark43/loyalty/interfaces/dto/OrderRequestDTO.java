package com.mark43.loyalty.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDTO {

    @NotBlank
    private String customerEmail;

    @NotBlank
    private String purchaseReference;

    @NotEmpty
    private Map<String, @Min(value = 1, message = "Quantity must be positive") Integer> productQuantities;
}