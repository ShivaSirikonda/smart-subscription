package com.smartsubscription.subscription.controller;

import com.smartsubscription.subscription.entity.Subscription;
import com.smartsubscription.subscription.entity.SubscriptionPlan;
import com.smartsubscription.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    
    // Create subscription
    @PostMapping("/subscribe")
    public ResponseEntity<Subscription> createSubscription(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> request) {
        Subscription subscription = subscriptionService.createSubscription(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscription);
    }
    
    // Get user subscriptions
    @GetMapping("/getAllSubscription")
    public ResponseEntity<List<Subscription>> getUserSubscriptions(
            @RequestHeader("X-User-Id") String userId) {
        List<Subscription> subscriptions = subscriptionService.getUserSubscriptions(userId);
        return ResponseEntity.ok(subscriptions);
    }
    
    // Get subscription by ID
    @GetMapping("getSubscription/{subscriptionId}")
    public ResponseEntity<Subscription> getSubscription(
            @PathVariable String subscriptionId,
            @RequestHeader("X-User-Id") String userId) {
        Subscription subscription = subscriptionService.getSubscription(subscriptionId, userId);
        return ResponseEntity.ok(subscription);
    }
    
    // Update subscription
    @PutMapping("/update/{subscriptionId}")
    public ResponseEntity<Subscription> updateSubscription(
            @PathVariable String subscriptionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, Object> updates) {
        Subscription subscription = subscriptionService.updateSubscription(
            subscriptionId, userId, updates);
        return ResponseEntity.ok(subscription);
    }
    
    // Cancel subscription
    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<Subscription> cancelSubscription(
            @PathVariable String subscriptionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        Subscription subscription = subscriptionService.cancelSubscription(
            subscriptionId, userId, reason);
        return ResponseEntity.ok(subscription);
    }
    
    // Pause subscription
    @PostMapping("/{subscriptionId}/pause")
    public ResponseEntity<Subscription> pauseSubscription(
            @PathVariable String subscriptionId,
            @RequestHeader("X-User-Id") String userId) {
        Subscription subscription = subscriptionService.pauseSubscription(subscriptionId, userId);
        return ResponseEntity.ok(subscription);
    }
    
    // Resume subscription
    @PostMapping("/{subscriptionId}/resume")
    public ResponseEntity<Subscription> resumeSubscription(
            @PathVariable String subscriptionId,
            @RequestHeader("X-User-Id") String userId) {
        Subscription subscription = subscriptionService.resumeSubscription(subscriptionId, userId);
        return ResponseEntity.ok(subscription);
    }
    
    // Get active subscription
    @GetMapping("/active")
    public ResponseEntity<Subscription> getActiveSubscription(
            @RequestHeader("X-User-Id") String userId) {
        Subscription subscription = subscriptionService.getActiveSubscription(userId);
        return ResponseEntity.ok(subscription);
    }
    
    // Get all plans (public endpoint)
    @GetMapping("/plans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        List<SubscriptionPlan> plans = subscriptionService.getAllPlans();
        return ResponseEntity.ok(plans);
    }
    
    // Get plan by code (public endpoint)
    @GetMapping("/plans/{planCode}")
    public ResponseEntity<SubscriptionPlan> getPlanByCode(@PathVariable String planCode) {
        SubscriptionPlan plan = subscriptionService.getPlanByCode(planCode);
        return ResponseEntity.ok(plan);
    }
}