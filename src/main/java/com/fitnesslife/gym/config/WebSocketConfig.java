package com.fitnesslife.gym.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import com.fitnesslife.gym.websocket.OnlineUserWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OnlineUserWebSocketHandler handler;

    public WebSocketConfig(OnlineUserWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(handler, "/ws/online-users")
                .setAllowedOrigins("*");
    }
}
