package com.bowe.meetstudent.security;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.utils.RoleConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Represent the details of the authenticated user and used by spring boot to check roles and permissions
 */
@Getter
@Builder
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Integer userId;
    private final String email;
    private final Collection<? extends GrantedAuthority> grantedAuthorities;
    @JsonIgnore
    private final String password;

    public UserPrincipal(UserEntity userEntity){
        this.userId = userEntity.getId();
        this.email = userEntity.getEmail();
        this.password = userEntity.getPassword();
        this.grantedAuthorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + userEntity.getRole().name())
        );
        System.out.println(this.grantedAuthorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return grantedAuthorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
