package com.commengine.repository;

import com.commengine.entity.VideoRoomComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoRoomCommentRepository extends JpaRepository<VideoRoomComment, String> {
    List<VideoRoomComment> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

    @Modifying
    @Query("delete from VideoRoomComment c where c.room.id = :roomId")
    void deleteAllByRoomId(@Param("roomId") String roomId);
}