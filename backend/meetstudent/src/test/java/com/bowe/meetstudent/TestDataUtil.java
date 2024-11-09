package com.bowe.meetstudent;

import com.bowe.meetstudent.entities.User;
import com.bowe.meetstudent.utils.UserType;

public class TestDataUtil {

    public static User createUser(){


        return User.builder()
                .id(1)
                .email("test@mail.com")
                .userType(UserType.ADMIN)
                .lastname("diallo")
                .firstname("Ibrahima")
                .password("passer")
                .build();
    }
}
