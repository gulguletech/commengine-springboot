package com.commengine.repository;

import com.commengine.entity.AudioRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AudioRoomMemberRepository extends JpaRepository<AudioRoomMember, String> {
    Optional<AudioRoomMember> findByRoomIdAndUserId(String roomId, String userId);
    List<AudioRoomMember> findByRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(String roomId);
    List<AudioRoomMember> findByRoomIdAndLeftAtIsNull(String roomId);
}
