package com.bowe.meetstudent.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Represents an authentication token for a user principal.
 * This class is used to encapsulate the authentication information
 * and pass it through the Spring Security framework.
 */
public class UserPrincipalAuthenticationToken extends AbstractAuthenticationToken {

    private final UserPrincipal principal;

    public UserPrincipalAuthenticationToken(UserPrincipal principal)
    {
        super(principal.getAuthorities());
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * Returns the credentials of the authenticated user.
     *
     * @return the password of the authenticated user
     */
    @Override
    public Object getCredentials() {
        return principal.getPassword();
    }

    /**
     * Returns the principal of the authenticated user.
     *
     * @return the authenticated user principal
     */
    @Override
    public UserPrincipal getPrincipal() {
        return principal;
    }
}