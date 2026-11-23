package com.commengine.repository;

import com.commengine.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByPhone(String phone);

    /** Locks the user row until the transaction ends, so two taps / two devices can't claim the same reward twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") String id);

    boolean existsByPublicId(String publicId);

    Optional<User> findByPublicId(String publicId);

    List<User> findByPublicIdIsNull();

    /** Name contains the text, or the short user ID starts with it. */
    @Query("select u from User u where u.id <> :excludeId and (:search is null or :search = '' or lower(u.name) like lower(concat('%', :search, '%')) or u.publicId like concat(:search, '%'))")
    List<User> searchUsers(@Param("excludeId") String excludeId, @Param("search") String search,
                            org.springframework.data.domain.Pageable pageable);

    // ---- admin panel ----
    @Query("select u from User u where :q = '' or lower(u.name) like lower(concat('%', :q, '%')) or u.phone like concat('%', :q, '%') or u.publicId like concat(:q, '%')")
    Page<User> adminSearch(@Param("q") String q, Pageable pageable);

    @Query("select count(u) from User u where u.isOnline = true")
    long countOnline();

    long countByBannedTrue();

    long countByCreatedAtBetween(Instant from, Instant to);

    // ---- store: keep the denormalised "equipped" copies in step with the shelf ----

    @Modifying
    @Query("update User u set u.frameUrl = :url where u.frameItemId = :itemId")
    int refreshFrame(@Param("itemId") String itemId, @Param("url") String url);

    @Modifying
    @Query("update User u set u.bubbleUrl = :url, u.bubbleTextColor = :color where u.bubbleItemId = :itemId")
    int refreshBubble(@Param("itemId") String itemId, @Param("url") String url, @Param("color") String color);

    @Modifying
    @Query("update User u set u.entryName = :name, u.entryVideoUrl = :url where u.entryItemId = :itemId")
    int refreshEntry(@Param("itemId") String itemId, @Param("name") String name, @Param("url") String url);

    @Modifying
    @Query("update User u set u.frameItemId = null, u.frameUrl = null, u.frameExpiresAt = null where u.frameItemId = :itemId")
    int clearFrame(@Param("itemId") String itemId);

    @Modifying
    @Query("update User u set u.bubbleItemId = null, u.bubbleUrl = null, u.bubbleTextColor = null, u.bubbleExpiresAt = null where u.bubbleItemId = :itemId")
    int clearBubble(@Param("itemId") String itemId);

    @Modifying
    @Query("update User u set u.entryItemId = null, u.entryName = null, u.entryVideoUrl = null, u.entryExpiresAt = null where u.entryItemId = :itemId")
    int clearEntry(@Param("itemId") String itemId);

    /** A user renewed a paid item they are wearing: push the new end date onto the equipped copy. */
    @Modifying
    @Query("update User u set u.frameExpiresAt = :at where u.id = :userId and u.frameItemId = :itemId")
    int extendFrame(@Param("userId") String userId, @Param("itemId") String itemId, @Param("at") Instant at);

    @Modifying
    @Query("update User u set u.bubbleExpiresAt = :at where u.id = :userId and u.bubbleItemId = :itemId")
    int extendBubble(@Param("userId") String userId, @Param("itemId") String itemId, @Param("at") Instant at);

    @Modifying
    @Query("update User u set u.entryExpiresAt = :at where u.id = :userId and u.entryItemId = :itemId")
    int extendEntry(@Param("userId") String userId, @Param("itemId") String itemId, @Param("at") Instant at);

    long countByVipExpiresAtAfter(java.time.Instant now);

    /** Users connected to the app right now (socket online), newest activity first. Optional name / user ID search. */
    @Query("select u from User u where u.isOnline = true and u.banned = false and (:q = '' or lower(u.name) like lower(concat('%', :q, '%')) or u.publicId = :q) order by u.lastSeen desc")
    java.util.List<User> findOnlineForInvite(@Param("q") String q, org.springframework.data.domain.Pageable pageable);
}
