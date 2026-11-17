package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.User;
import com.commengine.entity.VipPlanConfig;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.repository.UserRepository;
import com.commengine.repository.VipPlanRepository;
import com.commengine.socket.SocketUserResolver;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * VIP membership, bought with coins. Five lengths (1 day, 7 days, 1 month, 6 months, 1 year); the admin sets each price.
 * Buying again adds the days on top of what is left. While a user is VIP they can use every store item the admin marked
 * VIP (frame, bubble, entry effect, room background) for free, and upload their own room background.
 */
@Service
public class VipService {

    /** The lengths on sale, in days, and how the app names them. */
    private static final int[] PLAN_DAYS = {1, 7, 30, 180, 365};
    private static final String[] PLAN_LABELS = {"1 Day", "7 Days", "1 Month", "6 Months", "1 Year"};
    private static final long[] DEFAULT_PRICE = {99, 499, 1_499, 7_999, 12_999};
    private static final long MAX_PRICE = 100_000_000L;
    private static final long MAX_HOLD_DAYS = 730;
    private static final long MAX_BG_BYTES = 5_000_000L;

    private final VipPlanRepository plans;
    private final UserRepository users;
    private final AudioRoomRepository rooms;
    private final WalletService wallets;
    private final StoreService store;
    private final CloudinaryService cloudinary;
    private final SocketIOServer server;
    private final SocketUserResolver socketUserResolver;

    public VipService(VipPlanRepository plans, UserRepository users, AudioRoomRepository rooms, WalletService wallets,
                      StoreService store, CloudinaryService cloudinary, SocketIOServer server,
                      SocketUserResolver socketUserResolver) {
        this.plans = plans;
        this.users = users;
        this.rooms = rooms;
        this.wallets = wallets;
        this.store = store;
        this.cloudinary = cloudinary;
        this.server = server;
        this.socketUserResolver = socketUserResolver;
    }

    // ------------------------------------------------------------------ plans

    /** Always the 5 plans: what the admin saved, or the default price if not touched yet. */
    private List<VipPlanConfig> planList() {
        Map<Integer, VipPlanConfig> by = new HashMap<>();
        for (VipPlanConfig c : plans.findAll()) by.put(c.getDays(), c);
        List<VipPlanConfig> out = new ArrayList<>();
        for (int i = 0; i < PLAN_DAYS.length; i++) {
            VipPlanConfig c = by.get(PLAN_DAYS[i]);
            if (c == null) {
                c = new VipPlanConfig();
                c.setDays(PLAN_DAYS[i]);
                c.setPriceCoins(DEFAULT_PRICE[i]);
                c.setActive(true);
            }
            out.add(c);
        }
        return out;
    }

    private static String label(int days) {
        for (int i = 0; i < PLAN_DAYS.length; i++) if (PLAN_DAYS[i] == days) return PLAN_LABELS[i];
        return days + " Days";
    }

    private static Map<String, Object> planJson(VipPlanConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", c.getDays());
        m.put("label", label(c.getDays()));
        m.put("priceCoins", c.getPriceCoins());
        m.put("active", c.isActive());
        return m;
    }

    // ------------------------------------------------------------------ app side

    @Transactional
    public Map<String, Object> status(User user) {
        User me = users.findById(user.getId()).orElseThrow(() -> ApiException.notFound("User not found"));
        return buildStatus(me);
    }

    private Map<String, Object> buildStatus(User me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("isVip", me.isVip());
        m.put("expiresAt", me.isVip() ? me.getVipExpiresAt().toString() : null);
        List<Map<String, Object>> list = new ArrayList<>();
        for (VipPlanConfig c : planList()) if (c.isActive()) list.add(planJson(c));
        m.put("plans", list);
        m.put("wallet", wallets.getBalance(me.getId()));
        Map<String, Object> bg = null;
        if (me.getCustomBgUrl() != null) {
            bg = new LinkedHashMap<>();
            bg.put("imageUrl", StoreService.bgFull(me.getCustomBgUrl()));
            bg.put("thumbUrl", StoreService.bgThumb(me.getCustomBgUrl()));
        }
        m.put("customBackground", bg);
        return m;
    }

