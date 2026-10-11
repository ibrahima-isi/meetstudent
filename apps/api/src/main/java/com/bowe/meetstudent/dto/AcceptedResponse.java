package com.bowe.meetstudent.dto;

/** Body of a 202 answer: the request was taken into account. */
public record AcceptedResponse(String status) {
    public static AcceptedResponse accepted() {
        return new AcceptedResponse("accepted");
    }
}
