package com.commengine.repository;

import com.commengine.entity.VideoRoomViewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VideoRoomViewerRepository extends JpaRepository<VideoRoomViewer, String> {
    Optional<VideoRoomViewer> findByRoomIdAndUserId(String roomId, String userId);

    @Modifying
    @Query("delete from VideoRoomViewer v where v.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") String roomId);
}