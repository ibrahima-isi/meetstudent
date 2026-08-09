package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.entities.embedded.Address;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class SchoolDTO extends BaseDTO {

    private Address address;
    // input: ids of already-uploaded media
    private Integer logoMediaId;
    private Integer coverMediaId;
    // output: resolved media objects (with publicUrl)
    private MediaDTO logo;
    private MediaDTO cover;
    private Double averageRate;
    private List<TagDTO> tags;

    private List<ProgramDTO> programs;

}
