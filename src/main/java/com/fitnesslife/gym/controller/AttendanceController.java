package com.fitnesslife.gym.controller;

import com.fitnesslife.gym.dto.QrScanRequestDTO;
import com.fitnesslife.gym.service.AttendanceService;
import com.fitnesslife.gym.service.AttendanceService.AttendanceResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/attendance/kiosk")
    public String kioskView() {
        return "attendance/kiosk";
    }

    @PostMapping("/api/attendance/verify")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifyAttendance(
            @Valid @RequestBody QrScanRequestDTO body) {

        log.info("Received attendance verification request");

        AttendanceResult result = attendanceService.processQrScan(body.qrCode().trim());

        Map<String, Object> response = new HashMap<>();
        response.put("status", result.status().name());
        response.put("message", result.message());
        response.put("userName", result.userName());
        response.put("userPhoto", result.userPhoto());
        response.put("userPlan", result.userPlan());
        response.put("userId", result.userId());

        log.info("Attendance result: {}", result.status());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/attendance/active-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getActiveCount() {
        int count = attendanceService.getActiveGymCount();
        return ResponseEntity.ok(Map.of("count", count));
    }
}