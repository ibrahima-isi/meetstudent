package com.bowe.meetstudent.dto;


import com.bowe.meetstudent.entities.embedded.Address;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class SchoolDTO extends BaseDTO{

    private Address address;
    private String logoUrl;
    private String coverPhotoUrl;

}
