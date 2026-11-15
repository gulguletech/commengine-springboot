package com.commengine.repository;

import com.commengine.entity.DailyRewardDay;
import com.commengine.entity.DailyRewardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyRewardDayRepository extends JpaRepository<DailyRewardDay, Integer> {

    /** A store item was deleted: the days that gave it become "nothing set". */
    @Modifying
    @Query("update DailyRewardDay d set d.type = :none, d.itemId = null, d.itemDays = 0 where d.itemId = :itemId")
    int clearItem(@Param("itemId") String itemId, @Param("none") DailyRewardType none);
}
