package com.smartsubscription.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {
    
    public void sendEmail(String to, String subject, String body) {
        // In production, integrate with SendGrid, AWS SES, etc.
        log.info("Sending email to: {}, Subject: {}, Body: {}", to, subject, body);
        
        // Mock implementation
        try {
            // Simulate email sending
            Thread.sleep(100);
            log.info("Email sent successfully to: {}", to);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Email sending interrupted for: {}", to);
        }
    }
}