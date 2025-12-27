package com.smartsubscription.payment.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public interface NotificationService {
    void sendNotification(String userId, String type, String title, 
                         String message, Map<String, Object> data);
}