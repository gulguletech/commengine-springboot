package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.dto.SendGiftRequest;
import com.commengine.entity.AudioRoom;
import com.commengine.entity.Gift;
import com.commengine.entity.RoomStatus;
import com.commengine.entity.User;
import com.commengine.entity.VideoRoom;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.repository.GiftRepository;
import com.commengine.repository.UserRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.socket.AudioRoomLiveState;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Gifts: sender pays COINS, receiver earns BEANS (70% of the price), everyone in the room sees it. */
@Service
public class GiftService {

    private static final int BEAN_PERCENT = 70;

    /** Limits for what the admin can upload. */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 10L * 1024 * 1024;
    private static final double MAX_VIDEO_SECONDS = 5.5; // "5 seconds" with a little slack for container rounding
    private static final long MAX_PRICE = 1_000_000;

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final AudioRoomRepository audioRoomRepository;
    private final VideoRoomRepository videoRoomRepository;
    private final AudioRoomLiveState audioState;
    private final SocketIOServer server;
    private final GiftRepository giftRepository;
    private final CloudinaryService cloudinaryService;

    public GiftService(WalletService walletService, UserRepository userRepository,
                       AudioRoomRepository audioRoomRepository, VideoRoomRepository videoRoomRepository,
                       AudioRoomLiveState audioState, SocketIOServer server,
                       GiftRepository giftRepository, CloudinaryService cloudinaryService) {
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.audioRoomRepository = audioRoomRepository;
        this.videoRoomRepository = videoRoomRepository;
        this.audioState = audioState;
        this.server = server;
        this.giftRepository = giftRepository;
        this.cloudinaryService = cloudinaryService;
    }

    // ------------------------------------------------------------------ app side

    /** The gift shelf the app shows: active gifts only. */
    public List<Map<String, Object>> catalog() {
        return giftRepository.findByActiveTrueOrderBySortOrderAscPriceAscCreatedAtAsc().stream()
                .map(GiftService::toJson).toList();
    }

    /** Shape sent to the app (shelf) and inside the gift:received socket event. */
    private static Map<String, Object> toJson(Gift g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("name", g.getName());
        m.put("emoji", cleanText(g.getEmoji()));
        m.put("imageUrl", cleanUrl(g.getImageUrl()));
        m.put("videoUrl", cleanUrl(g.getVideoUrl()));
        m.put("price", g.getPrice());
        return m;
    }

    /** Rows saved with a literal "undefined"/"null" (a classic form-data slip) must not reach the app as real values. */
    private static String cleanText(String v) {
        if (v == null) return null;
        String t = v.trim();
        return (t.isEmpty() || t.equalsIgnoreCase("undefined") || t.equalsIgnoreCase("null")) ? null : t;
    }

    private static String cleanUrl(String v) {
        String t = cleanText(v);
        return (t != null && t.regionMatches(true, 0, "http", 0, 4)) ? t : null;
    }

    // ------------------------------------------------------------------ admin side

