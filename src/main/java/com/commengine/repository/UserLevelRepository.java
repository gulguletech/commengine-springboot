package com.commengine.repository;

import com.commengine.entity.UserLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserLevelRepository extends JpaRepository<UserLevel, String> {

    /** Atomic add (creates the row on first gift), safe with many gifts arriving at once. */
    @Modifying
    @Query(value = "INSERT INTO user_levels (user_id, received_coins, updated_at) VALUES (:uid, :amt, now()) "
            + "ON CONFLICT (user_id) DO UPDATE SET received_coins = user_levels.received_coins + :amt, updated_at = now()",
            nativeQuery = true)
    int addReceived(@Param("uid") String uid, @Param("amt") long amt);
}
