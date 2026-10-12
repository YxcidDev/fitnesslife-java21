package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.dto.FunctionalTrainingResponseDTO;
import com.fitnesslife.gym.exception.AlreadyEnrolledException;
import com.fitnesslife.gym.exception.DomainException;
import com.fitnesslife.gym.exception.FunctionalTrainingNotFoundException;
import com.fitnesslife.gym.exception.InvalidRequestException;
import com.fitnesslife.gym.exception.NoActivePlanException;
import com.fitnesslife.gym.mapper.FunctionalTrainingMapper;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.service.FunctionalTrainingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/trainings")
public class FunctionalTrainingController {

    private static final String REDIRECT_ADMIN_CLASSES = "redirect:/admin/functionalTraining";

    private final FunctionalTrainingService service;
    private final FunctionalTrainingMapper trainingMapper;

    @PostMapping("/save")
    public String saveTraining(
            @Valid @ModelAttribute FunctionalTraining training,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", summarizeErrors(bindingResult));
            return REDIRECT_ADMIN_CLASSES;
        }

        try {
            service.createTraining(training);
            redirectAttributes.addFlashAttribute("success", "Clase creada exitosamente");
        } catch (DomainException e) {
            log.warn("No se pudo crear la clase: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return REDIRECT_ADMIN_CLASSES;
    }

    @PostMapping("/delete/{id}")
    public String deleteTraining(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "hoy") String filtro,
            @RequestParam(defaultValue = "0") int page) {

        service.deleteTraining(id);

        return "redirect:/admin/functionalTraining?filtro=" + filtro + "&page=" + page;
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public FunctionalTrainingResponseDTO getTrainingById(@PathVariable String id) {
        return service.getTrainingById(id)
                .map(trainingMapper::toResponseDTO)
                .orElseThrow(() -> new FunctionalTrainingNotFoundException("Clase no encontrada: " + id));
    }

    @PostMapping("/update")
    public String updateTrainingPost(
            @RequestParam("id") String id,
            @RequestParam("nameTraining") String nameTraining,
            @RequestParam("instructorId") String instructorId,
            @RequestParam("room") String room,
            @RequestParam("date") String date,
            @RequestParam("time") String time,
            RedirectAttributes redirectAttributes) {

        try {
            if (nameTraining == null || nameTraining.isBlank()
                    || instructorId == null || instructorId.isBlank()
                    || room == null || room.isBlank()) {
                throw new InvalidRequestException("Nombre, instructor y sala son obligatorios");
            }

            FunctionalTraining existing = service.getTrainingById(id)
                    .orElseThrow(() -> new FunctionalTrainingNotFoundException("Clase no encontrada: " + id));

            existing.setNameTraining(nameTraining);
            existing.setInstructorId(instructorId);
            existing.setRoom(room);

            if (!date.isEmpty() && !time.isEmpty()) {
                String datetimeStr = date + " " + time;
                try {
                    java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                    existing.setDatetime(formatter.parse(datetimeStr));
                } catch (java.text.ParseException e) {
                    throw new InvalidRequestException("Fecha u hora inválida");
                }
            }

            service.updateTraining(existing.getId(), existing);
            redirectAttributes.addFlashAttribute("success", "Clase actualizada exitosamente");
        } catch (DomainException e) {
            log.warn("No se pudo actualizar la clase {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return REDIRECT_ADMIN_CLASSES;
    }

    @PostMapping("/inscribirme/{idFunctionalTraining}")
    public String inscribirme(@PathVariable int idFunctionalTraining, Principal principal) {
        try {
            service.inscribirUsuario(idFunctionalTraining, principal.getName());
            return "redirect:/home?success=inscrito";
        } catch (NoActivePlanException e) {
            return "redirect:/home?error=sin-plan";
        } catch (AlreadyEnrolledException e) {
            return "redirect:/home?error=ya-inscrito";
        }
    }

    @PostMapping("/cancelarInscripcion/{id}")
    public String cancelarInscripcion(@PathVariable int id, Principal principal) {
        service.cancelarInscripcion(id, principal.getName());
        return "redirect:/home?success=Cancelada";
    }

    private String summarizeErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(error -> error.isBindingFailure()
                        ? "El valor del campo '" + error.getField() + "' no es válido"
                        : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(". "));
    }
}