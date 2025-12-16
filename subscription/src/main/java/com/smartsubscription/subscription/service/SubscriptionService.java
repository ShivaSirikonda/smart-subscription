package com.smartsubscription.subscription.service;

import com.smartsubscription.subscription.entity.Subscription;
import com.smartsubscription.subscription.entity.SubscriptionPlan;
import com.smartsubscription.subscription.entity.SubscriptionStatus;
import com.smartsubscription.subscription.repository.SubscriptionPlanRepository;
import com.smartsubscription.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    
    // Create subscription
    @Transactional
    public Subscription createSubscription(String userId, Map<String, Object> request) {

        String planId = (request.containsKey("planId"))? (String) request.get("planId") : null;
        if (planId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan ID is required");
        }
        // Check if user already has active subscription
        if (subscriptionRepository.existsByUserIdAndPlanIdAndStatus(userId, planId,SubscriptionStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "User already has an active subscription for given plan");
        }
        
        SubscriptionPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
        
        if (!plan.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan is not active");
        }
        
        // Create subscription
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID().toString());
        subscription.setUserId(userId);
        subscription.setPlanId(plan.getId());
        subscription.setPlanName(plan.getName());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());
        subscription.setEndDate(calculateEndDate(LocalDateTime.now(), plan.getBillingCycle()));
        subscription.setNextBillingDate(subscription.getEndDate());
        subscription.setPrice(plan.getPrice());
        subscription.setCurrency(plan.getCurrency());
        subscription.setBillingCycle(plan.getBillingCycle());
        
        // Handle trial
        Integer trialDays = (Integer) request.getOrDefault("trialDays", plan.getTrialDays());
        subscription.setTrialDays(trialDays);
        
        if (trialDays > 0) {
            subscription.setStatus(SubscriptionStatus.TRIAL);
            subscription.setTrialEndDate(LocalDateTime.now().plusDays(trialDays));
        }
        
        // Auto renew
        Boolean autoRenew = (Boolean) request.getOrDefault("autoRenew", true);
        subscription.setAutoRenew(autoRenew);
        
        subscriptionRepository.save(subscription);
        
        log.info("Created subscription {} for user {}", subscription.getId(), userId);
        return subscription;
    }
    
    // Get user subscriptions
    public List<Subscription> getUserSubscriptions(String userId) {
        return subscriptionRepository.findByUserId(userId);
    }
    
    // Get subscription by ID
    public Subscription getSubscription(String subscriptionId, String userId) {
        return subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Subscription not found"));
    }
    
    // Update subscription
    @Transactional
    public Subscription updateSubscription(String subscriptionId, String userId, Map<String, Object> updates) {
        Subscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Subscription not found"));
        
        // Update auto-renew
        if (updates.containsKey("autoRenew")) {
            subscription.setAutoRenew((Boolean) updates.get("autoRenew"));
        }
        
        // Update plan (upgrade/downgrade)
        if (updates.containsKey("planId")) {
            String newPlanId = (String) updates.get("planId");
            SubscriptionPlan newPlan = planRepository.findById(newPlanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
            
            // Update subscription with new plan
            subscription.setPlanId(newPlan.getId());
            subscription.setPlanName(newPlan.getName());
            subscription.setPrice(newPlan.getPrice());
            subscription.setBillingCycle(newPlan.getBillingCycle());
            subscription.setEndDate(calculateEndDate(LocalDateTime.now(), newPlan.getBillingCycle()));
            subscription.setNextBillingDate(subscription.getEndDate());
            
            log.info("User {} upgraded to plan {}", userId, newPlan.getName());
        }
        
        return subscriptionRepository.save(subscription);
    }
    
    // Cancel subscription
    @Transactional
    public Subscription cancelSubscription(String subscriptionId, String userId, String reason) {
        Subscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Subscription not found"));
        
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Subscription already cancelled");
        }
        
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancellationReason(reason);
        subscription.setEndDate(LocalDateTime.now());
        subscription.setNextBillingDate(null);
        
        subscriptionRepository.save(subscription);
        
        log.info("Cancelled subscription {} for user {}", subscription.getId(), userId);
        return subscription;
    }
    
    // Pause subscription
    @Transactional
    public Subscription pauseSubscription(String subscriptionId, String userId) {
        Subscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Subscription not found"));
        
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Only active subscriptions can be paused");
        }
        
        subscription.setStatus(SubscriptionStatus.PAUSED);
        
        subscriptionRepository.save(subscription);
        
        log.info("Paused subscription {} for user {}", subscription.getId(), userId);
        return subscription;
    }
    
    // Resume subscription
    @Transactional
    public Subscription resumeSubscription(String subscriptionId, String userId) {
        Subscription subscription = subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Subscription not found"));
        
        if (subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Only paused subscriptions can be resumed");
        }
        
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        
        subscriptionRepository.save(subscription);
        
        log.info("Resumed subscription {} for user {}", subscription.getId(), userId);
        return subscription;
    }
    
    // Get active subscription for user
    public Subscription getActiveSubscription(String userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "No active subscription found"));
    }
    
    // Get all subscription plans
    public List<SubscriptionPlan> getAllPlans() {
        return planRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }
    
    // Get plan by code
    public SubscriptionPlan getPlanByCode(String planCode) {
        return planRepository.findByCode(planCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Plan not found"));
    }
    
    // Scheduled job to process renewals
    @Scheduled(cron = "0 0 1 * * ?") // Run daily at 1 AM
    public void processRenewals() {
        log.info("Processing subscription renewals");
        
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> dueForRenewal = subscriptionRepository
            .findByStatusAndNextBillingDateBefore(SubscriptionStatus.ACTIVE, now);
        
        for (Subscription subscription : dueForRenewal) {
            try {
                // Check if auto-renew is enabled
                if (Boolean.TRUE.equals(subscription.getAutoRenew())) {
                    // Renew subscription
                    subscription.setStartDate(LocalDateTime.now());
                    subscription.setEndDate(calculateEndDate(LocalDateTime.now(), subscription.getBillingCycle()));
                    subscription.setNextBillingDate(subscription.getEndDate());
                    subscriptionRepository.save(subscription);
                    
                    log.info("Renewed subscription {} for user {}", 
                        subscription.getId(), subscription.getUserId());
                    
                    // Here you would typically call payment service
                    // paymentService.processPayment(subscription.getUserId(), subscription.getPrice());
                } else {
                    // Expire subscription
                    subscription.setStatus(SubscriptionStatus.EXPIRED);
                    subscriptionRepository.save(subscription);
                    
                    log.info("Expired subscription {} for user {}", 
                        subscription.getId(), subscription.getUserId());
                }
            } catch (Exception e) {
                log.error("Failed to process renewal for subscription {}: {}", 
                    subscription.getId(), e.getMessage());
            }
        }
    }
    
    // Scheduled job to end trials
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    public void processTrialEndings() {
        log.info("Processing trial endings");
        
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> trialsEnding = subscriptionRepository
            .findByStatusAndTrialEndDateBefore(SubscriptionStatus.TRIAL, now);
        
        for (Subscription subscription : trialsEnding) {
            try {
                // Convert trial to active (if payment succeeds) or expired
                // For simplicity, we'll mark as expired
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                subscriptionRepository.save(subscription);
                
                log.info("Trial ended for subscription {} for user {}", 
                    subscription.getId(), subscription.getUserId());
            } catch (Exception e) {
                log.error("Failed to process trial ending for subscription {}: {}", 
                    subscription.getId(), e.getMessage());
            }
        }
    }
    
    // Helper method to calculate end date
    private LocalDateTime calculateEndDate(LocalDateTime startDate, String billingCycle) {
        return switch (billingCycle.toUpperCase()) {
            case "MONTHLY" -> startDate.plusMonths(1);
            case "QUARTERLY" -> startDate.plusMonths(3);
            case "YEARLY" -> startDate.plusYears(1);
            case "WEEKLY" -> startDate.plusWeeks(1);
            case "DAILY" -> startDate.plusDays(1);
            default -> startDate.plusMonths(1);
        };
    }
}