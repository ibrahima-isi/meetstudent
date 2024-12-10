package com.bowe.meetstudent.dto;

import com.bowe.meetstudent.utils.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {

    private Integer id;
    private String firstname;
    private String lastname;
    private String email;
    private Date birthday;
    private String password;
    private Role role;
    private String speciality;

}
