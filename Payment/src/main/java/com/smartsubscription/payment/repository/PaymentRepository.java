package com.smartsubscription.payment.repository;
import com.smartsubscription.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
    List<Payment> findByUserId(String userId);
    
    List<Payment> findBySubscriptionId(String subscriptionId);
    
    List<Payment> findByUserIdAndStatus(String userId, String status);
}