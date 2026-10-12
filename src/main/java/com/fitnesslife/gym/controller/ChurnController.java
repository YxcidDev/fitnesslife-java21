package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.dto.ChurnPredictionDTO;
import com.fitnesslife.gym.model.ChurnPrediction;
import com.fitnesslife.gym.service.ChurnPredictionService;
import com.fitnesslife.gym.service.ChurnPredictionService.ChurnDashboardResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChurnController {

    private final ChurnPredictionService churnPredictionService;

    private static final int PAGE_SIZE = 20;

    @GetMapping("/admin/churn")
    public String churnDashboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) String profile,
            @RequestParam(required = false) String search,
            Model model) {

        log.debug("[ChurnController] Dashboard churn — página={}, risk={}, profile={}, search={}",
                page, risk, profile, search);

        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("riskOrder").ascending());

        ChurnDashboardResult dashboard = churnPredictionService.getDashboardData(risk, profile, search, pageable);
        Page<ChurnPrediction> predictionsPage = dashboard.predictions();

        model.addAttribute("churnResults", predictionsPage.getContent());
        model.addAttribute("highCount", dashboard.highCount());
        model.addAttribute("mediumCount", dashboard.mediumCount());
        model.addAttribute("lowCount", dashboard.lowCount());
        model.addAttribute("totalUsers", dashboard.totalUsers());
        model.addAttribute("churnPct", dashboard.churnPct());
        model.addAttribute("hasData", dashboard.hasData());
        model.addAttribute("lastCalculated", dashboard.lastCalculated());
        model.addAttribute("currentPage", predictionsPage.getNumber());
        model.addAttribute("totalPages", predictionsPage.getTotalPages());
        model.addAttribute("totalItems", predictionsPage.getTotalElements());
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("riskFilter", risk != null ? risk : "");
        model.addAttribute("profileFilter", profile != null ? profile : "");
        model.addAttribute("searchTerm", search != null ? search : "");
        model.addAttribute("currentPageNav", "churn");

        return "admin/churn-management";
    }

    @GetMapping("/api/churn/users")
    @ResponseBody
    public ResponseEntity<List<ChurnPredictionDTO>> getChurnUsers(
            @RequestParam(required = false) String risk) {
        return ResponseEntity.ok(churnPredictionService.getChurnUsers(risk));
    }

    @PostMapping("/api/churn/recalculate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> forceRecalculate() {
        log.info("[ChurnController] Recálculo manual disparado");
        long start = System.currentTimeMillis();
        var results = churnPredictionService.recalculateAndPersist();
        long elapsed = System.currentTimeMillis() - start;

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "processed", results.size(),
                "elapsedMs", elapsed));
    }
}