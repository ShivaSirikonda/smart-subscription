package com.smartsubscription.subscription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "plan_id", nullable = false)
    private String planId;
    
    @Column(name = "plan_name", nullable = false)
    private String planName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;
    
    @Column(name = "end_date")
    private LocalDateTime endDate;
    
    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;
    
    @Column(name = "trial_end_date")
    private LocalDateTime trialEndDate;
    
    @Column(nullable = false)
    private Double price;
    
    @Column(nullable = false)
    private String currency = "USD";
    
    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle;
    
    @Column(name = "trial_days")
    private Integer trialDays = 0;
    
    @Column(name = "auto_renew")
    private Boolean autoRenew = true;
    
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}