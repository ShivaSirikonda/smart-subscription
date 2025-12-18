package com.smartsubscription.controller;
import com.smartsubscription.payment.entity.PaymentRequest;
import com.smartsubscription.payment.entity.PaymentResponse;
import com.smartsubscription.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    
    private final PaymentService paymentService;
    
    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(
            Principal principal,
            @Valid @RequestBody PaymentRequest request) {
        
        String userId = principal.getName(); // From JWT
        PaymentResponse response = paymentService.doPayment(userId, request);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(
            Principal principal,
            @PathVariable String paymentId) {
        
        String userId = principal.getName();
        PaymentResponse response = paymentService.cancelPayment(userId, paymentId);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            Principal principal,
            @PathVariable String paymentId) {
        
        String userId = principal.getName();
        PaymentResponse response = paymentService.getPayment(userId, paymentId);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/user")
    public ResponseEntity<PaymentResponse[]> getUserPayments(Principal principal) {
        String userId = principal.getName();
        PaymentResponse[] payments = paymentService.getUserPayments(userId);
        
        return ResponseEntity.ok(payments);
    }
}