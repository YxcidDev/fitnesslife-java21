package com.fitnesslife.gym.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fitnesslife.gym.enums.BloodType;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.enums.Sex;
import com.fitnesslife.gym.validation.OnCreate;
import com.fitnesslife.gym.validation.OnProfileUpdate;
import com.fitnesslife.gym.validation.OnUpdate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Document(collection = "users")
@CompoundIndexes({
        @CompoundIndex(name = "role_active_idx", def = "{'role': 1, 'isActive': 1}"),
        @CompoundIndex(name = "role_created_idx", def = "{'role': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "active_created_idx", def = "{'isActive': 1, 'createdAt': -1}")
})
public class User implements Serializable {

    @Id
    private String id;

    @NotNull(message = "La identificación es obligatoria", groups = { OnCreate.class, OnUpdate.class })
    @Indexed(unique = true)
    private Long identification;

    @NotBlank(message = "El nombre es obligatorio", groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    @Size(min = 2, max = 30, message = "El nombre debe tener entre 2 y 30 caracteres",
            groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El nombre solo admite letras y espacios",
            groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    @Indexed
    private String name;

    @NotBlank(message = "El apellido es obligatorio", groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    @Size(min = 2, max = 30, message = "El apellido debe tener entre 2 y 30 caracteres",
            groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El apellido solo admite letras y espacios",
            groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    @Indexed
    private String lastname;

    private Sex sex;

    @Past(message = "La fecha de nacimiento no puede ser futura",
            groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    private LocalDate birthDate;

    private BloodType bloodType;

    @NotBlank(message = "El correo es obligatorio", groups = { OnCreate.class, OnUpdate.class })
    @Email(message = "El correo debe tener un formato válido", groups = { OnCreate.class, OnUpdate.class })
    @Indexed(unique = true)
    private String email;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "El teléfono debe tener exactamente 10 dígitos",
            groups = { OnCreate.class, OnUpdate.class, OnProfileUpdate.class })
    private String phone;

    @NotBlank(message = "La contraseña es obligatoria", groups = OnCreate.class)
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres", groups = OnCreate.class)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe incluir letras y números",
            groups = OnCreate.class)
    @JsonIgnore
    private String password;

    private String photoProfile;

    @Indexed
    private Role role;

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]{2,30}$",
            message = "El plan debe tener entre 2 y 30 caracteres y solo letras", groups = OnUpdate.class)
    private String plan;

    private boolean isActive;
    private String qrCodePath;

    @Indexed
    private String qrToken;

    @CreatedDate
    @Indexed
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime lastLogin;

    public User() {
    }

    public User(String id, Long identification, String name, String lastname, Sex sex, LocalDate birthDate,
            BloodType bloodType, String email, String phone, String password, String photoProfile,
            Role role, String plan, boolean isActive, String qrCodePath, LocalDateTime lastLogin) {

        this.id = id;
        this.identification = identification;
        this.name = name;
        this.lastname = lastname;
        this.sex = sex;
        this.birthDate = birthDate;
        this.bloodType = bloodType;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.photoProfile = photoProfile;
        this.role = role;
        this.plan = plan;
        this.isActive = isActive;
        this.qrCodePath = qrCodePath;
        this.lastLogin = lastLogin;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getIdentification() {
        return identification;
    }

    public void setIdentification(Long identification) {
        this.identification = identification;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public void setBloodType(BloodType bloodType) {
        this.bloodType = bloodType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhotoProfile() {
        return photoProfile;
    }

    public void setPhotoProfile(String photoProfile) {
        this.photoProfile = photoProfile;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public String getQrCodePath() {
        return qrCodePath;
    }

    public void setQrCodePath(String qrCodePath) {
        this.qrCodePath = qrCodePath;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
}