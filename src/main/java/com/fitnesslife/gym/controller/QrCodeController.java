package com.fitnesslife.gym.controller;

import com.google.zxing.WriterException;
import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.service.QrCodeService;
import com.fitnesslife.gym.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Controller
public class QrCodeController {

    private final QrCodeService qrCodeService;
    private final UserService userService;

    @GetMapping("/qrcode")
    public String qrCodePage(Model model, Principal principal) throws WriterException, IOException {
        User user = userService.getUserOrThrow(principal.getName());

        if (!qrCodeService.qrCodeExists(user)) {
            log.info("Generando QR para usuario: {}", user.getIdentification());
            qrCodeService.generateAndSaveQRCode(user);
        }

        model.addAttribute("user", user);
        return "client/qr-code";
    }

    @GetMapping(value = "/api/qr/image", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getQRCodeImage(Principal principal) throws WriterException, IOException {
        User user = userService.getUserOrThrow(principal.getName());

        if (!qrCodeService.qrCodeExists(user)) {
            log.info("QR inexistente o del esquema anterior, regenerando para usuario: {}",
                    user.getIdentification());
            qrCodeService.generateAndSaveQRCode(user);
        }

        BufferedImage qrImage = qrCodeService.getQRCodeImage(user);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "png", baos);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(baos.toByteArray());
    }

    @PostMapping("/api/qr/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshQRCode(Principal principal)
            throws WriterException, IOException {
        User user = userService.getUserOrThrow(principal.getName());

        qrCodeService.regenerateQRCode(user);
        log.info("QR regenerado correctamente para usuario: {}", user.getIdentification());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Código QR actualizado correctamente"));
    }
}