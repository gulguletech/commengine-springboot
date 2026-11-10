package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.dto.SendGiftRequest;
import com.commengine.entity.AudioRoom;
import com.commengine.entity.RoomStatus;
import com.commengine.entity.User;
import com.commengine.entity.VideoRoom;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.repository.UserRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.socket.AudioRoomLiveState;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Gifts: sender pays COINS, receiver earns BEANS (70% of the price), everyone in the room sees it. */
@Service
public class GiftService {

    /** id, name, emoji, price in coins */
    public record Gift(String id, String name, String emoji, long price) {}

    public static final List<Gift> CATALOG = List.of(
            new Gift("rose", "Rose", "🌹", 10),
            new Gift("heart", "Heart", "💖", 20),
            new Gift("icecream", "Ice Cream", "🍦", 50),
            new Gift("teddy", "Teddy", "🧸", 100),
            new Gift("cake", "Cake", "🎂", 200),
            new Gift("rocket", "Rocket", "🚀", 500),
            new Gift("crown", "Crown", "👑", 1000),
            new Gift("diamond", "Diamond", "💎", 2000)
    );

    private static final int BEAN_PERCENT = 70;

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final AudioRoomRepository audioRoomRepository;
    private final VideoRoomRepository videoRoomRepository;
    private final AudioRoomLiveState audioState;
    private final SocketIOServer server;

    public GiftService(WalletService walletService, UserRepository userRepository,
                       AudioRoomRepository audioRoomRepository, VideoRoomRepository videoRoomRepository,
                       AudioRoomLiveState audioState, SocketIOServer server) {
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.audioRoomRepository = audioRoomRepository;
        this.videoRoomRepository = videoRoomRepository;
        this.audioState = audioState;
        this.server = server;
    }

    public List<Map<String, Object>> catalog() {
        return CATALOG.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.id());
            m.put("name", g.name());
            m.put("emoji", g.emoji());
            m.put("price", g.price());
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> send(User sender, SendGiftRequest req) {
        Gift gift = CATALOG.stream().filter(g -> g.id().equals(req.getGiftId())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown gift"));
        if (sender.getId().equals(req.getReceiverId())) {
            throw ApiException.badRequest("You can't send a gift to yourself");
        }
        User receiver = userRepository.findById(req.getReceiverId())
                .orElseThrow(() -> ApiException.notFound("Receiver not found"));

        String channel;
        boolean audio = "audio".equals(req.getRoomKind());
        if (audio) {
            AudioRoom room = audioRoomRepository.findById(req.getRoomId()).orElse(null);
            if (room == null || room.getStatus() != RoomStatus.ACTIVE) throw ApiException.notFound("Room not found or ended");
            if (!audioState.getMembers(room.getId()).contains(sender.getId())) {
                throw ApiException.forbidden("Join the room to send gifts");
            }
            boolean receiverIsHost = room.getHost().getId().equals(receiver.getId());
            boolean receiverSeated = audioState.findSeatIndexByUser(room.getId(), receiver.getId()) != -1;
            if (!receiverIsHost && !receiverSeated) throw ApiException.badRequest("Gifts can only go to the host or people on a seat");
            channel = "audio-room:" + room.getId();
        } else if ("video".equals(req.getRoomKind())) {
            VideoRoom room = videoRoomRepository.findById(req.getRoomId()).orElse(null);
            if (room == null || room.getStatus() != RoomStatus.ACTIVE) throw ApiException.notFound("Live room not found or ended");
            if (!room.getHost().getId().equals(receiver.getId())) throw ApiException.badRequest("Gifts in a live stream go to the host");
            channel = "video-room:" + room.getId();
        } else {
            throw ApiException.badRequest("roomKind must be 'audio' or 'video'");
        }

        int count = req.getCount();
        long total = gift.price() * count;
        long beans = Math.max(1, total * BEAN_PERCENT / 100);
        String ref = UUID.randomUUID().toString();

        walletService.adjust(sender.getId(), WalletService.COINS, -total, "GIFT_SENT", ref,
                "Sent " + count + "x " + gift.name() + " to " + receiver.getName());
        walletService.adjust(receiver.getId(), WalletService.BEANS, beans, "GIFT_RECEIVED", ref,
                "Got " + count + "x " + gift.name() + " from " + sender.getName());

        Map<String, Object> giftJson = new LinkedHashMap<>();
        giftJson.put("id", gift.id());
        giftJson.put("name", gift.name());
        giftJson.put("emoji", gift.emoji());
        giftJson.put("price", gift.price());

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", ref);
        event.put("roomKind", req.getRoomKind());
        event.put("roomId", req.getRoomId());
        event.put("gift", giftJson);
        event.put("count", count);
        event.put("sender", DtoMapper.userBrief(sender));
        event.put("receiver", DtoMapper.userBrief(receiver));
        server.getRoomOperations(channel).sendEvent("gift:received", event);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("wallet", walletService.getBalance(sender.getId()));
        return res;
    }
}
