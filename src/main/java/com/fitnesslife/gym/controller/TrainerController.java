package com.fitnesslife.gym.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.fitnesslife.gym.dto.TrainerClassDTO;
import com.fitnesslife.gym.enums.TrainerClassFilter;
import com.fitnesslife.gym.service.TrainerClassService;
import com.fitnesslife.gym.service.TrainerClassService.TrainerClassesResult;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
@RequestMapping("/trainer")
public class TrainerController {

    private static final String VIEW_ASSIGNED_CLASSES = "trainer/trainer-profile";

    private final TrainerClassService trainerClassService;

    @GetMapping("/classes")
    public String assignedClasses(
            @RequestParam(defaultValue = "today") String filter,
            @RequestParam(defaultValue = "0") int page,
            Principal principal,
            Model model) {

        TrainerClassFilter selectedFilter = TrainerClassFilter.fromParam(filter);

        TrainerClassesResult result = trainerClassService
                .getAssignedClasses(principal.getName(), selectedFilter, Math.max(page, 0));
        Page<TrainerClassDTO> classesPage = result.classes();

        model.addAttribute("classes", classesPage.getContent());
        model.addAttribute("pageNumber", classesPage.getNumber());
        model.addAttribute("totalPages", classesPage.getTotalPages());
        model.addAttribute("totalItems", classesPage.getTotalElements());
        model.addAttribute("filter", selectedFilter.name().toLowerCase(Locale.ROOT));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("classesToday", result.classesToday());
        model.addAttribute("nextClass", result.nextClass());

        return VIEW_ASSIGNED_CLASSES;
    }
}