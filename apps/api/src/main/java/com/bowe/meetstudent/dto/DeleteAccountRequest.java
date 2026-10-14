package com.bowe.meetstudent.dto;

/** Current-password confirmation for self-deletion; administrators deleting another account omit it. */
public record DeleteAccountRequest(String currentPassword) {}
