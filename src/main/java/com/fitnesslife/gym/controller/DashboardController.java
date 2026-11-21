package com.fitnesslife.gym.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/admin/bi")
    public String businessIntelligence(Model model) {
        model.addAttribute("currentPage", "bi");
        model.addAttribute("activePage", "bi");
        return "admin/bi";
    }
}