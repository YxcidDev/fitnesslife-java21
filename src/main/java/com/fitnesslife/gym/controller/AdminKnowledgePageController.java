package com.fitnesslife.gym.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.fitnesslife.gym.config.ChatbotProperties;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminKnowledgePageController {

    private final ChatbotProperties properties;

    @GetMapping("/admin/knowledge")
    public String knowledgePage(Model model) {
        model.addAttribute("currentPage", "knowledge");
        model.addAttribute("activePage", "knowledge");
        model.addAttribute("maxFileBytes", properties.ingestion().maxFileBytes());
        model.addAttribute("maxFileMb", properties.ingestion().maxFileBytes() / (1024 * 1024));
        return "admin/knowledge";
    }
}