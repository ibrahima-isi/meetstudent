package com.bowe.meetstudent.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts an endpoint to the accounts allowed to write on the forum: students, and experts approved by an admin
 * (status read live, see {@link ExpertAccess}), with a verified email while mail is enabled (see
 * {@link ForumWriterAccess}). A pending or rejected expert, an admin, or an unverified account while mail is enabled,
 * gets 403.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("(hasRole('STUDENT') or (hasRole('EXPERT') and @expertAccess.isApproved(authentication)))"
        + " and @forumWriterAccess.hasVerifiedEmailIfRequired(authentication)")
public @interface RequiresForumWriter {
}
