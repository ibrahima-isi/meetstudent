package com.bowe.meetstudent.services.auth;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.repositories.UserRepository;
import com.bowe.meetstudent.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Service class for loading user details by email.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads the user details by email.
     *
     * @param email the email of the user
     * @return the user principal containing user details
     * @throws UsernameNotFoundException if the user is not found
     */
    @Override
    public UserPrincipal loadUserByUsername(String email) throws UsernameNotFoundException {

        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new UserPrincipal(user);
    }
}