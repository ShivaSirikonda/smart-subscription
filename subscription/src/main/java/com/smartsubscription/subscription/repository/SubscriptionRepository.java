package com.smartsubscription.subscription.repository;

import com.smartsubscription.subscription.entity.Subscription;
import com.smartsubscription.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    
    List<Subscription> findByUserId(String userId);
    
    Optional<Subscription> findByIdAndUserId(String id, String userId);
    
    Optional<Subscription> findByUserIdAndStatus(String userId, SubscriptionStatus status);
    
    List<Subscription> findByStatusAndNextBillingDateBefore(
        SubscriptionStatus status, LocalDateTime date);
    
    List<Subscription> findByStatusAndTrialEndDateBefore(
        SubscriptionStatus status, LocalDateTime date);
    
    boolean existsByUserIdAndStatus(String userId, SubscriptionStatus status);

    boolean existsByUserIdAndPlanIdAndStatus(String userId, String planId, SubscriptionStatus status);

    long countByUserIdAndStatus(String userId, SubscriptionStatus status);
}