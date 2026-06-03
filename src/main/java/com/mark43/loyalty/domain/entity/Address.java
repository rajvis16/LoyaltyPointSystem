package com.mark43.loyalty.domain.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private int streetNo;
    private String streetName;
    private String city;
    private String state;
    private String country;
}