    @Transactional
    public Map<String, Object> buy(User user, int days) {
        VipPlanConfig plan = planList().stream().filter(p -> p.getDays() == days && p.isActive()).findFirst()
                .orElseThrow(() -> ApiException.badRequest("That VIP plan is not available"));

        User me = users.lockById(user.getId()).orElseThrow(() -> ApiException.notFound("User not found"));
        Instant now = Instant.now();
        Instant base = me.isVip() ? me.getVipExpiresAt() : now;
        Instant end = base.plus(days, ChronoUnit.DAYS);
        if (java.time.Duration.between(now, end).toDays() > MAX_HOLD_DAYS) {
            throw ApiException.unprocessable("You can hold at most " + MAX_HOLD_DAYS + " days of VIP at a time");
        }

        wallets.adjust(me.getId(), WalletService.COINS, -plan.getPriceCoins(), "VIP_BUY", UUID.randomUUID().toString(),
                "VIP " + label(days));
        me.setVipExpiresAt(end);
        users.save(me);
        store.extendVipBenefits(me.getId(), end); // worn VIP items + room backgrounds now last as long as the membership
        socketUserResolver.refreshClients(server, me);
        return buildStatus(me);
    }

    /** VIP member uploads the picture they want as their room background. Reused for any of their rooms. */
    @Transactional
    public Map<String, Object> uploadBackground(User user, MultipartFile image) {
        User me = users.lockById(user.getId()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!me.isVip()) throw ApiException.forbidden("Uploading your own background is for VIP members");
        if (image == null || image.isEmpty()) throw ApiException.unprocessable("Choose a picture");
        if (image.getSize() > MAX_BG_BYTES) throw ApiException.unprocessable("Picture must be under 5 MB");
        byte[] head;
        try {
            byte[] all = image.getBytes();
            head = Arrays.copyOf(all, Math.min(all.length, 12));
        } catch (IOException e) {
            throw ApiException.unprocessable("Could not read the picture");
        }
        if (!looksLikeImage(head)) throw ApiException.unprocessable("Use a JPG, PNG or WebP picture");

        CloudinaryService.MediaResult up = cloudinary.uploadMedia(image, "commengine/vip-backgrounds", "image");
        String oldId = me.getCustomBgPublicId();
        me.setCustomBgUrl(up.url());
        me.setCustomBgPublicId(up.publicId());
        users.save(me);
        cloudinary.delete(oldId, "image");

        // rooms already using the custom background show the new picture
        rooms.refreshCustomBackground(me.getId(), StoreService.bgFull(up.url()), StoreService.bgThumb(up.url()));
        return buildStatus(me);
    }

    private static boolean looksLikeImage(byte[] h) {
        if (h.length < 12) return false;
        boolean png = (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
        boolean jpg = (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
        boolean webp = h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
        return png || jpg || webp;
    }

    // ------------------------------------------------------------------ admin side

    @Transactional(readOnly = true)
    public Map<String, Object> adminList() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plans", planList().stream().map(VipService::planJson).toList());
        m.put("activeMembers", users.countByVipExpiresAtAfter(Instant.now()));
        return m;
    }

    @Transactional
    public Map<String, Object> adminSet(int days, Long priceCoins, Boolean active) {
        if (Arrays.stream(PLAN_DAYS).noneMatch(d -> d == days)) throw ApiException.badRequest("Unknown VIP plan");
        VipPlanConfig c = plans.findById(days).orElseGet(() -> planList().stream().filter(p -> p.getDays() == days).findFirst().orElseThrow());
        if (priceCoins != null) {
            if (priceCoins < 1 || priceCoins > MAX_PRICE) throw ApiException.unprocessable("Price must be between 1 and " + MAX_PRICE + " coins");
            c.setPriceCoins(priceCoins);
        }
        if (active != null) c.setActive(active);
        c.setUpdatedAt(Instant.now());
        plans.save(c);
        return planJson(c);
    }
}
