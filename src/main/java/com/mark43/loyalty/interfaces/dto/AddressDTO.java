package com.mark43.loyalty.interfaces.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressDTO {
    private String streetNo;
    private String streetName;
    private String city;
    private String state;
    private String country;
}