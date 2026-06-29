package com.ayurveda.platform.tenant.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for Razorpay payment gateway integration.
 * Handles order creation and payment signature verification.
 */
@Service
@Profile("simple")
@Slf4j
public class RazorpayService {

    @Value("${app.razorpay.key-id}")
    private String keyId;

    @Value("${app.razorpay.key-secret}")
    private String keySecret;

    /**
     * Creates a Razorpay order for payment processing.
     *
     * @param amount   Order amount in INR (will be converted to paise)
     * @param currency Currency code (e.g. INR)
     * @param receipt  Receipt identifier (e.g. internal order number)
     * @return Map containing razorpayOrderId, amount, currency, keyId
     */
    public Map<String, Object> createOrder(BigDecimal amount, String currency, String receipt) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject params = new JSONObject();
            // Razorpay expects amount in smallest currency unit (paise for INR)
            params.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue());
            params.put("currency", currency);
            params.put("receipt", receipt);

            com.razorpay.Order razorpayOrder = client.orders.create(params);

            Map<String, Object> result = new HashMap<>();
            result.put("razorpayOrderId", razorpayOrder.get("id"));
            result.put("amount", razorpayOrder.get("amount"));
            result.put("currency", razorpayOrder.get("currency"));
            result.put("keyId", keyId);

            log.info("Razorpay order created: {}, amount: {}", razorpayOrder.get("id"), amount);
            return result;

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            throw new RuntimeException("Failed to create payment order: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the Razorpay payment signature to confirm payment authenticity.
     *
     * @param razorpayOrderId   Razorpay order ID from order creation
     * @param razorpayPaymentId Payment ID returned by Razorpay after payment
     * @param razorpaySignature Signature returned by Razorpay after payment
     * @return true if signature is valid, false otherwise
     */
    public boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", razorpayOrderId);
            attributes.put("razorpay_payment_id", razorpayPaymentId);
            attributes.put("razorpay_signature", razorpaySignature);

            Utils.verifyPaymentSignature(attributes, keySecret);
            log.info("Payment signature verified for order: {}, payment: {}", razorpayOrderId, razorpayPaymentId);
            return true;

        } catch (RazorpayException e) {
            log.warn("Payment signature verification failed for order: {}: {}", razorpayOrderId, e.getMessage());
            return false;
        }
    }
}
