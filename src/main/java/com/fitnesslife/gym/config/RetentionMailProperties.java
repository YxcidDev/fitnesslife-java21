package com.fitnesslife.gym.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "retention.mail")
public class RetentionMailProperties {

    private boolean dryRun = true;

    private String from = "";
    private String fromName = "Fitness Life";
    private String gymName = "Fitness Life";

    private String baseUrl = "http://localhost:8080";
    private String reminderPath = "/";
    private String promotionPath = "/";
    private String classPath = "/";

    private int batchSize = 20;

    private long delayBetweenMs = 300;

    private long pollMs = 5000;

    private int staleClaimMinutes = 10;

    private int maxAttempts = 3;

    private int promotionValidDays = 15;
}
