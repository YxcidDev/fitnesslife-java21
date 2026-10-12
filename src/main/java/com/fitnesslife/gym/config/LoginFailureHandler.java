package com.fitnesslife.gym.config;

import java.io.IOException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/login?locked=true");
            return;
        }

        String email = request.getParameter("email");
        loginAttemptService.recordFailure(email);
        response.sendRedirect(request.getContextPath() + "/login?error=true");
    }
}