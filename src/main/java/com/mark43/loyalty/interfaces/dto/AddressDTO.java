package com.mark43.loyalty.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {

    @NotNull(message = "Street number cannot be blank.")
    private int streetNo;

    @NotBlank(message = "Street address cannot be blank.")
    private String street;

    @NotBlank(message = "City cannot be blank.")
    private String city;

    @NotBlank(message = "State cannot be blank.")
    private String state;

    @NotBlank(message = "Zip code cannot be blank.")
    private String zipCode;

    @NotBlank(message = "Country cannot be blank.")
    private String country;
}