package com.fitnesslife.gym.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OptimizerRestClientConfig {

    @Bean
    public RestClient optimizerRestClient(
            @Value("${optimizer.api.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${optimizer.api.read-timeout-ms:60000}") long readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
