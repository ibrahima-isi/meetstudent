package com.bowe.meetstudent.utils;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.services.UserService;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class FakeUserGenerator {

    private final UserService userService;
    private final Faker faker = new Faker(new Locale("en-GB"));
    private final Random random = new Random();

    public UserEntity fakerUser(){
        UserEntity userEntity = new UserEntity();
        userEntity.setFirstname(faker.name().firstName());
        userEntity.setLastname(faker.name().lastName());
        userEntity.setEmail(faker.internet().emailAddress());
        userEntity.setBirthday(faker.date().birthday());
        userEntity.setPassword("Passer123");
        userEntity.setSpeciality(faker.job().field());
        userEntity.setRole(getRandomRole());

        return userEntity;
    }

    public void generateFakeUsers(int count){
        for(int i = 0; i < count; i++){
            UserEntity userEntity = fakerUser();
            userService.saveUser(userEntity);
        }
    }

    public Role getRandomRole(){
        Role[] roles = Role.values();
        return roles[random.nextInt(roles.length)];
    }



}
