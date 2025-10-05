package com.bowe.meetstudent;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.embedded.Address;
import com.bowe.meetstudent.utils.Role;
import net.datafaker.Faker;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
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
                .email(faker.internet().emailAddress())
                .role(getRandomRole())
                .lastname(faker.name().lastName())
                .firstname(faker.name().firstName())
                .password(faker.regexify("[a-zA-Z0-9]{8,16}"))
                .birthday(Date.from(faker.timeAndDate().birthday().atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .speciality(faker.job().field())
                .build();
    }

    public static SchoolDTO createSchoolDto(){
        Address address = getAddress();
        Calendar start = getStartDate();
        Calendar end = getEndDate();

        return SchoolDTO.builder()
                .name(faker.educator().university())
                .address(address)
                .code(faker.random().hex(5))
                .creation(Date.from(faker.timeAndDate().between(start.getTime().toInstant(), end.getTime().toInstant())))
                .build();
    }

    public static Address getAddress(){
        return Address.builder()
                .location(faker.address().streetAddress())
                .city(faker.address().city())
                .country(faker.address().country())
                .build();
    }

    public static Calendar getStartDate() {
        Calendar start = Calendar.getInstance();
        start.set(1990, Calendar.JANUARY, 1);
        return start;
    }

    public static Calendar getEndDate(){
        Calendar end = Calendar.getInstance();
        end.set(2015, Calendar.DECEMBER, 31);
        return end;
    }
}
