package com.commengine.repository;

import com.commengine.entity.VideoRoomComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRoomCommentRepository extends JpaRepository<VideoRoomComment, String> {
    List<VideoRoomComment> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
}
