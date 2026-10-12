package com.fitnesslife.gym.controller;

import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.fitnesslife.gym.dto.UserResponseDTO;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.exception.DomainException;
import com.fitnesslife.gym.mapper.UserMapper;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.service.AuthService;
import com.fitnesslife.gym.service.UserService;
import com.fitnesslife.gym.validation.OnCreate;
import com.fitnesslife.gym.validation.OnUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Controller
public class AdminUserController {

    private final UserService userService;
    private final AuthService authService;
    private final UserMapper userMapper;

    private static final String VIEW_USER = "admin/userTable";

    @GetMapping("/admin/userTable")
    public String userTable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Model model) {

        Page<User> usersPage = userService.getUsersPaginated(page, size, role, status, search);

        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", usersPage.getTotalPages());
        model.addAttribute("totalItems", usersPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("roleFilter", role != null ? role : "");
        model.addAttribute("statusFilter", status != null ? status : "");
        model.addAttribute("searchTerm", search != null ? search : "");
        model.addAttribute("roles", Role.values());
        model.addAttribute("newUser", new User());

        return VIEW_USER;
    }

    @PostMapping("/admin/users/create")
    public String createUser(
            @Validated(OnCreate.class) @ModelAttribute User user,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String roleFilter,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String search,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", summarizeErrors(bindingResult));
            return buildRedirectUrl(page, roleFilter, statusFilter, search);
        }

        try {
            authService.createUserByAdmin(user);
            redirectAttributes.addFlashAttribute("success", "Usuario creado exitosamente");
        } catch (DomainException e) {
            log.warn("No se pudo crear el usuario: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return buildRedirectUrl(page, roleFilter, statusFilter, search);
    }

    @PostMapping("/admin/users/update/{id}")
    public String updateUser(
            @PathVariable String id,
            @Validated(OnUpdate.class) @ModelAttribute User user,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String roleFilter,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String search,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", summarizeErrors(bindingResult));
            return buildRedirectUrl(page, roleFilter, statusFilter, search);
        }

        try {
            userService.updateUser(id, user);
            redirectAttributes.addFlashAttribute("success", "Usuario actualizado exitosamente");
        } catch (DomainException e) {
            log.warn("No se pudo actualizar el usuario {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return buildRedirectUrl(page, roleFilter, statusFilter, search);
    }

    @PostMapping("/update-role")
    public String updateUserRole(
            @RequestParam String id,
            @RequestParam String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String roleFilter,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String search,
            RedirectAttributes redirectAttributes) {

        Role roleEnum;
        try {
            roleEnum = Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Rol inválido recibido: {}", role);
            redirectAttributes.addFlashAttribute("error", "Rol inválido");
            return buildRedirectUrl(page, roleFilter, statusFilter, search);
        }

        try {
            userService.updateUserRole(id, roleEnum);
            redirectAttributes.addFlashAttribute("success", "Rol actualizado correctamente");
        } catch (DomainException e) {
            log.warn("No se pudo actualizar el rol del usuario {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return buildRedirectUrl(page, roleFilter, statusFilter, search);
    }

    @PostMapping("/admin/users/delete/{id}")
    public String deleteUser(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String roleFilter,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String search,
            RedirectAttributes redirectAttributes,
            @AuthenticationPrincipal UserDetails currentUser) {
        try {
            User user = userService.getUserByIdOrThrow(id);

            if (user.getEmail().equals(currentUser.getUsername())) {
                redirectAttributes.addFlashAttribute("error", "No puedes eliminarte a ti mismo");
                return buildRedirectUrl(page, roleFilter, statusFilter, search);
            }

            userService.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Usuario eliminado exitosamente");
        } catch (DomainException e) {
            log.warn("No se pudo eliminar el usuario {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return buildRedirectUrl(page, roleFilter, statusFilter, search);
    }

    @GetMapping("/admin/users/get/{id}")
    @ResponseBody
    public UserResponseDTO getUserById(@PathVariable String id) {
        return userMapper.toResponseDTO(userService.getUserByIdOrThrow(id));
    }

    private String summarizeErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(". "));
    }

    private String buildRedirectUrl(int page, String roleFilter, String statusFilter, String search) {
        StringBuilder url = new StringBuilder("redirect:/admin/userTable");
        url.append("?page=").append(page);

        if (roleFilter != null && !roleFilter.trim().isEmpty()) {
            url.append("&role=").append(roleFilter);
        }
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            url.append("&status=").append(statusFilter);
        }
        if (search != null && !search.trim().isEmpty()) {
            url.append("&search=").append(search);
        }

        return url.toString();
    }
}