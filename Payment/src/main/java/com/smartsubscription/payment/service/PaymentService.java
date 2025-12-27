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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationService notificationService; // Add this line

    @Value("${payment.provider.url}")
    private String paymentProviderUrl;

    @Value("${payment.provider.api-key}")
    private String paymentProviderApiKey;

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

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

            // Step 6: Send notification
            sendPaymentSuccessNotification(userId, payment, subscription);

            // Step 7: Publish payment success event (optional - if using Kafka)
            publishPaymentSuccessEvent(userId, payment.getId().toString(),
                    request.getSubscriptionId(), request.getAmount());

            log.info("Payment successful for user: {}, paymentId: {}", userId, payment.getId());

            return buildPaymentResponse(payment);

        } catch (Exception e) {
            // Step 8: Handle payment failure
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            // Update subscription status to PAUSED
            updateSubscriptionStatus(subscription, SubscriptionStatus.PAUSED);

            // Send failure notification
            sendPaymentFailureNotification(userId, payment, subscription, e.getMessage());

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

            // Step 6: Send refund notification
            sendRefundNotification(userId, payment, subscription, refundAmount);

            // Step 7: Publish refund event (optional - if using Kafka)
            publishRefundEvent(userId, paymentId, payment.getSubscriptionId(), refundAmount);

            log.info("Payment cancelled and refund processed for payment: {}", paymentId);

            return buildPaymentResponse(payment);

        } catch (Exception e) {
            log.error("Refund failed for payment: {}", paymentId, e);
            throw new RuntimeException("Refund processing failed: " + e.getMessage());
        }
    }

    // ========== NOTIFICATION METHODS ==========

    private void sendPaymentSuccessNotification(String userId, Payment payment, Subscription subscription) {
        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("paymentId", payment.getId().toString());
            notificationData.put("amount", payment.getAmount());
            notificationData.put("subscriptionId", subscription.getId());
            notificationData.put("subscriptionName", subscription.getPlanName());

            notificationService.sendNotification(
                    userId,
                    "PAYMENT_SUCCESS",
                    "Payment Successful",
                    String.format("Your payment of $%.2f for %s has been processed successfully.",
                            payment.getAmount(), subscription.getPlanName()),
                    notificationData
            );
        } catch (Exception e) {
            log.error("Failed to send payment success notification: {}", e.getMessage());
            // Don't throw exception - notification failure shouldn't fail payment
        }
    }

    private void sendPaymentFailureNotification(String userId, Payment payment,
                                                Subscription subscription, String errorMessage) {
        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("paymentId", payment.getId().toString());
            notificationData.put("amount", payment.getAmount());
            notificationData.put("subscriptionId", subscription.getId());
            notificationData.put("error", errorMessage);

            notificationService.sendNotification(
                    userId,
                    "PAYMENT_FAILED",
                    "Payment Failed",
                    String.format("Your payment of $%.2f for %s has failed. Please try again.",
                            payment.getAmount(), subscription.getPlanName()),
                    notificationData
            );
        } catch (Exception e) {
            log.error("Failed to send payment failure notification: {}", e.getMessage());
        }
    }

    private void sendRefundNotification(String userId, Payment payment,
                                        Subscription subscription, BigDecimal refundAmount) {
        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("paymentId", payment.getId().toString());
            notificationData.put("originalAmount", payment.getAmount());
            notificationData.put("refundAmount", refundAmount);
            notificationData.put("subscriptionId", subscription.getId());

            notificationService.sendNotification(
                    userId,
                    "PAYMENT_REFUNDED",
                    "Refund Processed",
                    String.format("Your refund of $%.2f for %s has been processed. The amount will be credited to your account within 5-7 business days.",
                            refundAmount, subscription.getPlanName()),
                    notificationData
            );
        } catch (Exception e) {
            log.error("Failed to send refund notification: {}", e.getMessage());
        }
    }

    // ========== REST OF YOUR METHODS (KEEP THEM AS IS) ==========

    private Subscription validateSubscription(String userId, String subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        if (!subscription.getUserId().equals(userId)) {
            throw new RuntimeException("Subscription does not belong to user");
        }

        return subscription;
    }

    private void updateSubscriptionStatus(Subscription subscription, SubscriptionStatus status) {
        try {
            subscription.setStatus(status);
            subscriptionRepository.save(subscription);
            log.info("Subscription {} status updated to {}", subscription.getId(), status);
        } catch (Exception e) {
            log.error("Failed to update subscription status for subscription: {}",
                    subscription.getId(), e);
        }
    }

    private String callPaymentProvider(String paymentMethodToken, BigDecimal amount) {
        // Mock payment provider integration
        log.info("Processing payment with token: {}, amount: {}", paymentMethodToken, amount);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "txn_" + UUID.randomUUID().toString().substring(0, 16);
    }

    private String callRefundProvider(String transactionId, BigDecimal amount) {
        log.info("Processing refund for transaction: {}, amount: {}", transactionId, amount);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

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