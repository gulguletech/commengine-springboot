package com.commengine.repository;

import com.commengine.entity.VideoRoomViewer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoRoomViewerRepository extends JpaRepository<VideoRoomViewer, String> {
    Optional<VideoRoomViewer> findByRoomIdAndUserId(String roomId, String userId);
}
