package com.fitnesslife.gym.controller;

import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fitnesslife.gym.dto.RetentionDispatchPreviewDTO;
import com.fitnesslife.gym.dto.RetentionDispatchProgressDTO;
import com.fitnesslife.gym.dto.RetentionLaunchRequestDTO;
import com.fitnesslife.gym.service.RetentionDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/retention/campaigns/{id}/dispatch")
public class RetentionDispatchController {

    private final RetentionDispatchService dispatchService;

    @GetMapping("/preview")
    public ResponseEntity<RetentionDispatchPreviewDTO> preview(@PathVariable String id) {
        return ResponseEntity.ok(dispatchService.preview(id));
    }

    @PostMapping("/launch")
    public ResponseEntity<RetentionDispatchProgressDTO> launch(
            @PathVariable String id,
            @Valid @RequestBody RetentionLaunchRequestDTO request,
            Principal principal) {

        log.info("[RetentionDispatch] Lanzamiento solicitado por {} para la campaña {}", principal.getName(), id);
        return ResponseEntity.accepted().body(dispatchService.launch(id, principal.getName(), request));
    }

    @PostMapping("/cancel")
    public ResponseEntity<RetentionDispatchProgressDTO> cancel(@PathVariable String id, Principal principal) {
        return ResponseEntity.ok(dispatchService.cancel(id, principal.getName()));
    }

    @PostMapping("/retry")
    public ResponseEntity<RetentionDispatchProgressDTO> retry(@PathVariable String id, Principal principal) {
        return ResponseEntity.ok(dispatchService.retryFailed(id, principal.getName()));
    }

    @GetMapping("/progress")
    public ResponseEntity<RetentionDispatchProgressDTO> progress(@PathVariable String id) {
        return ResponseEntity.ok(dispatchService.progress(id));
    }
}
