package com.smartsubscription.payment.service;
import com.smartsubscription.payment.entity.Payment;
import com.smartsubscription.payment.entity.PaymentRequest;
import com.smartsubscription.payment.entity.PaymentResponse;
import com.smartsubscription.payment.entity.PaymentStatus;
import com.smartsubscription.payment.repository.PaymentRepository;
import com.smartsubscription.subscription.entity.Subscription;
import com.smartsubscription.subscription.entity.SubscriptionStatus;
import com.smartsubscription.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository; // Direct access
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${payment.provider.url}")
    private String paymentProviderUrl;
    
    @Value("${payment.provider.api-key}")
    private String paymentProviderApiKey;
    
    /**
     * Process payment and update subscription status
     */
    @Transactional
    public PaymentResponse doPayment(String userId, PaymentRequest request) {
        log.info("Processing payment for user: {}, subscription: {}", userId, request.getSubscriptionId());
        
        // Step 1: Validate subscription exists and belongs to user
        Subscription subscription = validateSubscription(userId, request.getSubscriptionId());
        
        // Step 2: Create payment record
        Payment payment = Payment.builder()
                .userId(userId)
                .subscriptionId(request.getSubscriptionId())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .build();
        
        payment = paymentRepository.save(payment);
        
        try {
            // Step 3: Call external payment provider
            String transactionId = callPaymentProvider(request.getPaymentMethodToken(), request.getAmount());
            
            // Step 4: Update payment status
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setTransactionId(transactionId);
            paymentRepository.save(payment);
            
            // Step 5: DIRECTLY update subscription status to ACTIVE using repository
            updateSubscriptionStatus(subscription, SubscriptionStatus.ACTIVE);
            
            // Step 6: Publish payment success event
            publishPaymentSuccessEvent(userId, payment.getId().toString(), 
                                     request.getSubscriptionId(), request.getAmount());
            
            log.info("Payment successful for user: {}, paymentId: {}", userId, payment.getId());
            
            return buildPaymentResponse(payment);
            
        } catch (Exception e) {
            // Step 7: Handle payment failure
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            
            // Update subscription status to FAILED
            updateSubscriptionStatus(subscription, SubscriptionStatus.PAUSED);
            
            log.error("Payment failed for user: {}, subscription: {}", 
                     userId, request.getSubscriptionId(), e);
            
            throw new RuntimeException("Payment processing failed: " + e.getMessage());
        }
    }
    
    /**
     * Cancel payment and process refund with 1% deduction
     */
    @Transactional
    public PaymentResponse cancelPayment(String userId, String paymentId) {
        log.info("Processing cancellation for payment: {}, user: {}", paymentId, userId);
        
        // Step 1: Get the payment
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        
        // Validate user ownership
        if (!payment.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to cancel this payment");
        }
        
        // Check if payment can be refunded
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new RuntimeException("Only successful payments can be cancelled");
        }
        
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new RuntimeException("Payment already refunded");
        }
        
        // Get the subscription
        Subscription subscription = validateSubscription(userId, payment.getSubscriptionId());
        
        try {
            // Step 2: Calculate refund amount (99% of original amount)
            BigDecimal refundAmount = payment.getAmount()
                    .multiply(new BigDecimal("0.99"))
                    .setScale(2, RoundingMode.HALF_UP);
            
            // Step 3: Process refund with payment provider
            String refundTransactionId = callRefundProvider(payment.getTransactionId(), refundAmount);
            
            // Step 4: Update payment record
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundAmount(refundAmount);
            payment.setRefundTransactionId(refundTransactionId);
            paymentRepository.save(payment);
            
            // Step 5: DIRECTLY update subscription status to PENDING using repository
            updateSubscriptionStatus(subscription, SubscriptionStatus.PENDING);
            
            // Step 6: Publish refund event
            publishRefundEvent(userId, paymentId, payment.getSubscriptionId(), refundAmount);
            
            log.info("Payment cancelled and refund processed for payment: {}", paymentId);
            
            return buildPaymentResponse(payment);
            
        } catch (Exception e) {
            log.error("Refund failed for payment: {}", paymentId, e);
            throw new RuntimeException("Refund processing failed: " + e.getMessage());
        }
    }
    
    // ========== PRIVATE HELPER METHODS ==========
    
    /**
     * Validate subscription exists and belongs to user
     */
    private Subscription validateSubscription(String userId, String subscriptionId) {

        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));
        
        // Verify subscription belongs to user
        if (!subscription.getUserId().equals(userId)) {
            throw new RuntimeException("Subscription does not belong to user");
        }
        
        return subscription;
    }
    
    /**
     * DIRECTLY update subscription status using repository
     */
    private void updateSubscriptionStatus(Subscription subscription, SubscriptionStatus status) {
        try {
            subscription.setStatus(status);
            subscriptionRepository.save(subscription);
            
            log.info("Subscription {} status updated to {}", subscription.getId(), status);
            
        } catch (Exception e) {
            log.error("Failed to update subscription status for subscription: {}", 
                     subscription.getId(), e);
            // In a real application, you might want to throw or handle this differently
            // But for payment processing, we might want to continue even if subscription update fails
        }
    }
    
    private String callPaymentProvider(String paymentMethodToken, BigDecimal amount) {
        // Mock payment provider integration
        // In reality, this would call Stripe, PayPal, etc.
        log.info("Processing payment with token: {}, amount: {}", paymentMethodToken, amount);
        
        // Simulate payment processing
        try {
            Thread.sleep(500); // Simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Return a mock transaction ID
        return "txn_" + UUID.randomUUID().toString().substring(0, 16);
    }
    
    private String callRefundProvider(String transactionId, BigDecimal amount) {
        // Mock refund provider integration
        log.info("Processing refund for transaction: {}, amount: {}", transactionId, amount);
        
        // Simulate refund processing
        try {
            Thread.sleep(500); // Simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Return a mock refund transaction ID
        return "ref_" + UUID.randomUUID().toString().substring(0, 16);
    }
    
    private void publishPaymentSuccessEvent(String userId, String paymentId, 
                                          String subscriptionId, BigDecimal amount) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "PAYMENT_SUCCESS");
            event.put("userId", userId);
            event.put("paymentId", paymentId);
            event.put("subscriptionId", subscriptionId);
            event.put("amount", amount);
            event.put("timestamp", System.currentTimeMillis());
            
            kafkaTemplate.send("payment-events", userId, event);
            log.debug("Payment success event published for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to publish payment success event", e);
        }
    }
    
    private void publishRefundEvent(String userId, String paymentId, 
                                   String subscriptionId, BigDecimal refundAmount) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "PAYMENT_REFUNDED");
            event.put("userId", userId);
            event.put("paymentId", paymentId);
            event.put("subscriptionId", subscriptionId);
            event.put("refundAmount", refundAmount);
            event.put("timestamp", System.currentTimeMillis());
            
            kafkaTemplate.send("payment-events", userId, event);
            log.debug("Refund event published for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to publish refund event", e);
        }
    }
    
    private PaymentResponse buildPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .userId(payment.getUserId())
                .subscriptionId(payment.getSubscriptionId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .build();
    }
    
    // Getter methods remain the same...
    public PaymentResponse getPayment(String userId, String paymentId) {
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId))
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        
        if (!payment.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized to view this payment");
        }
        
        return buildPaymentResponse(payment);
    }
    
    public PaymentResponse[] getUserPayments(String userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(this::buildPaymentResponse)
                .toArray(PaymentResponse[]::new);
    }
}