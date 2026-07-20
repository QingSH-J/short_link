package com.example.demo.service;

import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

@Service
public class QRcodeService {
    private final QRCodeWriter qrCodeWriter;

    public QRcodeService() {
        this.qrCodeWriter = new QRCodeWriter();
    }

    public byte[] generateQRCode(String content, int PNG_WIDTH, int PNG_HEIGHT) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        if (PNG_WIDTH <= 0 || PNG_HEIGHT <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");    
        }

        try {
            BitMatrix bitMatrix = qrCodeWriter.encode(
                content,
                BarcodeFormat.QR_CODE,
                PNG_WIDTH,
                PNG_HEIGHT
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG",output);
            return output.toByteArray();
        } catch (Exception e) {
                throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}
