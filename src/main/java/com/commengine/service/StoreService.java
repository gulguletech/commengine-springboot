package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.*;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.repository.DailyRewardDayRepository;
import com.commengine.repository.StoreItemRepository;
import com.commengine.repository.UserItemRepository;
import com.commengine.repository.UserRepository;
import com.commengine.socket.SocketUserResolver;
import com.commengine.util.StoreMediaValidator;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The store. Four kinds of item (see StoreCategory), priced per day in coins, rented for a number of days.
 *
 * - Free items are usable by everyone. Paid items need a UserItem row that has not expired.
 * - FRAME / BUBBLE / ENTRY_EFFECT are "worn": one per category per user, copied onto the User row so every payload
 *   that carries a user (DtoMapper) shows it with no extra queries.
 * - BACKGROUND is not worn: the host picks one per audio room (copied onto the AudioRoom row).
 */
@Service
public class StoreService {

    /** Rental lengths the app offers. */
    public static final List<Integer> DURATIONS = List.of(1, 3, 7, 15, 30);
    private static final long MAX_PRICE_PER_DAY = 1_000_000;
    private static final long MAX_HOLD_DAYS = 365;
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final StoreItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final UserRepository userRepository;
    private final AudioRoomRepository roomRepository;
    private final WalletService walletService;
    private final CloudinaryService cloudinaryService;
    private final SocketIOServer server;
    private final SocketUserResolver socketUserResolver;
    private final DailyRewardDayRepository dailyRewardDays;

    public StoreService(StoreItemRepository itemRepository, UserItemRepository userItemRepository,
                        UserRepository userRepository, AudioRoomRepository roomRepository,
                        WalletService walletService, CloudinaryService cloudinaryService,
                        SocketIOServer server, SocketUserResolver socketUserResolver,
                        DailyRewardDayRepository dailyRewardDays) {
        this.dailyRewardDays = dailyRewardDays;
        this.itemRepository = itemRepository;
        this.userItemRepository = userItemRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.walletService = walletService;
        this.cloudinaryService = cloudinaryService;
        this.server = server;
        this.socketUserResolver = socketUserResolver;
    }

    // ================================================================== app side

    /** The shelf for one category, with this user's state on every item. (Not read-only: it may create the wallet row.) */
    @Transactional
    public Map<String, Object> catalog(User user, StoreCategory category) {
        User me = freshUser(user.getId());
        Map<String, UserItem> owned = ownedByItem(me.getId());
        List<Map<String, Object>> items = itemRepository
                .findByCategoryAndActiveTrueOrderBySortOrderAscCreatedAtAsc(category).stream()
                .map(i -> appJson(i, me, owned.get(i.getId()))).toList();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("category", category);
        res.put("items", items);
        res.put("durations", DURATIONS);
        res.put("wallet", walletService.getBalance(me.getId()));
        return res;
    }

    /** Backpack: everything the user owns right now (paid, not expired), plus what is currently worn. */
    @Transactional(readOnly = true)
    public Map<String, Object> backpack(User user) {
        User me = freshUser(user.getId());
        List<UserItem> mine = userItemRepository.findByUserIdAndExpiresAtAfterOrderByExpiresAtAsc(me.getId(), Instant.now());
        Map<String, StoreItem> items = new HashMap<>();
        itemRepository.findAllById(mine.stream().map(UserItem::getItemId).toList()).forEach(i -> items.put(i.getId(), i));
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserItem ui : mine) {
            StoreItem item = items.get(ui.getItemId());
            if (item != null) list.add(appJson(item, me, ui));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", list);
        res.put("equipped", equippedJson(me));
        return res;
    }

