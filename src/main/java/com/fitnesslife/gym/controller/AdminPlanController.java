package com.fitnesslife.gym.controller;

import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.fitnesslife.gym.dto.PlanResponseDTO;
import com.fitnesslife.gym.exception.DomainException;
import com.fitnesslife.gym.mapper.PlanMapper;
import com.fitnesslife.gym.model.Plan;
import com.fitnesslife.gym.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final PlanService planService;
    private final PlanMapper planMapper;

    @GetMapping
    public String showPlansManagement(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        log.info("Cargando gestión de planes - Página: {}", page);

        Pageable pageable = PageRequest.of(page, 10, Sort.by("price").ascending());
        Page<Plan> plansPage = planService.getAllPlansWithPagination(pageable);

        model.addAttribute("plans", plansPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", plansPage.getTotalPages());
        model.addAttribute("totalItems", plansPage.getTotalElements());
        model.addAttribute("newPlan", new Plan());

        log.info("Se cargaron {} planes de un total de {}",
                plansPage.getContent().size(), plansPage.getTotalElements());

        return "admin/plans";
    }

    @PostMapping("/create")
    public String createPlan(
            @Valid @ModelAttribute Plan plan,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        log.info("Creando nuevo plan: {}", plan.getPlanName());

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", summarizeErrors(bindingResult));
            return "redirect:/admin/plans";
        }

        planService.createPlan(plan);
        redirectAttributes.addFlashAttribute("success", "Plan creado exitosamente");
        return "redirect:/admin/plans";
    }

    @PostMapping("/update/{id}")
    public String updatePlan(
            @PathVariable String id,
            @Valid @ModelAttribute Plan plan,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        log.info("Actualizando plan con ID: {}", id);
        plan.setId(id);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", summarizeErrors(bindingResult));
            return "redirect:/admin/plans";
        }

        try {
            planService.updatePlan(plan);
            redirectAttributes.addFlashAttribute("success", "Plan actualizado exitosamente");
        } catch (DomainException e) {
            log.warn("No se pudo actualizar el plan {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/plans";
    }

    @PostMapping("/delete/{id}")
    public String deletePlan(@PathVariable String id, RedirectAttributes redirectAttributes) {
        log.info("Eliminando plan con ID: {}", id);

        try {
            planService.deletePlan(id);
            redirectAttributes.addFlashAttribute("success", "Plan eliminado exitosamente");
        } catch (DomainException e) {
            log.warn("No se pudo eliminar el plan {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/plans";
    }

    @GetMapping("/get/{id}")
    @ResponseBody
    public PlanResponseDTO getPlanById(@PathVariable String id) {
        log.info("Obteniendo plan con ID: {}", id);
        return planMapper.toResponseDTO(planService.getPlanById(id));
    }

    private String summarizeErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(". "));
    }
}