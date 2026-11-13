package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.config.AppProperties;
import com.commengine.entity.PaymentOrder;
import com.commengine.entity.User;
import com.commengine.payment.CashfreeClient;
import com.commengine.entity.CoinPack;
import com.commengine.repository.PaymentOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final String TX_TYPE = "RECHARGE";

    private final PaymentOrderRepository orders;
    private final WalletService wallets;
    private final CashfreeClient cashfree;
    private final AppProperties props;
    private final TransactionTemplate tx;
    private final CoinPackService coinPacks;

    public PaymentService(PaymentOrderRepository orders, WalletService wallets, CashfreeClient cashfree, AppProperties props,
                          PlatformTransactionManager txManager, CoinPackService coinPacks) {
        this.coinPacks = coinPacks;
        this.tx = new TransactionTemplate(txManager);
        this.orders = orders;
        this.wallets = wallets;
        this.cashfree = cashfree;
        this.props = props;
    }

    public List<Map<String, Object>> packages() {
        return coinPacks.publicList();
    }

    /** Creates our order row + the Cashfree order, returns what the app needs to open checkout. */
    public Map<String, Object> createOrder(User user, String packageId) {
        CoinPack pack = coinPacks.requireBuyable(packageId);

        String orderId = "CE_" + UUID.randomUUID().toString().replace("-", "");
        String base = trimSlash(props.getCashfree().getPublicBaseUrl());
        String returnUrl = base + "/api/payments/return?order_id={order_id}";
        String notifyUrl = base + "/api/payments/webhook/cashfree";

        JsonNode cf = cashfree.createOrder(orderId, pack.getPriceInr(), user.getId(), phone10(user.getPhone()), returnUrl, notifyUrl);
        String sessionId = cf.path("payment_session_id").asText(null);
        if (sessionId == null) throw new ApiException("Could not start payment. Please try again.", org.springframework.http.HttpStatus.BAD_GATEWAY);

        PaymentOrder o = new PaymentOrder();
        o.setOrderId(orderId);
        o.setUserId(user.getId());
        o.setPackageId(pack.getId());
        o.setCoins(pack.getCoins());
        o.setAmountInr(pack.getPriceInr());
        o.setCfOrderId(cf.path("cf_order_id").asText(null));
        orders.save(o);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", orderId);
        out.put("paymentSessionId", sessionId);
        out.put("mode", cashfree.mode());
        out.put("coins", pack.getCoins());
        out.put("amountInr", pack.getPriceInr());
        return out;
    }

    /** Called by the app after checkout closes. Only the order's owner may check it. */
    public Map<String, Object> verifyForUser(String userId, String orderId) {
        PaymentOrder o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!o.getUserId().equals(userId)) throw ApiException.notFound("Order not found");
        return settle(orderId);
    }

    /** Called by the webhook. The body is NOT trusted: we re-fetch the order from Cashfree. */
    public void settleFromWebhook(String orderId) {
        if (orders.existsById(orderId)) settle(orderId);
    }

    /** Asks Cashfree for the real status and credits coins exactly once. Safe to call repeatedly. */
    public Map<String, Object> settle(String orderId) {
        JsonNode cf = cashfree.getOrder(orderId);
        String cfStatus = cf.path("order_status").asText("");
        double cfAmount = cf.path("order_amount").asDouble(-1);
        // HTTP call above is outside the DB transaction; only the short credit step holds the row lock.
        return tx.execute(status -> applyStatus(orderId, cfStatus, cfAmount));
    }

    private Map<String, Object> applyStatus(String orderId, String cfStatus, double cfAmount) {
        PaymentOrder o = orders.findForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));

        if ("PAID".equals(cfStatus) && !"PAID".equals(o.getStatus())) {
            if (Math.abs(cfAmount - o.getAmountInr()) > 0.001) {
                // Never credit when the paid amount differs from our package price. Flag for manual review.
                log.error("Amount mismatch for {}: expected {} got {}", orderId, o.getAmountInr(), cfAmount);
                o.setStatus("FAILED");
                orders.save(o);
                return result(o);
            }
            wallets.adjust(o.getUserId(), WalletService.COINS, o.getCoins(), TX_TYPE, o.getOrderId(),
                    o.getCoins() + " coins recharge (Rs " + o.getAmountInr() + ")");
            o.setStatus("PAID");
            o.setPaidAt(Instant.now());
            orders.save(o);
        } else if (("EXPIRED".equals(cfStatus) || "TERMINATED".equals(cfStatus) || "TERMINATION_REQUESTED".equals(cfStatus))
                && "CREATED".equals(o.getStatus())) {
            o.setStatus("FAILED");
            orders.save(o);
        }

        return result(o);
    }

    private Map<String, Object> result(PaymentOrder o) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", o.getOrderId());
        out.put("status", o.getStatus());          // CREATED (pending) | PAID | FAILED
        out.put("coins", o.getCoins());
        out.put("wallet", WalletService.toMap(wallets.ensure(o.getUserId())));
        return out;
    }

    /** Cashfree wants a 10-digit mobile number. */
    static String phone10(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private static String trimSlash(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
