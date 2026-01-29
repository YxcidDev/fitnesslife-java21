package com.fitnesslife.gym.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineUserWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);

        if (session.getPrincipal() != null) {
            activeUsers.add(session.getPrincipal().getName());
        }

        broadcast();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);

        if (session.getPrincipal() != null) {
            activeUsers.remove(session.getPrincipal().getName());
        }

        broadcast();
    }

    private void broadcast() {
        int online = activeUsers.size();

        sessions.forEach(ws -> {
            try {
                ws.sendMessage(new TextMessage(String.valueOf(online)));
            } catch (Exception ignored) {
            }
        });
    }
}
