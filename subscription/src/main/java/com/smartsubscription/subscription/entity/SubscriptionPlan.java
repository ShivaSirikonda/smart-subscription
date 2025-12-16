package com.smartsubscription.subscription.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {
    
    @Id
    private String id;
    
    @Column(unique = true, nullable = false)
    private String code;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(nullable = false)
    private Double price;
    
    @Column(nullable = false)
    private String currency = "USD";
    
    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle;
    
    @Column(name = "trial_days")
    private Integer trialDays = 0;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @Column(name = "max_users")
    private Integer maxUsers = 1;
    
    @Column(name = "max_projects")
    private Integer maxProjects = 10;
    
    @Column(name = "storage_limit")
    private Long storageLimit;
    
    @Column(name = "api_rate_limit")
    private Integer apiRateLimit = 1000;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}