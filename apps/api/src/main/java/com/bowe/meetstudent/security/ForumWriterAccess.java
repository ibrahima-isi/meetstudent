package com.bowe.meetstudent.security;

import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mail.MailProperties;
import com.bowe.meetstudent.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The email rule of {@link RequiresForumWriter}: while {@code MAIL_ENABLED=true} ({@code app.mail.enabled}, and only
 * that setting: the local dev-log sender does not turn it on), only an account with a verified email may write, so
 * throwaway addresses cannot spam; while it is false nobody could receive a link, and nothing changes. Read from the
 * database on every call, like {@link ExpertAccess}, so a verification applies to the next request.
 */
@Component("forumWriterAccess")
@RequiredArgsConstructor
public class ForumWriterAccess {

    private final MailProperties mailProperties;
    private final UserRepository userRepository;

    public boolean hasVerifiedEmailIfRequired(Authentication authentication) {
        if (!mailProperties.getMail().isEnabled()) {
            return true;
        }
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || principal.getId() == null) {
            return false;
        }
        return userRepository.findById(principal.getId()).map(UserEntity::isEmailVerified).orElse(false);
    }
}
