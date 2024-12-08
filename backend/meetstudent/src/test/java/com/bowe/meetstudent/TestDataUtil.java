package com.bowe.meetstudent;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.utils.Role;
import com.github.javafaker.Faker;

import java.util.Random;

public class TestDataUtil {

    private static final Faker faker = new Faker();
    private static final Random random = new Random();

    public static Role getRandomRole(){
        Role[] roles = Role.values();
        return roles[random.nextInt(roles.length)];
    }


    public static UserDTO createUserDto(){
        return UserDTO.builder()
                .id(1)
                .email(faker.internet().emailAddress())
                .role(getRandomRole())
                .lastname(faker.name().lastName())
                .firstname(faker.name().firstName())
                .password(faker.internet().password())
                .build();
    }
}
