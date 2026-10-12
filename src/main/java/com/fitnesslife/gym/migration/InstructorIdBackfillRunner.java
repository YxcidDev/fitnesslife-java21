package com.fitnesslife.gym.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.fitnesslife.gym.service.FunctionalTrainingService;
import com.fitnesslife.gym.service.FunctionalTrainingService.InstructorBackfillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Temporary migration: links existing classes to their trainer by user id.
 * Safe to run on every start (it only touches classes without an instructorId).
 * Delete this class and FunctionalTrainingService#backfillInstructorIds once no class is left unlinked.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class InstructorIdBackfillRunner implements ApplicationRunner {

    private final FunctionalTrainingService trainingService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            InstructorBackfillResult result = trainingService.backfillInstructorIds();
            if (result.linked() == 0 && result.unresolved() == 0) {
                log.info("Todas las clases ya tienen su entrenador vinculado por id");
                return;
            }
            log.info("Vinculación de clases con su entrenador: {} vinculadas, {} sin resolver",
                    result.linked(), result.unresolved());
        } catch (Exception e) {
            log.error("Error vinculando las clases existentes con su entrenador", e);
        }
    }
}