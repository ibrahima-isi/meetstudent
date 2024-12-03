package com.bowe.meetstudent.entities.embedded;

import jakarta.persistence.Embeddable;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Embeddable
public class Address {

    private String location;
    private String city;
    private String Country;
}
