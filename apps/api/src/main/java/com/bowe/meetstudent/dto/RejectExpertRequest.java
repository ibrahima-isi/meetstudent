package com.bowe.meetstudent.dto;

/** Body of a rejection. Deliberately minimal: the decision itself never travels through UserDTO. */
public record RejectExpertRequest(String reason) {
}
