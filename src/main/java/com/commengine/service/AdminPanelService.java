package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.Admin;
import com.commengine.entity.AudioRoom;
import com.commengine.entity.RoomStatus;
import com.commengine.entity.User;
import com.commengine.entity.VideoRoom;
import com.commengine.entity.Wallet;
import com.commengine.entity.WalletTransaction;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.repository.UserRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.repository.WalletRepository;
import com.commengine.repository.WalletTransactionRepository;
import com.commengine.socket.AudioRoomLiveState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Everything the admin panel reads / does. Controllers stay thin and just call this. */
@Service
public class AdminPanelService {

    private static final long MAX_ADJUST = 100_000_000L;

    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletTransactionRepository txs;
    private final WalletService walletService;
    private final AudioRoomRepository audioRooms;
    private final VideoRoomRepository videoRooms;
    private final AudioRoomService audioRoomService;
    private final AudioRoomLiveState audioState;
    private final RoomPresenceService presence;

    public AdminPanelService(UserRepository users, WalletRepository wallets, WalletTransactionRepository txs,
                             WalletService walletService, AudioRoomRepository audioRooms, VideoRoomRepository videoRooms,
                             AudioRoomService audioRoomService, AudioRoomLiveState audioState, RoomPresenceService presence) {
        this.users = users;
        this.wallets = wallets;
        this.txs = txs;
        this.walletService = walletService;
        this.audioRooms = audioRooms;
        this.videoRooms = videoRooms;
        this.audioRoomService = audioRoomService;
        this.audioState = audioState;
        this.presence = presence;
    }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Instant now = Instant.now();
        Instant since24h = now.minus(24, ChronoUnit.HOURS);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalUsers", users.count());
        m.put("onlineUsers", users.countOnline());
        m.put("bannedUsers", users.countByBannedTrue());
        m.put("newUsers24h", users.countByCreatedAtBetween(since24h, now));
        m.put("activeAudioRooms", audioRooms.countByStatus(RoomStatus.ACTIVE));
        m.put("activeLiveRooms", videoRooms.countByStatus(RoomStatus.ACTIVE));
        m.put("coinsInCirculation", wallets.sumCoins());
        m.put("beansInCirculation", wallets.sumBeans());
        m.put("gifts24h", txs.countByTypeAndCreatedAtAfter("GIFT_SENT", since24h));
        m.put("giftCoins24h", txs.sumSpentSince("GIFT_SENT", since24h));

