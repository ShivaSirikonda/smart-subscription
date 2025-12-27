package com.smartsubscription.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsService {
    
    public void sendSms(String to, String message) {
        // In production, integrate with Twilio, AWS SNS, etc.
        log.info("Sending SMS to: {}, Message: {}", to, message);
        
        // Mock implementation
        try {
            // Simulate SMS sending
            Thread.sleep(50);
            log.info("SMS sent successfully to: {}", to);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("SMS sending interrupted for: {}", to);
        }
    }
}