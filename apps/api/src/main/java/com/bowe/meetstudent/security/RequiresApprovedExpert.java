package com.bowe.meetstudent.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts an endpoint to Expert accounts approved by an admin (see {@link ExpertAccess}). */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('EXPERT') and @expertAccess.isApproved(authentication)")
public @interface RequiresApprovedExpert {
}
