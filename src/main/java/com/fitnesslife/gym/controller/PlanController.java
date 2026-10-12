package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.model.Payment;
import com.fitnesslife.gym.model.Plan;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.service.PaymentService;
import com.fitnesslife.gym.service.PlanService;
import com.fitnesslife.gym.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/plan")
@RequiredArgsConstructor
@Slf4j
public class PlanController {

    private final PlanService planService;
    private final PaymentService paymentService;
    private final UserService userService;

    @Value("${epayco.public-key}")
    private String epaycoPublicKey;

    @Value("${epayco.test-mode}")
    private boolean epaycoTestMode;

    @GetMapping
    public String showPlans(
            Model model,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(value = "success", required = false) String success) {

        log.info("Cargando página de planes para usuario: {}", userDetails.getUsername());

        model.addAttribute("epaycoPublicKey", epaycoPublicKey);
        model.addAttribute("epaycoTestMode", epaycoTestMode);

        User currentUser = userService.getUserOrThrow(userDetails.getUsername());

        Optional<Payment> activePaymentOpt = paymentService.getActivePayment(currentUser.getId());

        if (activePaymentOpt.isPresent()) {
            Payment activePayment = activePaymentOpt.get();

            log.info("Usuario {} tiene plan activo: {}",
                    currentUser.getEmail(),
                    activePayment.getPlan().getPlanName());

            model.addAttribute("hasActivePlan", true);
            model.addAttribute("activePayment", activePayment);
            model.addAttribute("activePlan", activePayment.getPlan());
            model.addAttribute("validFrom", activePayment.getValidFrom());
            model.addAttribute("validUntil", activePayment.getValidUntil());

            if ("true".equals(success)) {
                model.addAttribute("showSuccessAlert", true);
            }

        } else {
            log.info("Usuario {} no tiene plan activo, mostrando planes disponibles",
                    currentUser.getEmail());

            List<Plan> availablePlans = planService.getAllPlans();

            model.addAttribute("hasActivePlan", false);
            model.addAttribute("availablePlans", availablePlans);

            if (currentUser.getPlan() != null && !currentUser.getPlan().isEmpty()) {
                model.addAttribute("planExpired", true);
            }
        }

        log.info("Página de planes cargada exitosamente");
        return "client/plan";
    }
}