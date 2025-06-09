package com.bowe.meetstudent.controllers.auth;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.dto.auth.LoginDTO;
import com.bowe.meetstudent.entities.UserEntity;
import com.bowe.meetstudent.services.UserService;
import com.bowe.meetstudent.services.auth.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping(path = "/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final PasswordEncoder encoder;

    /**
     * Display the registration form
     */
    @GetMapping("/register")
    public String register(Model model) {
        UserDTO userDTO = new UserDTO();
        model.addAttribute(userDTO);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(Model model, @Valid @ModelAttribute UserDTO userDTO, BindingResult result) {
        if (!userDTO.getPassword().equals(userDTO.getConfirmedPassword())) {
            result.addError(
                    new FieldError("userDTO", "confirmedPassword", "Mot de passe different")
            );
        }

        if (userService.emailNotExists(userDTO.getEmail())) {
            result.addError(
                    new FieldError(
                            "userDTO",
                            "email",
                            "Cet email existe deja !"
                    )
            );
        }

        if (result.hasErrors()) {
            return "auth/register";
        }

        try {
            UserEntity userEntity = new UserEntity();
            userEntity.setFirstname(userDTO.getFirstname());
            userEntity.setLastname(userDTO.getLastname());
            userEntity.setEmail(userDTO.getEmail());
            userEntity.setPassword(userDTO.getPassword());
            userEntity.setBirthday(userDTO.getBirthday());

            if (userDTO.getRole() != null) {
                userEntity.setRole(userDTO.getRole());
            }
            if (!userDTO.getSpeciality().isEmpty()) {
                userEntity.setSpeciality(userDTO.getSpeciality());
            }
            var saved = userService.saveUser(userEntity);
            model.addAttribute("success", true);

        } catch (Exception exception) {
            result.addError(
                    new ObjectError("userDTO", exception.getMessage())
            );
            return "auth/register";
        }
        return "index";
    }

    @GetMapping("/login")
    public String login(Model model) {
        LoginDTO dto = new LoginDTO();
        model.addAttribute("dto", dto);
        return "auth/login";
    }

    @PostMapping("/process-login")
    public String login(Model model, @Valid @ModelAttribute("dto") LoginDTO dto, BindingResult result, RedirectAttributes redirectAttributes) {
        if (dto.getEmail().isEmpty() || dto.getPassword().isEmpty()) {
            result.addError(
                    new FieldError("dto", "email", "Vous devez saisir votre mail et votre mot de passe")
            );
        }

        if (result.hasErrors()) {
            return "auth/login";
        }

        try {
            UserDetails userDetails = authenticationService.loadUserByUsername(dto.getEmail());
            if (userDetails == null || !encoder.matches(dto.getPassword(), userDetails.getPassword())) {
                result.addError(new ObjectError("dto", "Controller error: Email ou mot de passe incorrect !"));
                return "auth/login";
            }
            // Set authentication in SecurityContext
            Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Add flash attribute for success message
            redirectAttributes.addFlashAttribute("loginSuccess", "Connexion réussie!");

        } catch (UsernameNotFoundException e) {
            result.addError(new ObjectError("dto", "Email ou mot de passe incorrect !"));
            return "auth/login";
        } catch (Exception e) {
            result.addError(new ObjectError("dto", "Erreur Inconnu: " + e.getMessage()));
            return "auth/login";
        }

        model.addAttribute("success", true);
        return "redirect:/"; // Redirect to the home page or another page
    }

}
