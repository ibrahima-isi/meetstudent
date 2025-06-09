package com.bowe.meetstudent.services.auth;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.auth.AuthenticationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthenticationService implements UserDetailsService {

    private final AuthenticationRepository authenticationRepository;
    private final PasswordEncoder passwordEncoder;


    /**
     * @param email the email of the user to look for
     * @return UserEntity object
     * @throws UsernameNotFoundException if user not found
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity userEntity = authenticationRepository.findByEmail(email);
        if(userEntity == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
        System.out.println(userEntity.getRole().toString());
            return User
                    .withUsername(userEntity.getEmail())
                    .password(
                            userEntity.getPassword()
                    )
                    .roles(
                            String.valueOf(userEntity.getRole())
                    )
                    .build();
//            return new User(userEntity.getEmail(), userEntity.getPassword(), Collections.singleton(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole())));
    }

}
