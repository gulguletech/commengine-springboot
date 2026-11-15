package com.commengine.repository;

import com.commengine.entity.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserItemRepository extends JpaRepository<UserItem, String> {

    Optional<UserItem> findByUserIdAndItemId(String userId, String itemId);

    /** Everything this user owns that has not run out yet. */
    List<UserItem> findByUserIdAndExpiresAtAfterOrderByExpiresAtAsc(String userId, Instant now);

    @Modifying
    @Query("delete from UserItem ui where ui.itemId = :itemId")
    int deleteByItemId(@Param("itemId") String itemId);
}
