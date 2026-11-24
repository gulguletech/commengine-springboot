package com.commengine.repository;

import com.commengine.entity.PkBattle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PkBattleRepository extends JpaRepository<PkBattle, String> {

    Optional<PkBattle> findFirstByRoomIdAndStatus(String roomId, String status);

    List<PkBattle> findByStatusAndEndsAtLessThanEqual(String status, Instant time);

    List<PkBattle> findByStatus(String status);
}
