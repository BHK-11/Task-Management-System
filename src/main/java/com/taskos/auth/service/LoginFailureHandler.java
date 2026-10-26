package com.taskos.auth.service;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.taskos.auth.entity.AuthEventType;
import com.taskos.security.service.LoginAttemptLimiter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {
    private final AuthAuditService auditService;
    private final LoginAttemptLimiter attemptLimiter;

    public LoginFailureHandler(AuthAuditService auditService, LoginAttemptLimiter attemptLimiter) {
        this.auditService = auditService;
        this.attemptLimiter = attemptLimiter;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        // Deliberately omit submitted email and exception details from the persistent audit record.
        auditService.record(AuthEventType.LOGIN_FAILED, null);
        attemptLimiter.recordFailure(request.getRemoteAddr(), request.getParameter("email"));
        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}
