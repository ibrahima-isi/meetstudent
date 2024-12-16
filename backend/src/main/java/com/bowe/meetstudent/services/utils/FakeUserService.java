package com.bowe.meetstudent.services.utils;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.utils.Role;
import com.github.javafaker.Faker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class FakeUserService {

    private final Faker faker = new Faker(new Locale("en-GB"));
    private final Random random = new Random();

    public UserEntity fakerUser(){
        UserEntity userEntity = new UserEntity();
        userEntity.setFirstname(faker.name().firstName());
        userEntity.setLastname(faker.name().lastName());
        userEntity.setEmail(faker.internet().emailAddress());
        userEntity.setBirthday(faker.date().birthday());
        userEntity.setPassword(faker.internet().password());
        userEntity.setSpeciality(faker.job().field());
        userEntity.setRole(getRandomRole());

        return userEntity;
    }

    public List<UserEntity> generateFakeUsers(int count){

        List<UserEntity> generatedUsers = new ArrayList<>();
        for(int i = 0; i < count; i++){
            UserEntity userEntity = fakerUser();
            generatedUsers.add(userEntity);
        }
        return generatedUsers;
    }

    public Role getRandomRole(){
        Role[] roles = Role.values();
        return roles[random.nextInt(roles.length)];
    }



}
