package com.smartsubscription.subscription.service;

import com.smartsubscription.subscription.entity.SubscriptionPlan;
import com.smartsubscription.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {
    
    private final SubscriptionPlanRepository planRepository;
    
    // Get all plans
    public List<SubscriptionPlan> getAllPlans() {
        return planRepository.findAll();
    }
    
    // Get active plans only
    public List<SubscriptionPlan> getActivePlans() {
        return planRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }
    
    // Get plan by ID
    public SubscriptionPlan getPlanById(String id) {
        return planRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Plan not found with ID: " + id));
    }
    
    // Create new plan
    @Transactional
    public SubscriptionPlan createPlan(SubscriptionPlan plan) {
        // Validate required fields
        if (plan.getCode() == null || plan.getCode().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Plan code is required");
        }
        
        if (plan.getName() == null || plan.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Plan name is required");
        }
        
        if (plan.getPrice() == null || plan.getPrice() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Price must be greater than 0");
        }
        
        if (plan.getBillingCycle() == null || plan.getBillingCycle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Billing cycle is required");
        }
        
        // Check if plan code already exists
        if (planRepository.existsByCode(plan.getCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Plan code already exists: " + plan.getCode());
        }
        
        // Generate ID if not provided
        if (plan.getId() == null) {
            plan.setId(UUID.randomUUID().toString());
        }
        
        // Set defaults if not provided
        if (plan.getIsActive() == null) {
            plan.setIsActive(true);
        }
        
        if (plan.getCurrency() == null) {
            plan.setCurrency("USD");
        }
        
        // Ensure code is uppercase for consistency
        plan.setCode(plan.getCode().toUpperCase());
        
        return planRepository.save(plan);
    }
    
    // Update plan
    @Transactional
    public SubscriptionPlan updatePlan(SubscriptionPlan updatedPlan) {
        // Check if plan exists
        SubscriptionPlan existingPlan = getPlanById(updatedPlan.getId());
        
        // Update fields
        if (updatedPlan.getCode() != null) {
            // Check if new code conflicts with other plans
            if (!existingPlan.getCode().equals(updatedPlan.getCode()) && 
                planRepository.existsByCode(updatedPlan.getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Plan code already exists: " + updatedPlan.getCode());
            }
            existingPlan.setCode(updatedPlan.getCode().toUpperCase());
        }
        
        if (updatedPlan.getName() != null) {
            existingPlan.setName(updatedPlan.getName());
        }
        
        if (updatedPlan.getDescription() != null) {
            existingPlan.setDescription(updatedPlan.getDescription());
        }
        
        if (updatedPlan.getPrice() != null) {
            if (updatedPlan.getPrice() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Price must be greater than 0");
            }
            existingPlan.setPrice(updatedPlan.getPrice());
        }
        
        if (updatedPlan.getCurrency() != null) {
            existingPlan.setCurrency(updatedPlan.getCurrency());
        }
        
        if (updatedPlan.getBillingCycle() != null) {
            existingPlan.setBillingCycle(updatedPlan.getBillingCycle());
        }
        
        if (updatedPlan.getTrialDays() != null) {
            existingPlan.setTrialDays(updatedPlan.getTrialDays());
        }
        
        if (updatedPlan.getIsActive() != null) {
            existingPlan.setIsActive(updatedPlan.getIsActive());
        }
        
        if (updatedPlan.getMaxUsers() != null) {
            existingPlan.setMaxUsers(updatedPlan.getMaxUsers());
        }
        
        if (updatedPlan.getMaxProjects() != null) {
            existingPlan.setMaxProjects(updatedPlan.getMaxProjects());
        }
        
        if (updatedPlan.getStorageLimit() != null) {
            existingPlan.setStorageLimit(updatedPlan.getStorageLimit());
        }
        
        if (updatedPlan.getApiRateLimit() != null) {
            existingPlan.setApiRateLimit(updatedPlan.getApiRateLimit());
        }
        
        if (updatedPlan.getSortOrder() != null) {
            existingPlan.setSortOrder(updatedPlan.getSortOrder());
        }
        
        return planRepository.save(existingPlan);
    }
    
    // Delete plan
    @Transactional
    public void deletePlan(String planId) {
        // Check if plan exists
        if (!planRepository.existsById(planId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Plan not found with ID: " + planId);
        }
        
        // Check if any subscriptions are using this plan
        // You might want to add this check when you have subscription table
        
        planRepository.deleteById(planId);
    }
    
    // Toggle plan active status
    @Transactional
    public SubscriptionPlan togglePlanActive(String planId) {
        SubscriptionPlan plan = getPlanById(planId);
        plan.setIsActive(!plan.getIsActive());
        return planRepository.save(plan);
    }
}