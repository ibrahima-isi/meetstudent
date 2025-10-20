package com.bowe.meetstudent.security;

import com.bowe.meetstudent.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userService.getUserByEmail(username).orElseThrow();

        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getEmail())
                .authorities(List.of(
                        new SimpleGrantedAuthority(
                            user.getRoles().getName()
                        )
                    )
                )
                .password(user.getPassword())
                .build();
    }
}
