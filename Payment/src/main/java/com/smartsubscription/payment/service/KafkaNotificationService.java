package com.smartsubscription.payment.service;

import com.smartsubscription.payment.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationService implements NotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Override
    public void sendNotification(String userId, String type, String title, 
                               String message, Map<String, Object> data) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", type);
            notification.put("title", title);
            notification.put("message", message);
            notification.put("userId", userId);
            notification.put("timestamp", System.currentTimeMillis());
            
            if (data != null) {
                notification.put("data", data);
            }
            
            kafkaTemplate.send("notifications", userId, notification);
            log.info("Notification sent to user: {}, type: {}", userId, type);
            
        } catch (Exception e) {
            log.error("Failed to send notification via Kafka for user: {}", userId, e);
            throw new RuntimeException("Notification service unavailable");
        }
    }
}