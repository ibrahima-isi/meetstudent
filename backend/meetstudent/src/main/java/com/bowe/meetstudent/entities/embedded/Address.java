package com.bowe.meetstudent.entities.embedded;

import jakarta.persistence.Embeddable;
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

    private String location;
    private String city;
    private String country;
}
