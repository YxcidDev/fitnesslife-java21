package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.enums.AccessResult;
import com.fitnesslife.gym.model.Access;
import com.fitnesslife.gym.service.AccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/accesses")
@RequiredArgsConstructor
@Slf4j
public class AdminAccessController {

    private final AccessService accessService;

    @GetMapping
    public String showAccessesManagement(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String search,
            Model model) {
        try {
            log.info("Cargando gestión de accesos - Página: {}, Tamaño: {}, Resultado: {}, Búsqueda: {}",
                    page, size, result, search);

            AccessResult accessResult = null;
            if (result != null && !result.trim().isEmpty()) {
                try {
                    accessResult = AccessResult.valueOf(result.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Resultado de acceso inválido recibido: {}", result);
                }
            }

            Page<Access> accessesPage = accessService.getAccessesPaginated(page, size, accessResult, search);

            model.addAttribute("accesses", accessesPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", accessesPage.getTotalPages());
            model.addAttribute("totalItems", accessesPage.getTotalElements());
            model.addAttribute("pageSize", size);
            model.addAttribute("resultFilter", result != null ? result : "");
            model.addAttribute("searchTerm", search != null ? search : "");
            log.info("Se cargaron {} accesos de {} totales",
                    accessesPage.getContent().size(),
                    accessesPage.getTotalElements());
            return "admin/accesses";
        } catch (Exception e) {
            log.error("Error al cargar accesos: {}", e.getMessage(), e);
            model.addAttribute("error", "Error al cargar los accesos");
            return "admin/accesses";
        }
    }

    @GetMapping("/get/{id}")
    @ResponseBody
    public Access getAccessById(@PathVariable String id) {
        log.info("Obteniendo acceso con ID: {}", id);
        return accessService.getAccessById(id);
    }

    @PostMapping("/update-result/{id}")
    public String updateAccessResult(
            @PathVariable String id,
            @RequestParam String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String resultFilter,
            @RequestParam(required = false) String search,
            RedirectAttributes redirectAttributes) {
        try {
            log.info("Actualizando resultado del acceso {} a {}", id, result);
            AccessResult accessResult;
            try {
                accessResult = AccessResult.valueOf(result.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.error("Resultado de acceso inválido: {}", result);
                redirectAttributes.addFlashAttribute("error", "Resultado de acceso inválido: " + result);
                return "redirect:/admin/accesses?page=" + page +
                        (resultFilter != null ? "&result=" + resultFilter : "") +
                        (search != null ? "&search=" + search : "");
            }
            accessService.updateAccessResult(id, accessResult);
            redirectAttributes.addFlashAttribute("success", "Resultado del acceso actualizado exitosamente");
            log.info("Resultado del acceso actualizado correctamente");
        } catch (Exception e) {
            log.error("Error al actualizar resultado del acceso: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el resultado: " + e.getMessage());
        }

        return "redirect:/admin/accesses?page=" + page +
                (resultFilter != null ? "&result=" + resultFilter : "") +
                (search != null ? "&search=" + search : "");
    }

    @PostMapping("/delete/{id}")
    public String deleteAccess(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String resultFilter,
            @RequestParam(required = false) String search,
            RedirectAttributes redirectAttributes) {
        try {
            log.info("Eliminando acceso con ID: {}", id);
            accessService.deleteAccess(id);
            redirectAttributes.addFlashAttribute("success", "Acceso eliminado exitosamente");
            log.info("Acceso eliminado con éxito");
        } catch (Exception e) {
            log.error("Error al eliminar acceso: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al eliminar el acceso: " + e.getMessage());
        }

        return "redirect:/admin/accesses?page=" + page +
                (resultFilter != null ? "&result=" + resultFilter : "") +
                (search != null ? "&search=" + search : "");
    }

    @PostMapping("/create")
    @ResponseBody
    public Access createAccess(
            @RequestParam String userId,
            @RequestParam String qrCode,
            @RequestParam String result) {
        log.info("Creando acceso para usuario: {}", userId);
        AccessResult accessResult;
        try {
            accessResult = AccessResult.valueOf(result.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Resultado de acceso inválido: {}", result);
            throw new IllegalArgumentException("Resultado de acceso inválido: " + result);
        }
        return accessService.createAccess(userId, qrCode, accessResult);
    }
}
