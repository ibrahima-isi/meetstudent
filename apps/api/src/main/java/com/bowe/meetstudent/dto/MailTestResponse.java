package com.bowe.meetstudent.dto;

/** Answer of the SMTP self-test: {@code SENT} or the name of the failed outcome. Never carries an address or a detail. */
public record MailTestResponse(String status) {
}
