package com.bowe.meetstudent.security;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mail.EmailSender;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The email rule of {@link RequiresForumWriter}: while mail is enabled ({@link EmailSender#isEnabled()}, the value
 * {@code GET /api/v1/auth/capabilities} reports as {@code emailEnabled}), only an account with a verified email may
 * write, so throwaway addresses cannot spam; while it is disabled nobody could verify, and nothing changes. Read from
 * the database on every call, like {@link ExpertAccess}, so a verification applies to the next request.
 */
@Component("forumWriterAccess")
@RequiredArgsConstructor
public class ForumWriterAccess {

    private final EmailSender emailSender;
    private final UserRepository userRepository;

    public boolean hasVerifiedEmailIfRequired(Authentication authentication) {
        if (!emailSender.isEnabled()) {
            return true;
        }
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || principal.getId() == null) {
            return false;
        }
        return userRepository.findById(principal.getId()).map(UserEntity::isEmailVerified).orElse(false);
    }
}