    /** Backgrounds this user may put in a room: every free one plus the paid ones they still own. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> usableBackgrounds(User user) {
        Map<String, UserItem> owned = ownedByItem(user.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (StoreItem i : itemRepository.findByCategoryAndActiveTrueOrderBySortOrderAscCreatedAtAsc(StoreCategory.BACKGROUND)) {
            UserItem ui = owned.get(i.getId());
            if (i.isFree() || (ui != null && ui.isActive())) out.add(appJson(i, null, ui));
        }
        // paid backgrounds the admin has since switched off stay usable until the rental ends
        for (UserItem ui : owned.values()) {
            if (!ui.isActive()) continue;
            if (out.stream().anyMatch(m -> ui.getItemId().equals(m.get("id")))) continue;
            itemRepository.findById(ui.getItemId())
                    .filter(i -> i.getCategory() == StoreCategory.BACKGROUND && !i.isFree())
                    .ifPresent(i -> out.add(appJson(i, null, ui)));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> buy(User user, String itemId, int days) {
        StoreItem item = purchasable(itemId, days);
        Instant newExpiry = projectedExpiry(user.getId(), item, days);
        long total = item.getPricePerDay() * days;
        String ref = UUID.randomUUID().toString();
        walletService.adjust(user.getId(), WalletService.COINS, -total, "STORE_BUY", ref,
                "Bought " + item.getName() + " for " + days + (days == 1 ? " day" : " days"));
        grant(user.getId(), item, newExpiry);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("itemId", item.getId());
        res.put("expiresAt", newExpiry.toString());
        res.put("wallet", walletService.getBalance(user.getId()));
        return res;
    }

    /** Buy an item as a present: the sender pays, the receiver gets the days. */
    @Transactional
    public Map<String, Object> send(User sender, String itemId, int days, String receiverId) {
        if (receiverId == null || receiverId.isBlank()) throw ApiException.badRequest("Pick who to send it to");
        if (receiverId.equals(sender.getId())) throw ApiException.badRequest("Use Buy to get an item for yourself");
        User receiver = userRepository.findById(receiverId).orElseThrow(() -> ApiException.notFound("That user was not found"));
        StoreItem item = purchasable(itemId, days);
        Instant newExpiry = projectedExpiry(receiver.getId(), item, days);
        long total = item.getPricePerDay() * days;
        String ref = UUID.randomUUID().toString();
        walletService.adjust(sender.getId(), WalletService.COINS, -total, "STORE_SEND", ref,
                "Sent " + item.getName() + " (" + days + "d) to " + receiver.getName());
        grant(receiver.getId(), item, newExpiry);

        Map<String, Object> note = new LinkedHashMap<>();
        note.put("item", publicJson(item));
        note.put("days", days);
        note.put("from", DtoMapper.userBrief(sender));
        server.getRoomOperations("user:" + receiver.getId()).sendEvent("store:item-received", note);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("wallet", walletService.getBalance(sender.getId()));
        return res;
    }

