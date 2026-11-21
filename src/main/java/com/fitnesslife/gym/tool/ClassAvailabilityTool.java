package com.fitnesslife.gym.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.service.ClassAvailabilityService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClassAvailabilityTool {

    private final ClassAvailabilityService service;
    private final ToolRunner runner;

    @Tool(name = "getClassAvailability", description = """
            Consulta en tiempo real las clases funcionales grupales de Fitness Life de un día: horario, entrenador, \
            sala, capacidad, inscritos y cupos libres, y si el usuario ya está inscrito. Úsala para preguntas como \
            "¿hay cupo en Spinning hoy?" o "¿qué clases hay mañana?". Solo hay datos de hoy y de los próximos días.""")
    public ToolResponse getClassAvailability(
            @ToolParam(description = "Día a consultar: la fecha en formato yyyy-MM-dd, o las palabras hoy o mañana. "
                    + "Si el usuario no indica un día, usa hoy.", required = false) String date,
            @ToolParam(description = "Nombre o parte del nombre de la clase, por ejemplo spinning o yoga. "
                    + "Omítelo para ver todas las clases del día.", required = false) String className,
            ToolContext toolContext) {
        return runner.run(toolContext, "getClassAvailability", "date=" + date + ", className=" + className,
                session -> service.availability(session, date, className));
    }
}