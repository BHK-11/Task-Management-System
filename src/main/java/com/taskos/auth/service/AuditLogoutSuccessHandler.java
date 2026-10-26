package com.taskos.auth.service;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.taskos.auth.entity.AuthEventType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuditLogoutSuccessHandler implements org.springframework.security.web.authentication.logout.LogoutSuccessHandler {
    private final AuthAuditService auditService;

    public AuditLogoutSuccessHandler(AuthAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        UUID userId = authentication != null && authentication.getPrincipal() instanceof TaskOsUserDetails user
                ? user.getId() : null;
        if (userId != null) auditService.record(AuthEventType.LOGOUT, userId);
        response.sendRedirect(request.getContextPath() + "/login?logout");
    }
}
