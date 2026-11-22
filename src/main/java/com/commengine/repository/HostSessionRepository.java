package com.commengine.repository;

import com.commengine.entity.HostSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface HostSessionRepository extends JpaRepository<HostSession, String> {

    List<HostSession> findByUserIdAndEndedAtIsNull(String userId);

    List<HostSession> findByRoomIdAndEndedAtIsNull(String roomId);

    List<HostSession> findByEndedAtIsNull();

    /** Sessions that overlap [from, to): started before `to` and not finished before `from`. */
    @Query("select s from HostSession s where s.userId = :userId and s.startedAt < :to and (s.endedAt is null or s.endedAt > :from)")
    List<HostSession> findOverlapping(@Param("userId") String userId, @Param("from") Instant from, @Param("to") Instant to);
}
