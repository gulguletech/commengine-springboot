package com.commengine.repository;

import com.commengine.entity.PkParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PkParticipantRepository extends JpaRepository<PkParticipant, String> {

    List<PkParticipant> findByBattleIdOrderByPositionAsc(String battleId);

    /** Atomic, so two gifts landing together can't overwrite each other. */
    @Modifying
    @Query("update PkParticipant p set p.score = p.score + :coins where p.battleId = :battleId and p.userId = :userId")
    int addScore(@Param("battleId") String battleId, @Param("userId") String userId, @Param("coins") long coins);

    @Query("select count(p) from PkParticipant p, PkBattle b where p.battleId = b.id and p.userId = :userId and b.status = 'RUNNING'")
    long countRunning(@Param("userId") String userId);

    /** True while the user plays in any RUNNING battle. */
    default boolean inRunningBattle(String userId) {
        return countRunning(userId) > 0;
    }
}
