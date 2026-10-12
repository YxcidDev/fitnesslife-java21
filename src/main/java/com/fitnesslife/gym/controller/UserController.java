package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.exception.InvalidProfileImageException;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.service.UserService;
import com.fitnesslife.gym.validation.OnProfileUpdate;
import java.security.Principal;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RequiredArgsConstructor
@Slf4j
@Controller
public class UserController {

    private final UserService userService;

    @PostMapping("/actualizarUserPerfil")
    public String actualizarPerfil(
            @Validated(OnProfileUpdate.class) @ModelAttribute User userForm,
            BindingResult bindingResult,
            @RequestParam(name = "healthDataConsent", required = false, defaultValue = "false") boolean healthDataConsent,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            String summary = bindingResult.getFieldErrors().stream()
                    .map(fe -> fe.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining(". "));
            redirectAttributes.addFlashAttribute("error", summary);
            return "redirect:/user-profile";
        }

        try {
            userService.updateProfile(principal.getName(), userForm, healthDataConsent);
            redirectAttributes.addFlashAttribute("success", "Perfil actualizado correctamente");
        } catch (InvalidRequestException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/user-profile";
    }

    @PostMapping("/update-profile-picture")
    public String updateProfilePicture(@RequestParam("profileImage") MultipartFile profileImage,
            Principal principal) {
        try {
            userService.updateProfilePicture(principal.getName(), profileImage);
            return "redirect:/user-profile?success=uploaded";
        } catch (InvalidProfileImageException e) {
            log.warn("Foto de perfil rechazada para {}: {}", principal.getName(), e.getMessage());
            return "redirect:/user-profile?error=upload-failed";
        } catch (Exception e) {
            log.error("Error inesperado al actualizar foto de perfil para {}", principal.getName(), e);
            return "redirect:/user-profile?error=upload-failed";
        }
    }
}