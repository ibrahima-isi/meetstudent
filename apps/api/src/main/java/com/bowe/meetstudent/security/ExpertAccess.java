package com.bowe.meetstudent.security;

import com.bowe.meetstudent.entities.enums.ExpertStatus;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Single place for the rule "this caller is an approved Expert" (rating programs and courses, and later the forum).
 * The status is read from the database on every call, never from the JWT: an approval or a rejection then applies
 * to the very next request without the expert signing in again, and a revoked right cannot outlive its token.
 * One indexed primary-key lookup per guarded request, the same cost class as the credential-version check of ADR 0001.
 */
@Component("expertAccess")
@RequiredArgsConstructor
public class ExpertAccess {

    private final UserRepository userRepository;

    public boolean isApproved(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || principal.getId() == null) {
            return false;
        }
        return userRepository.findExpertStatusById(principal.getId())
                .filter(ExpertStatus.APPROVED::equals)
                .isPresent();
    }
}
