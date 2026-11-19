package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.User;
import com.commengine.entity.UserFollow;
import com.commengine.repository.UserFollowRepository;
import com.commengine.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Follow / unfollow (one-way, instant) and the followers / following lists. */
@Service
public class FollowService {

    private final UserFollowRepository follows;
    private final UserRepository users;

    public FollowService(UserFollowRepository follows, UserRepository users) {
        this.follows = follows;
        this.users = users;
    }

    @Transactional
    public Map<String, Object> follow(User me, String targetId) {
        if (me.getId().equals(targetId)) throw ApiException.badRequest("You can't follow yourself");
        if (!users.existsById(targetId)) throw ApiException.notFound("User not found");
        if (!follows.existsByFollowerIdAndFollowingId(me.getId(), targetId)) {
            UserFollow f = new UserFollow();
            f.setFollowerId(me.getId());
            f.setFollowingId(targetId);
            try {
                follows.saveAndFlush(f);
            } catch (DataIntegrityViolationException ignored) {
                // a double tap raced us; already following is the result we wanted
            }
        }
        return status(me.getId(), targetId);
    }

    @Transactional
    public Map<String, Object> unfollow(User me, String targetId) {
        follows.unfollow(me.getId(), targetId);
        return status(me.getId(), targetId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(String myId, String otherId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("self", myId.equals(otherId));
        m.put("following", follows.existsByFollowerIdAndFollowingId(myId, otherId));
        m.put("followsMe", follows.existsByFollowerIdAndFollowingId(otherId, myId));
        m.put("followers", follows.countByFollowingId(otherId));
        m.put("followingCount", follows.countByFollowerId(otherId));
        return m;
    }

    /** People `userId` follows. */
    @Transactional(readOnly = true)
    public Map<String, Object> following(String userId) {
        return Map.of("users", load(follows.followingIds(userId)));
    }

    /** People who follow `userId`. */
    @Transactional(readOnly = true)
    public Map<String, Object> followers(String userId) {
        return Map.of("users", load(follows.followerIds(userId)));
    }

    /** Ids -> user cards, keeping the newest-first order. */
    private List<Map<String, Object>> load(List<String> ids) {
        Map<String, User> byId = new HashMap<>();
        for (User u : users.findAllById(ids)) byId.put(u.getId(), u);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String id : ids) {
            User u = byId.get(id);
            if (u != null) out.add(DtoMapper.userWithOnline(u));
        }
        return out;
    }
}
