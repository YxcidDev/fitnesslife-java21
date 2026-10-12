package com.fitnesslife.gym.controller;

import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.fitnesslife.gym.model.FunctionalTraining;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.service.FunctionalTrainingService;
import com.fitnesslife.gym.service.PaymentService;
import com.fitnesslife.gym.service.PlanService;
import com.fitnesslife.gym.service.UserService;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final UserService userService;
    private final FunctionalTrainingService trainingService;
    private final PlanService planService;
    private final PaymentService paymentService;

    private static final String VIEW_INDEX = "index";
    private static final String VIEW_LOGIN = "auth/login";
    private static final String VIEW_HOME = "client/home";
    private static final String VIEW_QR_CODE = "client/qr-code";
    private static final String VIEW_USER_PROFILE = "client/user-profile";
    private static final String VIEW_PRIVACY = "legal/privacy-policy";
    private static final String VIEW_TERMS = "legal/terms-and-conditions";
    private static final String VIEW_COOKIES = "legal/cookie-policy";

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("availablePlans", planService.getAllPlans());
        return VIEW_INDEX;
    }

    @GetMapping("/login")
    public String login() {
        return VIEW_LOGIN;
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        User user = userService.getUserOrThrow(principal.getName());
        model.addAttribute("identification", user.getIdentification());

        boolean hasActivePlan = paymentService.getActivePayment(user.getId()).isPresent();
        model.addAttribute("hasActivePlan", hasActivePlan);

        List<FunctionalTraining> confirmedClasses = trainingService.getUserTodayTrainings(user.getIdentification());
        model.addAttribute("confirmedClasses", confirmedClasses);

        List<FunctionalTraining> trainings = trainingService.getTodayTrainings();
        model.addAttribute("trainings", trainings);

        return VIEW_HOME;
    }

    @GetMapping("/qr-code")
    public String qrCode() {
        return VIEW_QR_CODE;
    }

    @GetMapping("/user-profile")
    public String userProfile() {
        return VIEW_USER_PROFILE;
    }

    @GetMapping("/legal/privacy-policy")
    public String privacyPolicy() {
        return VIEW_PRIVACY;
    }

    @GetMapping("/legal/terms-and-conditions")
    public String termsAndConditions() {
        return VIEW_TERMS;
    }

    @GetMapping("/legal/cookie-policy")
    public String cookiePolicy() {
        return VIEW_COOKIES;
    }
}