    public List<Map<String, Object>> adminList() {
        return giftRepository.findAllByOrderBySortOrderAscPriceAscCreatedAtAsc().stream().map(g -> {
            Map<String, Object> m = toJson(g);
            m.put("active", g.isActive());
            m.put("sortOrder", g.getSortOrder());
            m.put("createdAt", g.getCreatedAt());
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, Object> adminCreate(String name, long price, Integer sortOrder, MultipartFile image, MultipartFile video) {
        String cleanName = cleanName(name);
        checkPrice(price);
        if (image == null || image.isEmpty()) throw ApiException.unprocessable("Gift image is required");

        Gift g = new Gift();
        g.setName(cleanName);
        g.setPrice(price);
        g.setSortOrder(sortOrder == null ? 0 : sortOrder);
        attachImage(g, image);
        try {
            if (video != null && !video.isEmpty()) attachVideo(g, video);
        } catch (RuntimeException e) {
            cloudinaryService.delete(g.getImagePublicId(), "image"); // don't leave the image orphaned
            throw e;
        }
        giftRepository.save(g);
        return adminRow(g);
    }

    @Transactional
    public Map<String, Object> adminUpdate(String id, String name, Long price, Integer sortOrder, Boolean active,
                                           MultipartFile image, MultipartFile video, boolean removeVideo) {
        Gift g = giftRepository.findById(id).orElseThrow(() -> ApiException.notFound("Gift not found"));
        if (name != null) g.setName(cleanName(name));
        if (price != null) { checkPrice(price); g.setPrice(price); }
        if (sortOrder != null) g.setSortOrder(sortOrder);
        if (active != null) g.setActive(active);

        if (image != null && !image.isEmpty()) {
            String oldId = g.getImagePublicId();
            attachImage(g, image);
            cloudinaryService.delete(oldId, "image");
        }
        if (video != null && !video.isEmpty()) {
            String oldId = g.getVideoPublicId();
            attachVideo(g, video);
            cloudinaryService.delete(oldId, "video");
        } else if (removeVideo && g.getVideoUrl() != null) {
            cloudinaryService.delete(g.getVideoPublicId(), "video");
            g.setVideoUrl(null);
            g.setVideoPublicId(null);
        }
        g.setUpdatedAt(Instant.now());
        giftRepository.save(g);
        return adminRow(g);
    }

    @Transactional
    public void adminDelete(String id) {
        Gift g = giftRepository.findById(id).orElseThrow(() -> ApiException.notFound("Gift not found"));
        giftRepository.delete(g);
        cloudinaryService.delete(g.getImagePublicId(), "image");
        cloudinaryService.delete(g.getVideoPublicId(), "video");
    }

    private Map<String, Object> adminRow(Gift g) {
        Map<String, Object> m = toJson(g);
        m.put("active", g.isActive());
        m.put("sortOrder", g.getSortOrder());
        m.put("createdAt", g.getCreatedAt());
        return m;
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 60) throw ApiException.unprocessable("Gift name must be 1-60 characters");
        return n;
    }

    private static void checkPrice(long price) {
        if (price < 1 || price > MAX_PRICE) throw ApiException.unprocessable("Price must be between 1 and " + MAX_PRICE + " coins");
    }

    private void attachImage(Gift g, MultipartFile image) {
        String type = image.getContentType() == null ? "" : image.getContentType();
        if (!(type.equals("image/png") || type.equals("image/jpeg") || type.equals("image/webp"))) {
            throw ApiException.unprocessable("Gift image must be PNG, JPG or WebP");
        }
        if (image.getSize() > MAX_IMAGE_BYTES) throw ApiException.unprocessable("Gift image must be under 5 MB");
        CloudinaryService.MediaResult up = cloudinaryService.uploadMedia(image, "commengine/gifts", "image");
        g.setImageUrl(up.url());
        g.setImagePublicId(up.publicId());
    }

    private void attachVideo(Gift g, MultipartFile video) {
        // MP4 only: every phone plays it, so the clip never silently fails in someone's room.
        if (!"video/mp4".equals(video.getContentType())) throw ApiException.unprocessable("Gift video must be an MP4 file");
        if (video.getSize() > MAX_VIDEO_BYTES) throw ApiException.unprocessable("Gift video must be under 10 MB");
        CloudinaryService.MediaResult up = cloudinaryService.uploadMedia(video, "commengine/gifts", "video");
        // Server-side length check (the admin page also checks in the browser, but never trust the client).
        if (up.duration() != null && up.duration() > MAX_VIDEO_SECONDS) {
            cloudinaryService.delete(up.publicId(), "video");
            throw ApiException.unprocessable("Gift video can be at most 5 seconds long");
        }
        g.setVideoUrl(up.url());
        g.setVideoPublicId(up.publicId());
    }

    @Transactional
    public Map<String, Object> send(User sender, SendGiftRequest req) {
        Gift gift = giftRepository.findById(req.getGiftId()).filter(Gift::isActive)
                .orElseThrow(() -> ApiException.badRequest("This gift is no longer available"));
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
            // Anyone in the room can gift anyone in the room (seated or not, themselves included).
            boolean receiverIsHost = room.getHost().getId().equals(receiver.getId());
            boolean receiverInRoom = audioState.getMembers(room.getId()).contains(receiver.getId());
            if (!receiverIsHost && !receiverInRoom) throw ApiException.badRequest("That person is no longer in the room");
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
        long total = gift.getPrice() * count;
        long beans = Math.max(1, total * BEAN_PERCENT / 100);
        String ref = UUID.randomUUID().toString();

        walletService.adjust(sender.getId(), WalletService.COINS, -total, "GIFT_SENT", ref,
                "Sent " + count + "x " + gift.getName() + " to " + receiver.getName());
        walletService.adjust(receiver.getId(), WalletService.BEANS, beans, "GIFT_RECEIVED", ref,
                "Got " + count + "x " + gift.getName() + " from " + sender.getName());

        Map<String, Object> giftJson = toJson(gift);

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
