package com.commengine.repository;

import com.commengine.entity.RoomStatus;
import com.commengine.entity.VideoRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRoomRepository extends JpaRepository<VideoRoom, String> {
    List<VideoRoom> findByStatusOrderByCreatedAtDesc(RoomStatus status);
}
