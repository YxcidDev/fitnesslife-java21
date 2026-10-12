package com.fitnesslife.gym.service;

import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.exception.EmailAlreadyExistsException;
import com.fitnesslife.gym.exception.IdentificationAlreadyExistsException;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(User user) {
        if (userService.findByEmail(user.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("El usuario ya existe");
        }
        if (user.getIdentification() != null
                && userService.getUserByIdentification(user.getIdentification()).isPresent()) {
            throw new IdentificationAlreadyExistsException("La identificación ya está registrada");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(Role.USER);
        user.setActive(true);
        user.setSex(null);
        user.setBirthDate(null);
        user.setBloodType(null);
        user.setPhotoProfile("");
        user.setPlan("");
        user.setQrCodePath("");
        user.setLastLogin(null);

        User saved = userService.save(user);
        log.info("Nuevo usuario registrado: {}", saved.getEmail());
        return saved;
    }

    @Transactional
    public User createUserByAdmin(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new InvalidRequestException("El email es requerido");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new InvalidRequestException("La contraseña es requerida");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }
        user.setActive(true);

        return userService.createUser(user);
    }
}