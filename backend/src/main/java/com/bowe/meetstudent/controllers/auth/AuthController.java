package com.bowe.meetstudent.controllers.auth;

import com.bowe.meetstudent.models.LoginRequest;
import com.bowe.meetstudent.models.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/auth")
public class AuthController {

//    private final JwtIssuer jwtIssuer;
//    private final AuthenticationManager authenticationManager;

//    @PostMapping
//    public LoginResponse login(@RequestBody @Validated LoginRequest request){
//        var auth = authenticationManager.authenticate(
//                new UsernamePasswordAuthenticationToken(
//                        request.getEmail(),
//                        request.getPassword()
//                )
//        );
//        SecurityContextHolder.getContext().setAuthentication(auth);
//
//        var principal = (UserPrincipal) auth.getPrincipal();
//        var roles = principal.getAuthorities()
//                .stream()
//                .map(GrantedAuthority::getAuthority)
//                .toList();
//
//        var token = jwtIssuer.issue(
//                principal.getUserId(),
//                principal.getEmail(),
//                roles
//        );
//
//        return LoginResponse.builder()
//                .accessToken(token)
//                .build();
//    }
}
