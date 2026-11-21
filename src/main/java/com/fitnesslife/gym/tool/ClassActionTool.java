package com.fitnesslife.gym.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.enums.PendingActionType;
import com.fitnesslife.gym.service.PendingActionService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClassActionTool {

    private final PendingActionService service;
    private final ToolRunner runner;

    @Tool(name = "prepareClassEnrollment", description = """
            Prepara la solicitud para inscribir al usuario actual en una clase grupal. NO inscribe: solo crea una \
            solicitud que el usuario debe confirmar con un botón. Antes de llamarla consulta getClassAvailability \
            para obtener el classId exacto de la clase. Falla si no hay cupo, si la clase ya empezó, si el usuario no \
            tiene plan vigente, si ya está inscrito o si se cruza con otra clase suya.""")
    public ToolResponse prepareClassEnrollment(
            @ToolParam(description = "classId de la clase, tal como lo devolvió getClassAvailability. Nunca lo inventes.")
            int classId,
            ToolContext toolContext) {
        return runner.run(toolContext, "prepareClassEnrollment", "classId=" + classId,
                session -> service.prepareForTool(session, PendingActionType.ENROLL_CLASS, classId));
    }

    @Tool(name = "prepareClassCancellation", description = """
            Prepara la solicitud para cancelar la inscripción del usuario actual en una clase. NO cancela: solo crea \
            una solicitud que el usuario debe confirmar con un botón. Antes de llamarla consulta getClassAvailability \
            para obtener el classId exacto; solo funciona si el usuario está inscrito y la clase no ha empezado.""")
    public ToolResponse prepareClassCancellation(
            @ToolParam(description = "classId de la clase, tal como lo devolvió getClassAvailability. Nunca lo inventes.")
            int classId,
            ToolContext toolContext) {
        return runner.run(toolContext, "prepareClassCancellation", "classId=" + classId,
                session -> service.prepareForTool(session, PendingActionType.CANCEL_ENROLLMENT, classId));
    }
}