package com.bowe.meetstudent;

import com.bowe.meetstudent.dto.SchoolDTO;
import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.entities.embedded.Address;
import com.bowe.meetstudent.entities.Role;
import net.datafaker.Faker;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.List;

public class TestDataUtil {

    private static final Faker faker = new Faker();
    private static final Random random = new Random();

    private static final List<Role> ROLES = List.of(
            Role.builder().id(1).name("ADMIN").build(),
            Role.builder().id(2).name("USER").build(),
            Role.builder().id(3).name("EXPERT").build(),
            Role.builder().id(4).name("STUDENT").build()
    );

    public static Role getRandomRole(){
        return ROLES.get(random.nextInt(ROLES.size()));
    }

    public static UserDTO createUserDto(){
        return UserDTO.builder()
                .email(faker.internet().emailAddress())
                .role(getRandomRole())
                .lastname(faker.name().lastName())
                .firstname(faker.name().firstName())
                .password(faker.regexify("[a-zA-Z0-9]{8,16}"))
                .birthday(Date.from(faker.timeAndDate().birthday().atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .qualification(faker.job().field())
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
