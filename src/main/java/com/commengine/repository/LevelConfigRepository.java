package com.commengine.repository;

import com.commengine.entity.DailyRewardType;
import com.commengine.entity.LevelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LevelConfigRepository extends JpaRepository<LevelConfig, Integer> {

    /** A store item was deleted: the levels that gave it become "no reward". */
    @Modifying
    @Query("update LevelConfig l set l.type = :none, l.itemId = null, l.itemDays = 0 where l.itemId = :itemId")
    int clearItem(@Param("itemId") String itemId, @Param("none") DailyRewardType none);
}
