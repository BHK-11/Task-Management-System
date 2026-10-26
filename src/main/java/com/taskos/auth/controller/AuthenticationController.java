package com.taskos.auth.controller;

import java.util.Objects;

import com.taskos.auth.dto.SignupForm;
import com.taskos.auth.service.RegistrationService;
import com.taskos.auth.service.RegistrationService.DuplicateEmailException;
import com.taskos.auth.service.TaskOsUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthenticationController {
    private final RegistrationService registrationService;

    public AuthenticationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        return isSignedIn(authentication) ? "redirect:/app" : "auth/login";
    }

    @GetMapping("/signup")
    public String signup(Authentication authentication, Model model) {
        if (isSignedIn(authentication)) return "redirect:/app";
        model.addAttribute("signupForm", new SignupForm());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String register(@Valid @ModelAttribute("signupForm") SignupForm form,
            BindingResult bindingResult) {
        if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "The passwords do not match.");
        }
        if (bindingResult.hasErrors()) return "auth/signup";

        try {
            registrationService.register(form.getDisplayName(), form.getEmail(), form.getPassword());
        } catch (DuplicateEmailException ex) {
            bindingResult.rejectValue("email", "email.duplicate", "An account with this email already exists.");
            return "auth/signup";
        }
        return "redirect:/login?registered";
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof TaskOsUserDetails;
    }
}
