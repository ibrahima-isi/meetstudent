package com.bowe.meetstudent.services.auth;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.dto.auth.LoginDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.mappers.implementations.UserMapper;
import com.bowe.meetstudent.repositories.auth.AuthenticationRepository;
import com.bowe.meetstudent.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

@Service
@RequiredArgsConstructor
public class AuthenticationService implements UserDetailsService {

    private final AuthenticationRepository authenticationRepository;
    private final PasswordEncoder encoder;
    private final UserService userService;
    private final UserMapper userMapper;

    /**
     * @param email the email of the user to look for
     * @return UserEntity object
     * @throws UsernameNotFoundException if user not found
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity userEntity = authenticationRepository.findByEmail(email);
        if (userEntity == null) {
            throw new UsernameNotFoundException("User not found with email: " + email);
        }
        System.out.println(userEntity.getRole().toString());
        return User
                .withUsername(userEntity.getEmail())
                .password(
                        userEntity.getPassword()
                )
                .roles(
                        String.valueOf(userEntity.getRole())
                )
                .build();
//            return new User(userEntity.getEmail(), userEntity.getPassword(), Collections.singleton(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole())));
    }

    public void register(UserDTO userDTO, PasswordEncoder encoder, BindingResult result) {
        // Verifie si mot de passe confirmer:
        if (!userService.isPasswordConfirmed(userDTO.getPassword(), userDTO.getConfirmedPassword())) {
            result.addError(
                    new FieldError("userDTO", "confirmedPassword", "Mot de passe different")
            );
        }

        // verifie si mail existe:
        if (!userService.emailNotExists(userDTO.getEmail())) {
            result.addError(
                    new FieldError(
                            "userDTO",
                            "email",
                            "Cet email existe deja !"
                    )
            );
            return;
        }
        try {
            UserEntity userEntity = userMapper.toEntity(userDTO);
            userService.saveUser(userEntity, encoder);
            return;
        } catch (Exception exception) {
            result.addError(
                    new ObjectError("userDTO", exception.getMessage())
            );
        }

    }

    public void login(LoginDTO login, BindingResult result){
        try {
            UserDetails userDetails = loadUserByUsername(login.getEmail());
            if (userDetails == null || !encoder.matches(login.getPassword(), userDetails.getPassword())) {
                result.addError(new ObjectError("dto", "Email ou mot de passe incorrect !"));
//                return "auth/login";
            }
            // Set authentication in SecurityContext
            assert userDetails != null;
            Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (UsernameNotFoundException e) {
            result.addError(new ObjectError("dto", "Email ou mot de passe incorrect !"));
        } catch (Exception e) {
            result.addError(new ObjectError("dto", "Erreur Inconnu: " + e.getMessage()));
        }
    }
}
