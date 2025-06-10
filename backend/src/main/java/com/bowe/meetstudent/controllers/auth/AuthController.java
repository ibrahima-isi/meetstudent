package com.bowe.meetstudent.controllers.auth;

import com.bowe.meetstudent.dto.UserDTO;
import com.bowe.meetstudent.dto.auth.LoginDTO;
import com.bowe.meetstudent.services.auth.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
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

    /**
     * Handles user registration form submission.
     * Validates the input, registers the user, and returns the appropriate view.
     */
    @PostMapping("/register")
    public String register(Model model, @Valid @ModelAttribute UserDTO userDTO, BindingResult result) {

        authenticationService.register(userDTO, encoder, result);
        if (result.hasErrors()) {
            return "auth/register";
        }
        model.addAttribute("success", true);
        return "index";
    }

    /**
     * Displays the login form.
     * Adds an empty LoginDTO to the model for form binding.
     */
    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("dto", new LoginDTO());
        return "auth/login";
    }


    /**
     * Processes the login form submission.
     * Validates the input fields, attempts authentication, and handles errors.
     * On successful login, adds a flash attribute and redirects to the home page.
     *
     * @param model the model to add attributes to
     * @param dto the login data transfer object containing user credentials
     * @param result the binding result for validation errors
     * @param redirectAttributes attributes for a redirect scenario
     * @return the view name to render or redirect to
     */
    @PostMapping("/process-login")
    public String login(Model model, @Valid @ModelAttribute("dto") LoginDTO dto, BindingResult result, RedirectAttributes redirectAttributes) {
        if (dto.getEmail().isEmpty() || dto.getPassword().isEmpty()) {
            result.addError(
                    new FieldError("dto", "email", "Vous devez saisir votre mail et votre mot de passe")
            );
        }

        authenticationService.login(dto, result);
        if (result.hasErrors()) {
            return "auth/login";
        }
        // Add flash attribute for success message
        redirectAttributes.addFlashAttribute("loginSuccess", "Connexion réussie!");
        return "redirect:/"; // Redirect to the home page or another page
    }

}
