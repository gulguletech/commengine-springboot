package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.GiftLog;
import com.commengine.entity.User;
import com.commengine.repository.GiftLogRepository;
import com.commengine.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Rankings built from gifts.
 *  - star:         people who RECEIVED the most gift value
 *  - contribution: people who SENT the most gift value
 * for today / this week (Monday start) / this month. Days change at midnight India time, like the daily reward.
 * Gifts people send to themselves are not counted.
 */
@Service
public class RankingService {

    private static final int TOP = 50;

    private final GiftLogRepository logs;
    private final UserRepository users;

    public RankingService(GiftLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    /** Called from the gift transaction. */
    @Transactional
    public void record(String senderId, String receiverId, long coins) {
        if (coins <= 0 || senderId.equals(receiverId)) return;
        GiftLog g = new GiftLog();
        g.setSenderId(senderId);
        g.setReceiverId(receiverId);
        g.setCoins(coins);
        g.setCreatedAt(Instant.now());
        logs.save(g);
    }

    private static Instant since(String period) {
        LocalDate today = LocalDate.now(DailyRewardService.ZONE);
        LocalDate start = switch (period) {
            case "today" -> today;
            case "week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> today.withDayOfMonth(1);
            default -> throw ApiException.badRequest("period must be today, week or month");
        };
        return start.atStartOfDay(DailyRewardService.ZONE).toInstant();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ranking(User me, String type, String period) {
        boolean star;
        if ("star".equals(type)) star = true;
        else if ("contribution".equals(type)) star = false;
        else throw ApiException.badRequest("type must be star or contribution");

        Instant since = since(period);
        List<Object[]> rows = star
                ? logs.topReceivers(since, PageRequest.of(0, TOP))
                : logs.topSenders(since, PageRequest.of(0, TOP));

        List<String> ids = new ArrayList<>();
        for (Object[] r : rows) ids.add((String) r[0]);
        Map<String, User> byId = new HashMap<>();
        for (User u : users.findAllById(ids)) byId.put(u.getId(), u);

        List<Map<String, Object>> top = new ArrayList<>();
        int rank = 0;
        for (Object[] r : rows) {
            User u = byId.get((String) r[0]);
            rank++;
            if (u == null || u.isBanned()) { rank--; continue; } // removed / banned accounts don't appear
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank);
            m.put("user", DtoMapper.userBrief(u));
            m.put("coins", ((Number) r[1]).longValue());
            top.add(m);
        }

        long mine = star ? logs.receivedSince(me.getId(), since) : logs.sentSince(me.getId(), since);
        Integer myRank = null;
        if (mine > 0) {
            long above = star ? logs.receiversAbove(since, mine) : logs.sendersAbove(since, mine);
            myRank = (int) Math.min(above + 1, Integer.MAX_VALUE);
        }
        Map<String, Object> meJson = new LinkedHashMap<>();
        meJson.put("rank", myRank);       // null = no gifts in this period yet
        meJson.put("coins", mine);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("type", type);
        res.put("period", period);
        res.put("top", top);
        res.put("me", meJson);
        return res;
    }
}
