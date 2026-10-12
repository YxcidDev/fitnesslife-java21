package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.service.AuthService;
import com.fitnesslife.gym.validation.OnCreate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequiredArgsConstructor
@Controller
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public String register(
            @Validated(OnCreate.class) @ModelAttribute User user,
            @RequestParam(name = "acceptedTerms", required = false, defaultValue = "false") boolean acceptedTerms) {

        if (!acceptedTerms) {
            throw new InvalidRequestException(
                    "Debes aceptar los Términos y Condiciones y la Política de Tratamiento de Datos para registrarte");
        }

        authService.register(user);
        return "redirect:/login?registered";
    }
}