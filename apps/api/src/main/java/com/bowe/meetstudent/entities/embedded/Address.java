package com.bowe.meetstudent.entities.embedded;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * An embeddable Address to ensure that anytime an address is needed, we use it to avoid repetitive code.
 * @author ibrabowe97
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Embeddable
@Builder
public class Address {

    @Size(max = 255, message = "location must be at most 255 characters")
    private String location;
    @Size(max = 255, message = "city must be at most 255 characters")
    private String city;
    @Size(max = 255, message = "country must be at most 255 characters")
    private String country;
}
