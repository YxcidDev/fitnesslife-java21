package com.fitnesslife.gym.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "retention.defaults")
public class RetentionDefaults {

    private double minConfidence = 0;

    private List<Double> expectedMonths = new ArrayList<>(List.of(6.0, 4.0, 3.0));

    private List<Double> retentionLift = new ArrayList<>(
            List.of(0.02, 0.05, 0.04, 0.05, 0.12, 0.10, 0.08, 0.20, 0.15));

    private double reminderCost = 500;

    private double discountRate = 0.15;

    private double classCost = 3000;

    private double budget = 500_000;

    private int maxPromotions = 15;

    private int maxReminders = 200;

    private double minHighRiskCoverage = 0.8;
}