    /**
     * Gives a user `days` of a paid item as a reward (no coins charged). The days are added on top of any days
     * they still have, exactly like buying again. Returns the item's public JSON, or null if the item no longer
     * exists or is free (free items are already usable by everyone, so there is nothing to give).
     */
    @Transactional
    public Map<String, Object> grantReward(String userId, String itemId, int days) {
        StoreItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null || item.isFree() || days < 1) return null;
        Instant now = Instant.now();
        Instant base = userItemRepository.findByUserIdAndItemId(userId, item.getId())
                .map(UserItem::getExpiresAt).filter(e -> e.isAfter(now)).orElse(now);
        Instant end = base.plus(days, ChronoUnit.DAYS);
        Instant cap = now.plus(MAX_HOLD_DAYS, ChronoUnit.DAYS); // a reward never fails, it just stops growing at the cap
        grant(userId, item, end.isAfter(cap) ? cap : end);
        return publicJson(item);
    }

    /** Item details for another feature (e.g. the daily reward screen). Null if the item is gone. */
    @Transactional(readOnly = true)
    public Map<String, Object> describe(String itemId) {
        return itemRepository.findById(itemId).map(StoreService::publicJson).orElse(null);
    }

    /** Wear a FRAME / BUBBLE / ENTRY_EFFECT. Backgrounds are chosen per room instead. */
    @Transactional
    public Map<String, Object> equip(User user, String itemId) {
        StoreItem item = itemRepository.findById(itemId).orElseThrow(() -> ApiException.notFound("Item not found"));
        if (item.getCategory() == StoreCategory.BACKGROUND) {
            throw ApiException.badRequest("Backgrounds are chosen for a room in the room settings");
        }
        Instant expiry = usableUntil(user.getId(), item); // throws if the user may not use it; null = free
        User me = freshUser(user.getId());
        switch (item.getCategory()) {
            case FRAME -> { me.setFrameItemId(item.getId()); me.setFrameUrl(item.getImageUrl()); me.setFrameExpiresAt(expiry); }
            case BUBBLE -> {
                me.setBubbleItemId(item.getId()); me.setBubbleUrl(item.getImageUrl());
                me.setBubbleTextColor(item.getTextColor()); me.setBubbleExpiresAt(expiry);
            }
            case ENTRY_EFFECT -> {
                me.setEntryItemId(item.getId()); me.setEntryName(item.getName());
                me.setEntryVideoUrl(item.getVideoUrl()); me.setEntryExpiresAt(expiry);
            }
            default -> throw ApiException.badRequest("This item cannot be worn");
        }
        userRepository.save(me);
        socketUserResolver.refreshClients(server, me);
        return Map.of("equipped", equippedJson(me));
    }

    @Transactional
    public Map<String, Object> unequip(User user, StoreCategory category) {
        User me = freshUser(user.getId());
        switch (category) {
            case FRAME -> { me.setFrameItemId(null); me.setFrameUrl(null); me.setFrameExpiresAt(null); }
            case BUBBLE -> { me.setBubbleItemId(null); me.setBubbleUrl(null); me.setBubbleTextColor(null); me.setBubbleExpiresAt(null); }
            case ENTRY_EFFECT -> { me.setEntryItemId(null); me.setEntryName(null); me.setEntryVideoUrl(null); me.setEntryExpiresAt(null); }
            default -> throw ApiException.badRequest("Backgrounds are removed in the room settings");
        }
        userRepository.save(me);
        socketUserResolver.refreshClients(server, me);
        return Map.of("equipped", equippedJson(me));
    }

    /**
     * Put a store background on a room (or clear it with a null/blank id). The host must be allowed to use it:
     * free, or paid and not expired. Called when a room is created and from room settings.
     */
    public void applyRoomBackground(AudioRoom room, User host, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            room.setBackgroundItemId(null);
            room.setBackgroundUrl(null);
            room.setBackgroundThumbUrl(null);
            room.setBackgroundScrim(null);
            room.setBackgroundExpiresAt(null);
            return;
        }
        StoreItem item = itemRepository.findById(itemId).orElseThrow(() -> ApiException.notFound("Background not found"));
        if (item.getCategory() != StoreCategory.BACKGROUND) throw ApiException.badRequest("That item is not a room background");
        Instant expiry = usableUntil(host.getId(), item);
        room.setBackgroundItemId(item.getId());
        room.setBackgroundUrl(item.getImageUrl());
        room.setBackgroundThumbUrl(thumbUrl(item));
        room.setBackgroundScrim(item.getScrim());
        room.setBackgroundExpiresAt(expiry);
    }

    // ------------------------------------------------------------------ app side helpers

    private User freshUser(String id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private Map<String, UserItem> ownedByItem(String userId) {
        Map<String, UserItem> m = new HashMap<>();
        for (UserItem ui : userItemRepository.findByUserIdAndExpiresAtAfterOrderByExpiresAtAsc(userId, Instant.now())) {
            m.put(ui.getItemId(), ui);
        }
        return m;
    }

    private StoreItem purchasable(String itemId, int days) {
        if (!DURATIONS.contains(days)) throw ApiException.badRequest("Choose " + DURATIONS + " days");
        StoreItem item = itemRepository.findById(itemId).filter(StoreItem::isActive)
                .orElseThrow(() -> ApiException.badRequest("This item is no longer available"));
        if (item.isFree()) throw ApiException.badRequest("This item is free - no need to buy it");
        return item;
    }

    /** When the rental will end if `days` are added now (stacks on top of days still left). */
    private Instant projectedExpiry(String userId, StoreItem item, int days) {
        Instant now = Instant.now();
        Instant base = userItemRepository.findByUserIdAndItemId(userId, item.getId())
                .map(UserItem::getExpiresAt).filter(e -> e.isAfter(now)).orElse(now);
        Instant end = base.plus(days, ChronoUnit.DAYS);
        if (Duration.between(now, end).toDays() > MAX_HOLD_DAYS) {
            throw ApiException.unprocessable("You can hold at most " + MAX_HOLD_DAYS + " days of one item");
        }
        return end;
    }

    /** Save the rental and push the new end date onto anything the owner is already wearing / using it in. */
    private void grant(String userId, StoreItem item, Instant newExpiry) {
        UserItem ui = userItemRepository.findByUserIdAndItemId(userId, item.getId()).orElseGet(() -> {
            UserItem n = new UserItem();
            n.setUserId(userId);
            n.setItemId(item.getId());
            return n;
        });
        ui.setExpiresAt(newExpiry);
        ui.setUpdatedAt(Instant.now());
        userItemRepository.save(ui);

        switch (item.getCategory()) {
            case FRAME -> userRepository.extendFrame(userId, item.getId(), newExpiry);
            case BUBBLE -> userRepository.extendBubble(userId, item.getId(), newExpiry);
            case ENTRY_EFFECT -> userRepository.extendEntry(userId, item.getId(), newExpiry);
            case BACKGROUND -> roomRepository.extendBackground(userId, item.getId(), newExpiry);
        }
        userRepository.findById(userId).ifPresent(u -> socketUserResolver.refreshClients(server, u));
    }

    /**
     * May this user use this item right now? Returns the end of their rental, or null for a free item.
     * Throws if they may not.
     */
    private Instant usableUntil(String userId, StoreItem item) {
        if (item.isFree()) {
            if (!item.isActive()) throw ApiException.badRequest("This item is no longer available");
            return null;
        }
        return userItemRepository.findByUserIdAndItemId(userId, item.getId())
                .filter(UserItem::isActive).map(UserItem::getExpiresAt)
                .orElseThrow(() -> ApiException.forbidden("Buy this item first (or it has expired)"));
    }

    // ------------------------------------------------------------------ JSON shapes

    /** Shared by the shelf, the backpack and the admin list. */
    private static Map<String, Object> publicJson(StoreItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("category", i.getCategory());
        m.put("name", i.getName());
        m.put("free", i.isFree());
        m.put("pricePerDay", i.getPricePerDay());
        m.put("imageUrl", i.getImageUrl());
        m.put("thumbUrl", thumbUrl(i));
        m.put("videoUrl", i.getVideoUrl());
        m.put("textColor", i.getTextColor());
        m.put("scrim", i.getScrim());
        return m;
    }

    /** publicJson + this user's state: usable, when the rental ends, and whether it is being worn. */
    private static Map<String, Object> appJson(StoreItem i, User me, UserItem ui) {
        Map<String, Object> m = publicJson(i);
        boolean rented = ui != null && ui.isActive();
        m.put("usable", i.isFree() || rented);
        m.put("expiresAt", rented ? ui.getExpiresAt().toString() : null);
        m.put("equipped", me != null && isWorn(me, i));
        return m;
    }

    private static boolean isWorn(User u, StoreItem i) {
        return switch (i.getCategory()) {
            case FRAME -> i.getId().equals(u.getFrameItemId()) && u.activeFrameUrl() != null;
            case BUBBLE -> i.getId().equals(u.getBubbleItemId()) && u.hasActiveBubble();
            case ENTRY_EFFECT -> i.getId().equals(u.getEntryItemId()) && u.hasActiveEntryEffect();
            case BACKGROUND -> false;
        };
    }

    private static Map<String, Object> equippedJson(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("frameItemId", u.activeFrameUrl() != null ? u.getFrameItemId() : null);
        m.put("bubbleItemId", u.hasActiveBubble() ? u.getBubbleItemId() : null);
        m.put("entryItemId", u.hasActiveEntryEffect() ? u.getEntryItemId() : null);
        return m;
    }

    /** Small picture for shelf grids. Cloudinary resizes on the fly; anything else is served as uploaded. */
    private static String thumbUrl(StoreItem i) {
        String t = switch (i.getCategory()) {
            case BACKGROUND -> "c_fill,w_270,h_480,f_jpg,q_auto";
            case FRAME, BUBBLE -> "c_limit,w_256,h_256,f_png,q_auto";
            case ENTRY_EFFECT -> "c_limit,w_256,h_256,q_auto";
        };
        return transform(i.getImageUrl(), t);
    }

    private static String transform(String url, String transformation) {
        if (url == null) return null;
        String marker = "/image/upload/";
        int at = url.indexOf(marker);
        if (at < 0 || !url.contains("res.cloudinary.com")) return url;
        return url.substring(0, at + marker.length()) + transformation + "/" + url.substring(at + marker.length());
    }

    // ================================================================== admin side

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminList(StoreCategory category) {
        List<StoreItem> all = category == null
                ? itemRepository.findAllByOrderByCategoryAscSortOrderAscCreatedAtAsc()
                : itemRepository.findByCategoryOrderBySortOrderAscCreatedAtAsc(category);
        return all.stream().map(this::adminJson).toList();
    }

    private Map<String, Object> adminJson(StoreItem i) {
        Map<String, Object> m = publicJson(i);
        m.put("active", i.isActive());
        m.put("sortOrder", i.getSortOrder());
        m.put("createdAt", i.getCreatedAt());
        return m;
    }

    public Map<String, Object> adminSpecs() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("specs", StoreMediaValidator.specs());
        m.put("durations", DURATIONS);
        m.put("maxPricePerDay", MAX_PRICE_PER_DAY);
        return m;
    }

    @Transactional
    public Map<String, Object> adminCreate(StoreCategory category, String name, boolean free, Long pricePerDay,
                                           Integer sortOrder, String textColor, Double scrim,
                                           MultipartFile image, MultipartFile preview, MultipartFile video) {
        StoreItem item = new StoreItem();
        item.setCategory(category);
        item.setName(cleanName(name));
        applyPrice(item, free, pricePerDay);
        item.setSortOrder(sortOrder == null ? 0 : sortOrder);
        applyExtras(item, textColor, scrim);

        // 1) validate EVERYTHING before anything is uploaded, so a rejected file never leaves orphans behind
        if (category == StoreCategory.ENTRY_EFFECT) {
            byte[] previewBytes = require(preview, "A preview image (preview) is required");
            byte[] videoBytes = require(video, "The effect video (video) is required");
            validate(() -> StoreMediaValidator.validatePreview(previewBytes));
            validate(() -> StoreMediaValidator.validateEntryVideo(videoBytes));
        } else {
            byte[] imageBytes = require(image, "The image (image) is required");
            validate(() -> validateImage(category, imageBytes));
        }

        // 2) upload, cleaning up if the second upload fails
        String folder = "commengine/store/" + category.name().toLowerCase();
        List<String[]> uploaded = new ArrayList<>(); // {publicId, kind}
        try {
            if (category == StoreCategory.ENTRY_EFFECT) {
                CloudinaryService.MediaResult p = cloudinaryService.uploadMedia(preview, folder, "image");
                uploaded.add(new String[]{p.publicId(), "image"});
                item.setImageUrl(p.url());
                item.setImagePublicId(p.publicId());
                CloudinaryService.MediaResult v = cloudinaryService.uploadMedia(video, folder, "video");
                uploaded.add(new String[]{v.publicId(), "video"});
                item.setVideoUrl(v.url());
                item.setVideoPublicId(v.publicId());
            } else {
                CloudinaryService.MediaResult up = cloudinaryService.uploadMedia(image, folder, "image");
                uploaded.add(new String[]{up.publicId(), "image"});
                item.setImageUrl(up.url());
                item.setImagePublicId(up.publicId());
            }
            itemRepository.save(item);
        } catch (RuntimeException e) {
            uploaded.forEach(u -> cloudinaryService.delete(u[0], u[1]));
            throw e;
        }
        return adminJson(item);
    }

    @Transactional
    public Map<String, Object> adminUpdate(String id, String name, Boolean free, Long pricePerDay, Integer sortOrder,
                                           Boolean active, String textColor, Double scrim,
                                           MultipartFile image, MultipartFile preview, MultipartFile video) {
        StoreItem item = itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Item not found"));
        StoreCategory category = item.getCategory();
        boolean wasFree = item.isFree();

        if (name != null) item.setName(cleanName(name));
        if (free != null || pricePerDay != null) {
            applyPrice(item, free != null ? free : item.isFree(), pricePerDay != null ? pricePerDay : (item.getPricePerDay() > 0 ? item.getPricePerDay() : null));
        }
        if (sortOrder != null) item.setSortOrder(sortOrder);
        if (active != null) item.setActive(active);
        if (textColor != null || scrim != null) applyExtras(item, textColor, scrim);

        // validate any replacement files first
        boolean entry = category == StoreCategory.ENTRY_EFFECT;
        if (!entry && has(preview)) throw ApiException.unprocessable("Only entry effects have a preview image");
        if (!entry && has(video)) throw ApiException.unprocessable("Only entry effects have a video");
        if (entry && has(image)) throw ApiException.unprocessable("Entry effects use preview + video, not image");
        if (has(image)) { byte[] b = require(image, ""); validate(() -> validateImage(category, b)); }
        if (has(preview)) { byte[] b = require(preview, ""); validate(() -> StoreMediaValidator.validatePreview(b)); }
        if (has(video)) { byte[] b = require(video, ""); validate(() -> StoreMediaValidator.validateEntryVideo(b)); }

        String folder = "commengine/store/" + category.name().toLowerCase();
        List<String[]> toDelete = new ArrayList<>();
        List<String[]> uploaded = new ArrayList<>();
        try {
            if (has(image) || has(preview)) {
                MultipartFile f = has(image) ? image : preview;
                CloudinaryService.MediaResult up = cloudinaryService.uploadMedia(f, folder, "image");
                uploaded.add(new String[]{up.publicId(), "image"});
                toDelete.add(new String[]{item.getImagePublicId(), "image"});
                item.setImageUrl(up.url());
                item.setImagePublicId(up.publicId());
            }
            if (has(video)) {
                CloudinaryService.MediaResult up = cloudinaryService.uploadMedia(video, folder, "video");
                uploaded.add(new String[]{up.publicId(), "video"});
                toDelete.add(new String[]{item.getVideoPublicId(), "video"});
                item.setVideoUrl(up.url());
                item.setVideoPublicId(up.publicId());
            }
            item.setUpdatedAt(Instant.now());
            itemRepository.save(item);
        } catch (RuntimeException e) {
            uploaded.forEach(u -> cloudinaryService.delete(u[0], u[1]));
            throw e;
        }
        toDelete.forEach(d -> cloudinaryService.delete(d[0], d[1]));

        // keep the copies on users / rooms in step with the shelf
        if (wasFree != item.isFree() || (item.isFree() && !item.isActive())) {
            // free <-> paid changed (or a free item was switched off): nobody keeps wearing it on the old terms
            clearEverywhere(item.getId());
        } else {
            refreshEverywhere(item);
        }
        return adminJson(item);
    }

    @Transactional
    public void adminDelete(String id) {
        StoreItem item = itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Item not found"));
        clearEverywhere(item.getId());
        userItemRepository.deleteByItemId(item.getId());
        dailyRewardDays.clearItem(item.getId(), DailyRewardType.NONE); // reward days that gave it become "not set"
        itemRepository.delete(item);
        cloudinaryService.delete(item.getImagePublicId(), "image");
        cloudinaryService.delete(item.getVideoPublicId(), "video");
    }

    private void refreshEverywhere(StoreItem item) {
        switch (item.getCategory()) {
            case FRAME -> userRepository.refreshFrame(item.getId(), item.getImageUrl());
            case BUBBLE -> userRepository.refreshBubble(item.getId(), item.getImageUrl(), item.getTextColor());
            case ENTRY_EFFECT -> userRepository.refreshEntry(item.getId(), item.getName(), item.getVideoUrl());
            case BACKGROUND -> roomRepository.refreshBackground(item.getId(), item.getImageUrl(), thumbUrl(item), item.getScrim());
        }
    }

    private void clearEverywhere(String itemId) {
        userRepository.clearFrame(itemId);
        userRepository.clearBubble(itemId);
        userRepository.clearEntry(itemId);
        roomRepository.clearBackground(itemId);
    }

    // ------------------------------------------------------------------ admin helpers

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 60) throw ApiException.unprocessable("Name must be 1-60 characters");
        return n;
    }

    private static void applyPrice(StoreItem item, boolean free, Long pricePerDay) {
        item.setFree(free);
        if (free) {
            item.setPricePerDay(0);
            return;
        }
        if (pricePerDay == null || pricePerDay < 1 || pricePerDay > MAX_PRICE_PER_DAY) {
            throw ApiException.unprocessable("A paid item needs a price per day between 1 and " + MAX_PRICE_PER_DAY + " coins");
        }
        item.setPricePerDay(pricePerDay);
    }

    /** textColor matters for BUBBLE, scrim for BACKGROUND; other categories ignore them. */
    private static void applyExtras(StoreItem item, String textColor, Double scrim) {
        if (item.getCategory() == StoreCategory.BUBBLE) {
            String c = textColor == null || textColor.isBlank() ? (item.getTextColor() == null ? "#FFFFFF" : item.getTextColor()) : textColor.trim();
            if (!HEX_COLOR.matcher(c).matches()) throw ApiException.unprocessable("Text colour must look like #FFFFFF");
            item.setTextColor(c.toUpperCase());
        }
        if (item.getCategory() == StoreCategory.BACKGROUND) {
            double s = scrim == null ? (item.getScrim() == null ? 0.35 : item.getScrim()) : scrim;
            if (s < 0 || s > 0.8) throw ApiException.unprocessable("Darkness (scrim) must be between 0 and 0.8");
            item.setScrim(s);
        }
    }

    private static boolean has(MultipartFile f) {
        return f != null && !f.isEmpty();
    }

    private static byte[] require(MultipartFile f, String missingMessage) {
        if (!has(f)) throw ApiException.unprocessable(missingMessage);
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw ApiException.unprocessable("Could not read the uploaded file");
        }
    }

    private static void validateImage(StoreCategory category, byte[] bytes) {
        switch (category) {
            case FRAME -> StoreMediaValidator.validateFrame(bytes);
            case BUBBLE -> StoreMediaValidator.validateBubble(bytes);
            case BACKGROUND -> StoreMediaValidator.validateBackground(bytes);
            default -> throw new IllegalArgumentException("This category does not take a single image");
        }
    }

    private static void validate(Runnable check) {
        try {
            check.run();
        } catch (IllegalArgumentException e) {
            throw ApiException.unprocessable(e.getMessage());
        }
    }
}
