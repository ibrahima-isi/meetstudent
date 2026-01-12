package com.bowe.meetstudent.dto;


import com.bowe.meetstudent.entities.embedded.Address;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class SchoolDTO extends BaseDTO{

    private Date creation;
    private Address address;
    private String logoUrl;
    private String coverPhotoUrl;

}
