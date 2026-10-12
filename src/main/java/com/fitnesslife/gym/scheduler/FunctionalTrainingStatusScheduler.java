package com.fitnesslife.gym.scheduler;

import com.fitnesslife.gym.service.FunctionalTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionalTrainingStatusScheduler {

    private final FunctionalTrainingService functionalTrainingService;

    @Scheduled(cron = "0 */15 * * * *")
    public void actualizarEstadosClases() {
        log.info("Iniciando actualización de estados de clases funcionales");
        try {
            functionalTrainingService.actualizarEstados();
            log.info("Actualización de estados de clases completada");
        } catch (Exception e) {
            log.error("Error actualizando estados de clases funcionales", e);
        }
    }
}