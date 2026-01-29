package com.fitnesslife.gym.service;

import com.fitnesslife.gym.enums.AccessResult;
import com.fitnesslife.gym.model.Access;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.AccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessService {

    private final AccessRepository accessRepository;
    private final UserService userService;

    @Transactional
    public Access createAccess(String userId, String qrCode, AccessResult result) {
        log.info("Creando acceso para usuario: {} con resultado: {}", userId, result);

        User user = userService.getUserByIdOrThrow(userId);

        Access access = Access.builder()
                .user(user)
                .qrCode(qrCode)
                .result(result)
                .accessedAt(LocalDateTime.now())
                .userName(user.getName() + " " + user.getLastname())
                .userEmail(user.getEmail())
                .build();

        Access saved = accessRepository.save(access);
        log.info("Acceso creado con ID: {}", saved.getId());
        return saved;
    }

    public List<Access> getUserAccesses(String userId) {
        User user = userService.getUserByIdOrThrow(userId);
        return accessRepository.findByUserOrderByAccessedAtDesc(user);
    }

    public Access getAccessById(String id) {
        log.info("Buscando acceso con ID: {}", id);
        return accessRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Acceso no encontrado con ID: {}", id);
                    return new RuntimeException("Acceso no encontrado con ID: " + id);
                });
    }

    @Transactional
    public Access updateAccessResult(String id, AccessResult newResult) {
        log.info("Actualizando resultado del acceso {} a {}", id, newResult);

        Access access = getAccessById(id);
        AccessResult oldResult = access.getResult();

        access.setResult(newResult);

        Access updated = accessRepository.save(access);
        log.info("Resultado del acceso actualizado de {} a {}", oldResult, newResult);
        return updated;
    }

    @Transactional
    public void deleteAccess(String id) {
        log.info("Eliminando acceso con ID: {}", id);
        accessRepository.deleteById(id);
        log.info("Acceso eliminado exitosamente");
    }

    public Page<Access> getAccessesPaginated(int page, int size, AccessResult result, String search) {
        log.info("Obteniendo accesos paginados - Página: {}, Tamaño: {}, Resultado: {}, Búsqueda: {}",
                page, size, result, search);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "accessedAt"));

        Page<Access> accessesPage;

        if (search != null && !search.trim().isEmpty() && result != null) {
            log.info("Buscando accesos con resultado '{}' y término '{}'", result, search);
            accessesPage = accessRepository.searchAccessesByResult(result, search, pageable);
        } else if (search != null && !search.trim().isEmpty()) {
            log.info("Buscando accesos con término '{}'", search);
            accessesPage = accessRepository.searchAccesses(search, pageable);
        } else if (result != null) {
            log.info("Filtrando accesos por resultado '{}'", result);
            accessesPage = accessRepository.findByResult(result, pageable);
        } else {
            log.info("Obteniendo todos los accesos");
            accessesPage = accessRepository.findAll(pageable);
        }

        log.info("Se encontraron {} accesos de {} totales",
                accessesPage.getNumberOfElements(),
                accessesPage.getTotalElements());

        return accessesPage;
    }
}
