package com.commengine.payment;

import com.commengine.common.ApiException;
import com.commengine.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thin wrapper over Cashfree Payment Gateway REST API (version 2023-08-01). */
@Component
public class CashfreeClient {

    private static final Logger log = LoggerFactory.getLogger(CashfreeClient.class);

    private final AppProperties.Cashfree cfg;
    private final RestClient http;

    public CashfreeClient(AppProperties props) {
        this.cfg = props.getCashfree();
        String base = cfg.isProduction() ? "https://api.cashfree.com/pg" : "https://sandbox.cashfree.com/pg";
        this.http = RestClient.builder()
                .baseUrl(base)
                .defaultHeader("x-api-version", cfg.getApiVersion())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /** "production" or "sandbox" - what Cashfree's JS SDK expects as `mode`. */
    public String mode() {
        return cfg.isProduction() ? "production" : "sandbox";
    }

    private void requireConfigured() {
        if (blank(cfg.getAppId()) || blank(cfg.getSecretKey())) {
            throw new ApiException("Payments are not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** POST /orders. Returns the Cashfree response (contains payment_session_id, cf_order_id). */
    public JsonNode createOrder(String orderId, long amountInr, String customerId, String phone10,
                                String returnUrl, String notifyUrl) {
        requireConfigured();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("return_url", returnUrl);
        if (!blank(notifyUrl)) meta.put("notify_url", notifyUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", orderId);
        body.put("order_amount", amountInr);          // whole rupees, e.g. 79
        body.put("order_currency", "INR");
        body.put("customer_details", Map.of("customer_id", customerId, "customer_phone", phone10));
        body.put("order_meta", meta);

        try {
            return http.post().uri("/orders")
                    .header("x-client-id", cfg.getAppId())
                    .header("x-client-secret", cfg.getSecretKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("Cashfree create order failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException("Could not start payment. Please try again.", HttpStatus.BAD_GATEWAY);
        }
    }

    /** GET /orders/{id}. Authoritative payment status straight from Cashfree. */
    public JsonNode getOrder(String orderId) {
        requireConfigured();
        try {
            return http.get().uri("/orders/{id}", orderId)
                    .header("x-client-id", cfg.getAppId())
                    .header("x-client-secret", cfg.getSecretKey())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("Cashfree get order failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException("Could not verify payment. Please try again.", HttpStatus.BAD_GATEWAY);
        }
    }

    /** signature = Base64( HMAC-SHA256( timestamp + rawBody, secretKey ) ) */
    public boolean verifyWebhookSignature(String timestamp, String rawBody, String signature) {
        if (blank(cfg.getSecretKey()) || blank(timestamp) || blank(signature) || rawBody == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cfg.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = Base64.getEncoder().encode(mac.doFinal((timestamp + rawBody).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
