package com.commengine.repository;

import com.commengine.entity.AudioRoom;
import com.commengine.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AudioRoomRepository extends JpaRepository<AudioRoom, String> {

    // Both PUBLIC and PRIVATE rooms are listed for everyone — PRIVATE rooms are still
    // publicly discoverable, they just require the correct passcode at join time
    // (enforced in AudioRoomService#joinRoom). The unused `userId` param is kept so
    // the calling code doesn't need to change.
    @Query("select r from AudioRoom r where r.status = :status order by r.createdAt desc")
    List<AudioRoom> findVisibleRooms(@Param("status") RoomStatus status, @Param("userId") String userId);
}