package com.commengine.repository;

import com.commengine.entity.GameJackpot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GameJackpotRepository extends JpaRepository<GameJackpot, String> {

    /** Row lock: only one settler at a time can pay out a game's rounds. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from GameJackpot j where j.game = :game")
    Optional<GameJackpot> lockByGame(@Param("game") String game);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GameJackpot j set j.pool = j.pool + :delta where j.game = :game")
    int addPool(@Param("game") String game, @Param("delta") long delta);
}
