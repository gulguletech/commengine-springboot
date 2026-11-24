package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.*;
import com.commengine.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything shown on someone's profile card: who they are, their level, stats and whether they are live now. */
@Service
public class UserDetailsService {

    private final UserRepository users;
    private final LevelService levels;
    private final UserLevelRepository userLevels;
    private final UserFollowRepository follows;
    private final GiftLogRepository giftLogs;
    private final AudioRoomRepository audioRooms;
    private final VideoRoomRepository videoRooms;
    private final BadgeService badgeService;

    public UserDetailsService(UserRepository users, LevelService levels, UserLevelRepository userLevels,
                              UserFollowRepository follows, GiftLogRepository giftLogs,
                              AudioRoomRepository audioRooms, VideoRoomRepository videoRooms,
                              BadgeService badgeService) {
        this.badgeService = badgeService;
        this.users = users;
        this.levels = levels;
        this.userLevels = userLevels;
        this.follows = follows;
        this.giftLogs = giftLogs;
        this.audioRooms = audioRooms;
        this.videoRooms = videoRooms;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> details(String id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));

        Map<String, Object> user = DtoMapper.userProfile(u);
        user.put("age", u.getAge());
        user.put("isVip", u.isVip());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", user);
        m.put("level", levels.levelOf(id));
        m.put("receivedCoins", userLevels.findById(id).map(UserLevel::getReceivedCoins).orElse(0L));
        m.put("sentCoins", giftLogs.sentSince(id, Instant.EPOCH));
        m.put("followers", follows.countByFollowingId(id));
        m.put("following", follows.countByFollowerId(id));
        m.put("live", liveNow(id));
        m.put("badges", badgeService.activeOf(id));
        return m;
    }

    /** The room this person is hosting right now (public audio rooms and live streams), or null. */
    private Map<String, Object> liveNow(String hostId) {
        List<VideoRoom> video = videoRooms.findByHostIdAndStatus(hostId, RoomStatus.ACTIVE);
        if (!video.isEmpty()) {
            VideoRoom r = video.get(0);
            return Map.of("kind", "video", "id", r.getId(), "title", r.getTitle());
        }
        AudioRoom a = audioRooms.findFirstByHostIdAndStatusAndOnlineTrueOrderByCreatedAtDesc(hostId, RoomStatus.ACTIVE).orElse(null);
        if (a != null && a.getType() == RoomType.PUBLIC) { // private rooms need a passcode, so they are not advertised
            return Map.of("kind", "audio", "id", a.getId(), "title", a.getTitle());
        }
        return null;
    }
}