        // sign-ups per day for the last 7 days (UTC days), oldest first
        List<Map<String, Object>> signups = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Instant from = d.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant to = d.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("count", users.countByCreatedAtBetween(from, to));
            signups.add(row);
        }
        m.put("signups7d", signups);
        return m;
    }

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public Map<String, Object> listUsers(String q, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 50);
        Page<User> result = users.adminSearch(q == null ? "" : q.trim(), PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<String, Wallet> walletByUser = wallets.findAllById(result.getContent().stream().map(User::getId).toList())
                .stream().collect(Collectors.toMap(Wallet::getUserId, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> items = new ArrayList<>();
        for (User u : result.getContent()) {
            Map<String, Object> row = userRow(u);
            Wallet w = walletByUser.get(u.getId());
            row.put("coins", w == null ? 0 : w.getCoins());
            row.put("beans", w == null ? 0 : w.getBeans());
            items.add(row);
        }
        return page(items, result.getTotalElements(), p, s);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userDetail(String id) {
        User u = findUser(id);
        Map<String, Object> m = userRow(u);
        m.put("bio", u.getBio());
        m.put("updatedAt", u.getUpdatedAt());
        m.put("bannedAt", u.getBannedAt());
        m.put("banReason", u.getBanReason());
        m.put("wallet", walletService.getBalance(id));
        m.put("transactions", walletService.transactions(id, 25));
        return m;
    }

    @Transactional
    public Map<String, Object> adjustWallet(Admin admin, String userId, String currency, long amount, String note) {
        findUser(userId);
        String cur = currency == null ? "" : currency.trim().toUpperCase();
        if (!cur.equals(WalletService.COINS) && !cur.equals(WalletService.BEANS)) {
            throw ApiException.badRequest("currency must be COINS or BEANS");
        }
        if (amount == 0) throw ApiException.badRequest("amount can't be 0");
        if (Math.abs(amount) > MAX_ADJUST) throw ApiException.badRequest("amount is too large");

        String text = "Admin " + admin.getUsername() + (note == null || note.isBlank() ? "" : ": " + note.trim());
        if (text.length() > 190) text = text.substring(0, 190);
        Wallet w = walletService.adjust(userId, cur, amount, "ADMIN_ADJUST", null, text);
        return WalletService.toMap(w);
    }

    @Transactional
    public void setBanned(String userId, boolean banned, String reason) {
        User u = findUser(userId);
        u.setBanned(banned);
        u.setBannedAt(banned ? Instant.now() : null);
        String r = (reason == null || reason.isBlank()) ? null : reason.trim();
        u.setBanReason(banned && r != null ? (r.length() > 190 ? r.substring(0, 190) : r) : null);
        if (banned) u.setOnline(false);
        users.save(u);
        if (banned) presence.kickUser(userId); // out of every room + live connections dropped
    }

    // ------------------------------------------------------------------ rooms

    @Transactional(readOnly = true)
    public Map<String, Object> listRooms() {
        List<Map<String, Object>> audio = new ArrayList<>();
        for (AudioRoom r : audioRooms.findVisibleRooms(RoomStatus.ACTIVE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("type", r.getType());
            m.put("createdAt", r.getCreatedAt());
            m.put("host", brief(r.getHost()));
            Set<String> members = audioState.peekMembers(r.getId());
            m.put("members", members == null ? 0 : members.size());
            int seated = 0;
            for (var seat : audioState.getSeats(r.getId())) if (seat != null) seated++;
            m.put("seated", seated);
            audio.add(m);
        }
        List<Map<String, Object>> live = new ArrayList<>();
        for (VideoRoom r : videoRooms.findByStatusOrderByCreatedAtDesc(RoomStatus.ACTIVE)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("createdAt", r.getCreatedAt());
            m.put("host", brief(r.getHost()));
            m.put("viewers", r.getViewerCount());
            live.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("audio", audio);
        out.put("live", live);
        return out;
    }

    @Transactional
    public void endAudioRoom(String id) {
        audioRoomService.adminEndRoom(id);
    }

    @Transactional
    public void endLiveRoom(String id) {
        VideoRoom room = videoRooms.findById(id).orElseThrow(() -> ApiException.notFound("Live room not found"));
        presence.endVideoRoom(room);
    }

    // ------------------------------------------------------------------ transactions

    @Transactional(readOnly = true)
    public Map<String, Object> listTransactions(String type, String userId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 100);
        PageRequest pr = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WalletTransaction> result;
        if (userId != null && !userId.isBlank()) result = txs.findByUserId(userId.trim(), pr);
        else if (type != null && !type.isBlank()) result = txs.findByType(type.trim().toUpperCase(), pr);
        else result = txs.findAll(pr);

        Map<String, User> byId = users.findAllById(result.getContent().stream().map(WalletTransaction::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<Map<String, Object>> items = new ArrayList<>();
        for (WalletTransaction t : result.getContent()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("userId", t.getUserId());
            User u = byId.get(t.getUserId());
            m.put("userName", u == null ? "(deleted user)" : u.getName());
            m.put("currency", t.getCurrency());
            m.put("amount", t.getAmount());
            m.put("balanceAfter", t.getBalanceAfter());
            m.put("type", t.getType());
            m.put("note", t.getNote());
            m.put("createdAt", t.getCreatedAt());
            items.add(m);
        }
        return page(items, result.getTotalElements(), p, s);
    }

    // ------------------------------------------------------------------ helpers

    private User findUser(String id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static Map<String, Object> userRow(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("phone", u.getPhone());
        m.put("age", u.getAge());
        m.put("gender", u.getGender());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("isVerified", u.isVerified());
        m.put("isOnline", u.isOnline());
        m.put("banned", u.isBanned());
        m.put("createdAt", u.getCreatedAt());
        m.put("lastSeen", u.getLastSeen());
        return m;
    }

    private static Map<String, Object> brief(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (u == null) return m;
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("avatarUrl", u.getAvatarUrl());
        return m;
    }

    private static Map<String, Object> page(List<Map<String, Object>> items, long total, int page, int size) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", items);
        m.put("total", total);
        m.put("page", page);
        m.put("size", size);
        m.put("pages", (int) Math.ceil(total / (double) size));
        return m;
    }
}
