package com.fitnesslife.gym.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.fitnesslife.gym.exception.ChatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ChatWebConfig implements WebMvcConfigurer {

    private final ChatbotProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!"POST".equals(request.getMethod())) {
                    return true;
                }
                long length = request.getContentLengthLong();
                boolean chunked = request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
                if (length > properties.limits().maxRequestBytes() || (length < 0 && chunked)) {
                    throw ChatException.payloadTooLarge();
                }
                return true;
            }
        }).addPathPatterns("/api/chat/**");
    }
}