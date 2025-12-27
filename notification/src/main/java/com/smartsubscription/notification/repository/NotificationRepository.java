package com.smartsubscription.notification.repository;

import com.smartsubscription.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByUserId(String userId);
    List<Notification> findByUserIdAndRead(String userId, boolean read);
    List<Notification> findByUserIdAndType(String userId, String type);
    long countByUserIdAndRead(String userId, boolean read);
}