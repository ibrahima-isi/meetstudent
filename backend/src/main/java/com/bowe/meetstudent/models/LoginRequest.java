package com.bowe.meetstudent.models;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class LoginRequest {

    private String username;
    private String password;
}
