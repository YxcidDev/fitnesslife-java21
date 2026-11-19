package com.fitnesslife.gym.controller;

import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import com.fitnesslife.gym.dto.RetentionFormDefaultsDTO;
import com.fitnesslife.gym.dto.RetentionOptimizationRequestDTO;
import com.fitnesslife.gym.dto.RetentionPlanResultDTO;
import com.fitnesslife.gym.service.RetentionOptimizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RetentionOptimizationController {

    private final RetentionOptimizationService retentionOptimizationService;

    @GetMapping("/admin/retention")
    public String retentionView(Model model) {
        model.addAttribute("currentPageNav", "churn");
        return "admin/retention-optimization";
    }

    @GetMapping("/api/retention/defaults")
    @ResponseBody
    public ResponseEntity<RetentionFormDefaultsDTO> getDefaults() {
        return ResponseEntity.ok(retentionOptimizationService.getFormDefaults());
    }

    @PostMapping("/api/retention/optimize")
    @ResponseBody
    public ResponseEntity<RetentionPlanResultDTO> optimize(
            @Valid @RequestBody RetentionOptimizationRequestDTO request,
            Principal principal) {
        log.info("[RetentionController] Optimización solicitada");
        return ResponseEntity.ok(retentionOptimizationService.optimize(request, principal.getName()));
    }
}