package com.taskos.security.service;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginAttemptLimiter limiter;

    public LoginRateLimitFilter(LoginAttemptLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI().substring(Math.min(contextPath.length(), request.getRequestURI().length()));
        return !HttpMethod.POST.matches(request.getMethod()) || !"/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (limiter.isBlocked(request.getRemoteAddr(), request.getParameter("email"))) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(LoginAttemptLimiter.WINDOW.toSeconds()));
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Try again later — TASK OS</title>"
                    + "<body><main><h1>Too many sign-in attempts</h1><p>Please wait 15 minutes before trying again.</p>"
                    + "<a href=\"/login\">Return to sign in</a></main></body></html>");
            return;
        }
        chain.doFilter(request, response);
    }
}
