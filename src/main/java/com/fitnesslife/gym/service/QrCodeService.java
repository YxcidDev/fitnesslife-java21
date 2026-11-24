package com.fitnesslife.gym.service;

import com.fitnesslife.gym.model.User;
import com.fitnesslife.gym.repository.UserRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class QrCodeService {

    private final UserRepository userRepository;

    private static final int QR_WIDTH = 500;
    private static final int QR_HEIGHT = 500;

    public String generateAndSaveQRCode(User user) {
        String token = UUID.randomUUID().toString();
        user.setQrToken(token);
        user.setQrCodePath(null);
        userRepository.save(user);
        return token;
    }

    public String regenerateQRCode(User user) {
        return generateAndSaveQRCode(user);
    }

    public boolean qrCodeExists(User user) {
        return user.getQrToken() != null && !user.getQrToken().isBlank();
    }

    public byte[] getQRCodePng(User user) throws WriterException, IOException {
        if (!qrCodeExists(user)) {
            return null;
        }
        BitMatrix matrix = new QRCodeWriter()
                .encode(user.getQrToken(), BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }
}