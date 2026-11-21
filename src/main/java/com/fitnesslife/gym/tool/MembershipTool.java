package com.fitnesslife.gym.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.service.MembershipStatusService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MembershipTool {

    private final MembershipStatusService service;
    private final ToolRunner runner;

    @Tool(name = "getMembershipStatus", description = """
            Consulta en tiempo real el estado de la membresía del usuario que está conversando: si tiene un plan \
            vigente, cuál es, desde y hasta cuándo, cuántos días le quedan, si tiene pagos pendientes y el estado de \
            su último pago. Úsala para preguntas como "¿cuándo vence mi plan?" o "¿mi pago fue aprobado?". \
            No recibe parámetros: siempre consulta al usuario actual.""")
    public ToolResponse getMembershipStatus(ToolContext toolContext) {
        return runner.run(toolContext, "getMembershipStatus", "", service::status);
    }
}