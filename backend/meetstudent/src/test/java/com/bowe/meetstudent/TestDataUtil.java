package com.bowe.meetstudent;

import com.bowe.meetstudent.entities.Role;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.utils.UserType;

public class TestDataUtil {

    public static UserEntity createUser(){


        return UserEntity.builder()
                .id(1)
                .email("test@mail.com")
                .role(null)
                .lastname("diallo")
                .firstname("Ibrahima")
                .password("passer")
                .build();
    }
}
