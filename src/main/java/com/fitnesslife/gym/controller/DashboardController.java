package com.fitnesslife.gym.controller;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.fitnesslife.gym.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    private static final String VIEW_DASHBOARD = "admin/dashboard";

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        log.info("Cargando dashboard");
        model.addAttribute("currentPage", "dashboard");
        return VIEW_DASHBOARD;
    }

    @GetMapping("/api/dashboard/metrics")
    @ResponseBody
    public Map<String, Object> getDashboardMetrics() {
        return dashboardService.getMainMetrics();
    }

    @GetMapping("/api/dashboard/monthly-revenue")
    @ResponseBody
    public List<Map<String, Object>> getMonthlyRevenue() {
        return dashboardService.getMonthlyRevenue();
    }

    @GetMapping("/api/dashboard/recent-transactions")
    @ResponseBody
    public List<Map<String, Object>> getRecentTransactions() {
        return dashboardService.getRecentTransactions();
    }

    @GetMapping("/api/dashboard/plan-sales")
    @ResponseBody
    public List<Map<String, Object>> getPlanSales() {
        return dashboardService.getPlanSalesDistribution();
    }

    @GetMapping("/api/dashboard/access-by-day")
    @ResponseBody
    public List<Map<String, Object>> getAccessByDay() {
        return dashboardService.getAccessByDayOfWeek();
    }

    @GetMapping("/api/dashboard/gender-distribution")
    @ResponseBody
    public List<Map<String, Object>> getGenderDistribution() {
        return dashboardService.getGenderDistribution();
    }

    @GetMapping("/api/dashboard/popular-classes")
    @ResponseBody
    public List<Map<String, Object>> getPopularClasses() {
        return dashboardService.getPopularClasses();
    }

    @GetMapping("/api/dashboard/expiring-plans")
    @ResponseBody
    public List<Map<String, Object>> getExpiringPlans() {
        return dashboardService.getExpiringPlans();
    }

    @GetMapping("/api/dashboard/peak-hours-heatmap")
    @ResponseBody
    public List<Map<String, Object>> getPeakHoursHeatmap() {
        return dashboardService.getPeakHoursHeatmap();
    }
}