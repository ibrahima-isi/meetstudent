package com.bowe.meetstudent.utils;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.services.UserService;
import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * A utility class for generating fake user data.
 */
@Component
@RequiredArgsConstructor
public class FakeUserGenerator {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final Faker faker = new Faker(new Locale("en-GB"));
    private final Random random = new Random();
    Date birthday = Date.from(faker.timeAndDate().birthday(18, 75)
            .atStartOfDay(ZoneId.systemDefault()).toInstant()
    );

    /**
     * Generates a fake user entity with random data.
     *
     * @return a {@link UserEntity} instance with fake data
     */
    public UserEntity fakerUser() {
        UserEntity userEntity = new UserEntity();
        userEntity.setFirstname(faker.name().firstName());
        userEntity.setLastname(faker.name().lastName());
        userEntity.setEmail(faker.internet().emailAddress());
        userEntity.setBirthday(birthday);
        userEntity.setPassword(passwordEncoder.encode("Passer123"));
        userEntity.setSpeciality(faker.job().field());
        userEntity.setRole(getRandomRole());

        return userEntity;
    }

    /**
     * Generates a specified number of fake users and saves them using the {@link UserService}.
     *
     * @param count the number of fake users to generate
     */
    public void generateFakeUsers(int count) {
        for (int i = 0; i < count; i++) {
            UserEntity userEntity = fakerUser();
            userService.saveUser(userEntity, passwordEncoder);
        }
    }

    /**
     * Returns a random role from the available roles.
     *
     * @return a randomly selected {@link Role}
     */
    public Role getRandomRole() {
        Role[] roles = Role.values();
        return roles[random.nextInt(roles.length)];
    }
}
