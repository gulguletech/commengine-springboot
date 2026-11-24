package com.commengine.repository;

import com.commengine.entity.AudioRoomAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AudioRoomAdminRepository extends JpaRepository<AudioRoomAdmin, String> {

    List<AudioRoomAdmin> findByRoomId(String roomId);

    boolean existsByRoomIdAndUserId(String roomId, String userId);

    Optional<AudioRoomAdmin> findByRoomIdAndUserId(String roomId, String userId);
}
