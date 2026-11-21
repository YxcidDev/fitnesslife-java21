package com.fitnesslife.gym.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResponse(boolean ok, String message, Object data) {

    public static ToolResponse success(Object data) {
        return new ToolResponse(true, null, data);
    }

    public static ToolResponse failure(String message) {
        return new ToolResponse(false, message, null);
    }
}