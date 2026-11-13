package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.payment.CashfreeClient;
import com.commengine.security.CurrentUser;
import com.commengine.service.PaymentService;
import com.commengine.dto.CreatePaymentOrderRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final CashfreeClient cashfree;
    private final ObjectMapper mapper = new ObjectMapper();

    public PaymentController(PaymentService paymentService, CashfreeClient cashfree) {
        this.paymentService = paymentService;
        this.cashfree = cashfree;
    }

    /** The 10 coin packages: [{ id, coins, priceInr }] */
    @GetMapping("/packages")
    public Object packages() {
        return ApiResponse.ok(Map.of("packages", paymentService.packages()));
    }

    /** Body: { packageId }. Returns { orderId, paymentSessionId, mode, coins, amountInr }. */
    @PostMapping("/orders")
    public Object createOrder(@CurrentUser User user, @Valid @RequestBody CreatePaymentOrderRequest body) {
        return ApiResponse.ok(paymentService.createOrder(user, body.getPackageId()), "Order created", HttpStatus.CREATED);
    }

    /** App calls this when checkout closes. Verifies with Cashfree and credits coins if paid. */
    @GetMapping("/orders/{orderId}")
    public Object order(@CurrentUser User user, @PathVariable String orderId) {
        return ApiResponse.ok(paymentService.verifyForUser(user.getId(), orderId));
    }

    /** Cashfree server-to-server webhook. Public, but the HMAC signature is mandatory. */
    @PostMapping(value = "/webhook/cashfree", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<String> webhook(@RequestBody String rawBody,
                                          @RequestHeader(value = "x-webhook-signature", required = false) String signature,
                                          @RequestHeader(value = "x-webhook-timestamp", required = false) String timestamp) {
        if (!cashfree.verifyWebhookSignature(timestamp, rawBody, signature)) {
            log.warn("Cashfree webhook rejected: bad signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            String orderId = root.path("data").path("order").path("order_id").asText(null);
            if (orderId != null) paymentService.settleFromWebhook(orderId);
        } catch (Exception e) {
            // Non-2xx makes Cashfree retry, which is what we want for transient failures.
            log.error("Cashfree webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
        return ResponseEntity.ok("ok");
    }

    /** Landing page Cashfree redirects the in-app browser to. The app watches for this URL and closes the sheet. */
    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public String returnPage() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;color:#2A1B4D;background:#FFF7FC}</style>"
                + "</head><body><p>Checking your payment&hellip;</p></body></html>";
    }
}
