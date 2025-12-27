package com.smartsubscription.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsubscription.notification.entity.Notification;
import com.smartsubscription.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumerService {
    @Autowired
      NotificationRepository notificationRepository;
    @Autowired
      ObjectMapper objectMapper;
    @Autowired
      EmailService emailService;
    @Autowired
      SmsService smsService;
    
    @KafkaListener(topics = "notifications", groupId = "notification-group")
    public void consumeNotification(String message) {
        try {
            Map<String, Object> notificationData = objectMapper.readValue(message, Map.class);
            processNotification(notificationData);
        } catch (Exception e) {
            log.error("Error processing notification message: {}", message, e);
        }
    }
    
    private void processNotification(Map<String, Object> notificationData) {
        try {
            String userId = (String) notificationData.get("userId");
            String type = (String) notificationData.get("type");
            String title = (String) notificationData.get("title");
            String message = (String) notificationData.get("message");
            
            @SuppressWarnings("unchecked")

            // Save to database
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .read(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            notificationRepository.save(notification);
            log.info("Notification saved for user: {}, type: {}", userId, type);
            
            // Send email notification (async)
            sendEmailNotification(userId, title, message);
            
            // Send SMS for critical notifications
            if (type.contains("FAILED") || type.contains("EXPIRED")) {
                sendSmsNotification(userId, message);
            }
            
        } catch (Exception e) {
            log.error("Error processing notification: {}", notificationData, e);
        }
    }
    
    private void sendEmailNotification(String userId, String subject, String body) {
        try {
            emailService.sendEmail(userId, subject, body);
            log.debug("Email sent to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send email to user: {}", userId, e);
        }
    }
    
    private void sendSmsNotification(String userId, String message) {
        try {
            smsService.sendSms(userId, message);
            log.debug("SMS sent to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send SMS to user: {}", userId, e);
        }
    }
}