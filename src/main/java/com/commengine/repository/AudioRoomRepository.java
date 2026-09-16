package com.commengine.repository;

import com.commengine.entity.AudioRoom;
import com.commengine.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AudioRoomRepository extends JpaRepository<AudioRoom, String> {

    @Query("select distinct r from AudioRoom r left join AudioRoomMember m on m.room = r and m.leftAt is null " +
           "where r.status = :status and (r.type = com.commengine.entity.RoomType.PUBLIC or r.host.id = :userId or m.user.id = :userId) " +
           "order by r.createdAt desc")
    List<AudioRoom> findVisibleRooms(@Param("status") RoomStatus status, @Param("userId") String userId);
}
