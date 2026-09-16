package com.commengine.repository;

import com.commengine.entity.AudioJoinRequest;
import com.commengine.entity.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AudioJoinRequestRepository extends JpaRepository<AudioJoinRequest, String> {
    Optional<AudioJoinRequest> findByRoomIdAndUserId(String roomId, String userId);
    List<AudioJoinRequest> findByRoomIdAndStatus(String roomId, JoinRequestStatus status);
}
