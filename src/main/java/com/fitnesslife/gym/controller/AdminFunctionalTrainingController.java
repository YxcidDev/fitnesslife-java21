package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.enums.Role;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.service.FunctionalTrainingService;
import com.fitnesslife.gym.service.FunctionalTrainingService.FilteredTrainingsResult;
import com.fitnesslife.gym.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@RequiredArgsConstructor
@Controller
public class AdminFunctionalTrainingController {

    private final FunctionalTrainingService trainingService;
    private final UserService userService;

    private static final String VIEW_FUNCTIONALTRAINING = "admin/functionalTrainingCrud";

    @GetMapping("/admin/functionalTraining")
    public String functionalTraining(
            @RequestParam(required = false, defaultValue = "hoy") String filtro,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        FilteredTrainingsResult result = trainingService.getFilteredTrainings(filtro, page, size);
        Page<FunctionalTraining> pagedTrainings = result.trainings();
        List<User> trainers = userService.getUsersByRole(Role.TRAINER).stream()
                .filter(User::isActive)
                .toList();

        model.addAttribute("currentPage", "clases");
        model.addAttribute("clases", result.todayTrainings());
        model.addAttribute("trainings", pagedTrainings.getContent());
        model.addAttribute("training", new FunctionalTraining());
        model.addAttribute("filtro", filtro);
        model.addAttribute("page", page);
        model.addAttribute("totalPages", pagedTrainings.getTotalPages());
        model.addAttribute("total", pagedTrainings.getTotalElements());
        model.addAttribute("trainers", trainers);

        return VIEW_FUNCTIONALTRAINING;
    }
}