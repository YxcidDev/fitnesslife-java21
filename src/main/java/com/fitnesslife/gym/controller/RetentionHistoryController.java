package com.fitnesslife.gym.controller;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import com.fitnesslife.gym.dto.RetentionAssignmentStatusRequestDTO;
import com.fitnesslife.gym.dto.RetentionCampaignSaveRequestDTO;
import com.fitnesslife.gym.dto.RetentionCampaignSavedDTO;
import com.fitnesslife.gym.enums.RetentionAssignmentStatus;
import com.fitnesslife.gym.enums.RetentionDeliveryStatus;
import com.fitnesslife.gym.model.RetentionAssignment;
import com.fitnesslife.gym.service.RetentionHistoryService;
import com.fitnesslife.gym.service.RetentionHistoryService.HistoryPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RetentionHistoryController {

    private static final int PAGE_SIZE = 20;

    private final RetentionHistoryService historyService;

    @GetMapping("/admin/retention/history")
    public String history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String campaignId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) RetentionAssignmentStatus status,
            @RequestParam(required = false) RetentionDeliveryStatus delivery,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean launch,
            Model model) {

        log.debug("[RetentionHistory] página={}, campaña={}, acción={}, riesgo={}, estado={}, envío={}, búsqueda={}",
                page, campaignId, action, risk, status, delivery, search);

        HistoryPage result = historyService.getHistory(campaignId, action, risk, status, delivery, search, page, PAGE_SIZE);

        model.addAttribute("assignments", result.assignments().getContent());
        model.addAttribute("currentPage", result.assignments().getNumber());
        model.addAttribute("totalPages", result.assignments().getTotalPages());
        model.addAttribute("totalItems", result.assignments().getTotalElements());
        model.addAttribute("campaigns", result.campaigns());
        model.addAttribute("selectedCampaign", result.selectedCampaign());

        model.addAttribute("campaignFilter", campaignId != null ? campaignId : "");
        model.addAttribute("actionFilter", action != null ? action : "");
        model.addAttribute("riskFilter", risk != null ? risk : "");
        model.addAttribute("statusFilter", status != null ? status.name() : "");
        model.addAttribute("deliveryFilter", delivery != null ? delivery.name() : "");
        // abre el modal de confirmación al llegar desde "Revisar y lanzar campaña"
        model.addAttribute("autoLaunch", launch && result.selectedCampaign() != null);
        model.addAttribute("searchTerm", search != null ? search : "");
        model.addAttribute("currentPageNav", "churn");

        return "admin/retention-history";
    }

    @PostMapping("/api/retention/campaigns/{id}/save")
    @ResponseBody
    public ResponseEntity<RetentionCampaignSavedDTO> saveCampaign(
            @PathVariable String id,
            @Valid @RequestBody(required = false) RetentionCampaignSaveRequestDTO request,
            Principal principal) {

        log.info("[RetentionHistory] Guardado solicitado para la campaña {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(historyService.saveCampaign(id, principal.getName(), request));
    }

    @PatchMapping("/api/retention/assignments/{id}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody RetentionAssignmentStatusRequestDTO request,
            Principal principal) {

        RetentionAssignment updated = historyService.updateStatus(id, request.status(), principal.getName());
        return ResponseEntity.ok(Map.of("id", updated.getId(), "status", updated.getStatus().name()));
    }
}