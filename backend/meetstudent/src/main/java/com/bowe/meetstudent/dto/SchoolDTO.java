package com.bowe.meetstudent.dto;


import com.bowe.meetstudent.entities.embedded.Address;
import lombok.*;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SchoolDTO extends BaseDTO{

    private Date creation;
    private Address address;

}