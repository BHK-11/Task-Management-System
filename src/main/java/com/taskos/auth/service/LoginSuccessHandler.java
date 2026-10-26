package com.taskos.auth.service;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.taskos.user.service.UserProfileService;
import com.taskos.security.service.LoginAttemptLimiter;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final UserProfileService profiles;
    private final LoginAttemptLimiter attemptLimiter;

    public LoginSuccessHandler(UserProfileService profiles, LoginAttemptLimiter attemptLimiter) {
        this.profiles = profiles;
        this.attemptLimiter = attemptLimiter;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof TaskOsUserDetails user) {
            attemptLimiter.recordSuccess(request.getRemoteAddr(), user.getEmail());
            profiles.recordSuccessfulLogin(user.getId());
        }
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }
}
