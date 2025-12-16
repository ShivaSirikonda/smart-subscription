package com.smartsubscription.subscription.controller;
import com.smartsubscription.security.JwtUtil;
import com.smartsubscription.subscription.entity.SubscriptionPlan;
import com.smartsubscription.subscription.service.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions/plans")
@RequiredArgsConstructor
public class PlanController {
     @Autowired
      PlanService planService;

    private final JwtUtil jwtUtil;

    // ========== PUBLIC ENDPOINTS (No token needed) ==========

    @GetMapping("/getAllPlans")
    public ResponseEntity<List<SubscriptionPlan>> getAllPlans() {
        return ResponseEntity.ok(planService.getAllPlans());
    }

    @GetMapping("/{planId}")
    public ResponseEntity<SubscriptionPlan> getPlanById(@PathVariable String planId) {
        return ResponseEntity.ok(planService.getPlanById(planId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionPlan>> getActivePlans() {
        return ResponseEntity.ok(planService.getActivePlans());
    }

    // ========== ADMIN ENDPOINTS (Need ADMIN token) ==========

    @PostMapping("/addPlan")
    public ResponseEntity<?> createPlan(
            @RequestBody SubscriptionPlan plan,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        // Validate token and check if ADMIN
        String error = validateAdminToken(authHeader);
        if (error != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        SubscriptionPlan createdPlan = planService.createPlan(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlan);
    }

    @PutMapping("updatePlan/{planId}")
    public ResponseEntity<?> updatePlan(
            @PathVariable String planId,
            @RequestBody SubscriptionPlan plan,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String error = validateAdminToken(authHeader);
        if (error != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        plan.setId(planId);
        SubscriptionPlan updatedPlan = planService.updatePlan(plan);
        return ResponseEntity.ok(updatedPlan);
    }

    @DeleteMapping("deletePlan/{planId}")
    public ResponseEntity<?> deletePlan(
            @PathVariable String planId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String error = validateAdminToken(authHeader);
        if (error != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        planService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{planId}/toggle-active")
    public ResponseEntity<?> togglePlanActive(
            @PathVariable String planId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String error = validateAdminToken(authHeader);
        if (error != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        SubscriptionPlan plan = planService.togglePlanActive(planId);
        return ResponseEntity.ok(plan);
    }

    // ========== HELPER METHOD ==========

    private String validateAdminToken(String authHeader) {
        // 1. Check if Authorization header exists
        if (authHeader == null || authHeader.isEmpty()) {
            return "Missing Authorization header";
        }

        // 2. Extract token from "Bearer <token>"
        String token;
        if (authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            return "Invalid Authorization format. Use: Bearer <token>";
        }

        // 3. Validate JWT token
        if (!jwtUtil.isTokenValid(token)) {
            return "Invalid or expired token";
        }

        // 4. Extract role from token
        String role = jwtUtil.extractRole(token);
        if (role == null || !role.equalsIgnoreCase("ADMIN")) {
            return "Admin access required";
        }

        // All checks passed
        return null;
    }
}