package com.fitnesslife.gym.tool;

import java.util.ArrayList;
import java.util.List;

public final class ToolCallRecorder {

    private final int maxCalls;
    private int attempted;
    private int succeeded;
    private final List<String> pendingActionIds = new ArrayList<>();

    public ToolCallRecorder(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    public synchronized boolean tryStartCall() {
        if (attempted >= maxCalls) {
            return false;
        }
        attempted++;
        return true;
    }

    public synchronized void markSuccess() {
        succeeded++;
    }

    public synchronized int successfulCalls() {
        return succeeded;
    }

    public synchronized void addPendingAction(String id) {
        pendingActionIds.add(id);
    }

    public synchronized List<String> pendingActionIds() {
        return List.copyOf(pendingActionIds);
    }
}