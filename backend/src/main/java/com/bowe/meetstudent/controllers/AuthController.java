package com.bowe.meetstudent.controllers;

import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.models.LoginResponse;
import com.bowe.meetstudent.security.JwtIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final JwtIssuer jwtIssuer;

    @PostMapping
    public LoginResponse login(@RequestBody @Validated LoginRequest request){
        var token = jwtIssuer.issueToken(1L, request.getUsername(), List.of("USER, EXPERT"));
        return  LoginResponse.builder()
                .accessToken(token)
                .build();
    }
}
