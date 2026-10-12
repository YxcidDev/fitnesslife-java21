package com.fitnesslife.gym.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.UserRepository;
import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.exception.InvalidProfileImageException;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fitnesslife.gym.exception.EmailAlreadyExistsException;
import com.fitnesslife.gym.exception.IdentificationAlreadyExistsException;
import java.util.Objects;
import java.util.Collection;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private static final String UPLOAD_DIRECTORY = "uploads/profiles";
    private static final long MAX_PROFILE_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_IMAGE_FORMATS = List.of("jpeg", "png", "gif");

    public Optional<User> findByEmail(String email) {
        log.debug("Buscando usuario con email: {}", email);
        return userRepository.findByEmail(email);
    }

    public User getUserOrThrow(String email) {
        return findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado: " + email));
    }

    public Optional<User> findById(String id) {
        log.debug("Buscando usuario con ID: {}", id);
        return userRepository.findById(id);
    }

    public User getUserByIdOrThrow(String id) {
        return findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con ID: " + id));
    }

    public Optional<User> getUserByIdentification(Long identification) {
        log.info("Buscando usuario por identificación: {}", identification);
        return userRepository.findByIdentification(identification);
    }

    public Optional<User> getUserByQrCode(String qrCodePath) {
        log.info("Buscando usuario por QR Code: {}", qrCodePath);
        return userRepository.findByQrCodePath(qrCodePath);
    }

    public Optional<User> getUserByQrToken(String qrToken) {
        log.debug("Buscando usuario por token QR");
        return userRepository.findByQrToken(qrToken);
    }

    public List<User> getUsersByIdentifications(Collection<Long> identifications) {
        log.debug("Buscando usuarios por lista de identificaciones");
        return userRepository.findByIdentificationIn(identifications);
    }

    @Transactional
    public User save(User user) {
        log.debug("Guardando usuario: {}", user.getEmail());
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getUsersByRole(Role role) {
        log.debug("Buscando usuarios con rol: {}", role);
        return userRepository.findByRole(role);
    }

    public Page<User> getUsersPaginated(int page, int size, String role, String status, String search) {
        log.info("Obteniendo usuarios paginados - Página: {}, Tamaño: {}, Rol: {}, Estado: {}, Búsqueda: {}",
                page, size, role, status, search);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Role roleEnum = null;
        if (role != null && !role.trim().isEmpty()) {
            try {
                roleEnum = Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Rol inválido recibido: {}. Se ignorará el filtro de rol.", role);
            }
        }

        Long identification = null;
        try {
            if (search != null && !search.trim().isEmpty()) {
                identification = Long.parseLong(search.trim());
            }
        } catch (NumberFormatException e) {
        }

        String safeSearch = SearchTermSanitizer.sanitize(search);

        Page<User> usersPage;
        boolean hasRole = roleEnum != null;
        boolean hasStatus = status != null && !status.trim().isEmpty();
        boolean hasSearch = safeSearch != null && !safeSearch.isEmpty();

        if (hasSearch && hasRole && hasStatus) {
            boolean isActive = "active".equals(status);
            usersPage = userRepository.searchUsersByRoleAndStatus(roleEnum, isActive, safeSearch, identification,
                    pageable);
        } else if (hasSearch && hasRole) {
            usersPage = userRepository.searchUsersByRole(roleEnum, safeSearch, identification, pageable);
        } else if (hasSearch && hasStatus) {
            boolean isActive = "active".equals(status);
            usersPage = userRepository.searchUsersByStatus(isActive, safeSearch, identification, pageable);
        } else if (hasSearch) {
            usersPage = userRepository.searchUsers(safeSearch, identification, pageable);
        } else if (hasRole && hasStatus) {
            boolean isActive = "active".equals(status);
            usersPage = userRepository.findByRoleAndIsActive(roleEnum, isActive, pageable);
        } else if (hasRole) {
            usersPage = userRepository.findByRole(roleEnum, pageable);
        } else if (hasStatus) {
            boolean isActive = "active".equals(status);
            usersPage = userRepository.findByIsActive(isActive, pageable);
        } else {
            usersPage = userRepository.findAll(pageable);
        }

        return usersPage;
    }

    @Transactional
    public User createUser(User user) {
        log.info("Creando nuevo usuario: {}", user.getEmail());

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("El email ya está registrado");
        }

        if (user.getIdentification() != null &&
                userRepository.findByIdentification(user.getIdentification()).isPresent()) {
            throw new IdentificationAlreadyExistsException("La identificación ya está registrada");
        }

        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        log.info("Usuario creado exitosamente con ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public User updateUser(String id, User updatedUser) {
        log.info("Actualizando usuario con ID: {}", id);

        User existingUser = getUserByIdOrThrow(id);

        if (!existingUser.getEmail().equals(updatedUser.getEmail())) {
            Optional<User> userWithEmail = userRepository.findByEmail(updatedUser.getEmail());
            if (userWithEmail.isPresent() && !userWithEmail.get().getId().equals(id)) {
                throw new EmailAlreadyExistsException("El email ya está en uso por otro usuario");
            }
        }

        if (updatedUser.getIdentification() != null &&
                !updatedUser.getIdentification().equals(existingUser.getIdentification())) {
            Optional<User> userWithId = userRepository.findByIdentification(updatedUser.getIdentification());
            if (userWithId.isPresent() && !userWithId.get().getId().equals(id)) {
                throw new IdentificationAlreadyExistsException("La identificación ya está en uso por otro usuario");
            }
        }

        existingUser.setName(updatedUser.getName());
        existingUser.setLastname(updatedUser.getLastname());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setIdentification(updatedUser.getIdentification());
        existingUser.setPhone(updatedUser.getPhone());
        existingUser.setSex(updatedUser.getSex());
        existingUser.setBirthDate(updatedUser.getBirthDate());
        existingUser.setBloodType(updatedUser.getBloodType());
        existingUser.setRole(updatedUser.getRole());
        existingUser.setPlan(updatedUser.getPlan());
        existingUser.setActive(updatedUser.isActive());
        existingUser.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(existingUser);
        log.info("Usuario actualizado exitosamente: {}", saved.getEmail());
        return saved;
    }

    @Transactional
    public void updateUserRole(String id, Role role) {
        User user = getUserByIdOrThrow(id);
        user.setRole(role);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional
    public void deleteById(String id) {
        log.debug("Eliminando usuario con ID: {}", id);
        User user = getUserByIdOrThrow(id);
        userRepository.delete(user);
        log.info("Usuario eliminado exitosamente: {}", user.getEmail());
    }

    public Optional<User> getUserById(String id) {
        return findById(id);
    }

    @Transactional
    public User updateProfile(String email, User form, boolean healthDataConsent) {
        User user = getUserOrThrow(email);

        boolean bloodTypeChanged = !Objects.equals(user.getBloodType(), form.getBloodType());
        if (bloodTypeChanged && form.getBloodType() != null && !healthDataConsent) {
            throw new InvalidRequestException(
                    "Debes autorizar el tratamiento de tu dato de salud para registrar el tipo de sangre");
        }

        user.setName(form.getName());
        user.setLastname(form.getLastname());
        user.setPhone(form.getPhone());
        user.setSex(form.getSex());
        user.setBirthDate(form.getBirthDate());
        user.setBloodType(form.getBloodType());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Transactional
    public void updateProfilePicture(String email, MultipartFile file) {
        User user = getUserOrThrow(email);
        Long idNumber = user.getIdentification();

        if (file == null || file.isEmpty()) {
            throw new InvalidProfileImageException("No se recibió ningún archivo");
        }
        if (idNumber == null) {
            throw new InvalidProfileImageException("El usuario no tiene identificación registrada");
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_BYTES) {
            throw new InvalidProfileImageException("La imagen supera el tamaño máximo permitido (5 MB)");
        }

        DecodedImage decoded = decodeAndNormalize(file);

        try {
            String userFolder = UPLOAD_DIRECTORY + "/" + idNumber;
            Path targetFolder = Paths.get(userFolder);

            if (!Files.exists(targetFolder)) {
                Files.createDirectories(targetFolder);
            }

            String fileName = UUID.randomUUID() + "." + decoded.extension();
            Path targetPath = targetFolder.resolve(fileName);

            Files.write(targetPath, decoded.bytes(), StandardOpenOption.CREATE_NEW);

            deleteOldProfilePicture(user, targetFolder);

            String relativeUrl = "/uploads/profiles/" + idNumber + "/" + fileName;
            user.setPhotoProfile(relativeUrl);
            user.setUpdatedAt(LocalDateTime.now());

            userRepository.save(user);
        } catch (IOException e) {
            log.error("Error guardando foto de perfil para {}: {}", email, e.getMessage(), e);
            throw new InvalidProfileImageException("No se pudo guardar la imagen. Intenta de nuevo.");
        }
    }

    private void deleteOldProfilePicture(User user, Path userFolder) {
        String previousUrl = user.getPhotoProfile();
        if (previousUrl == null || previousUrl.isBlank()) {
            return;
        }
        try {
            String previousFileName = previousUrl.substring(previousUrl.lastIndexOf('/') + 1);
            Files.deleteIfExists(userFolder.resolve(previousFileName));
        } catch (Exception e) {
            log.warn("No se pudo eliminar la foto de perfil anterior de {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private record DecodedImage(byte[] bytes, String extension) {
    }

    private DecodedImage decodeAndNormalize(MultipartFile file) {
        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidProfileImageException("No se pudo leer el archivo enviado");
        }

        BufferedImage image;
        String detectedFormat;

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            if (iis == null) {
                throw new InvalidProfileImageException(
                        "El archivo no es una imagen válida (formatos permitidos: JPG, PNG, GIF)");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidProfileImageException(
                        "El archivo no es una imagen válida (formatos permitidos: JPG, PNG, GIF)");
            }

            ImageReader reader = readers.next();
            String formatName = reader.getFormatName().toLowerCase();
            if (!ALLOWED_IMAGE_FORMATS.contains(formatName)) {
                throw new InvalidProfileImageException(
                        "Formato de imagen no permitido. Formatos aceptados: JPG, PNG, GIF");
            }

            reader.setInput(iis);
            image = reader.read(0);
            detectedFormat = formatName;
            reader.dispose();
        } catch (IOException e) {
            throw new InvalidProfileImageException("No se pudo leer el archivo como imagen");
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean written = ImageIO.write(image, detectedFormat, out);
            if (!written) {
                throw new InvalidProfileImageException("No se pudo procesar la imagen enviada");
            }
            String extension = "jpeg".equals(detectedFormat) ? "jpg" : detectedFormat;
            return new DecodedImage(out.toByteArray(), extension);
        } catch (IOException e) {
            throw new InvalidProfileImageException("No se pudo procesar la imagen enviada");
        }
    }
}