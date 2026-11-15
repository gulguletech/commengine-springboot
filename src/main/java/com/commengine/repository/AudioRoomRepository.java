package com.commengine.repository;

import com.commengine.entity.AudioRoom;
import com.commengine.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AudioRoomRepository extends JpaRepository<AudioRoom, String> {

    // Both PUBLIC and PRIVATE rooms are listed for everyone — PRIVATE rooms are still
    // publicly discoverable, they just require the correct passcode at join time
    // (enforced in AudioRoomService#joinRoom).
    @Query("select r from AudioRoom r where r.status = :status order by r.createdAt desc")
    List<AudioRoom> findVisibleRooms(@Param("status") RoomStatus status);

    long countByStatus(RoomStatus status);

    // ---- store backgrounds: keep the denormalised copy on rooms in step with the shelf ----

    @Modifying
    @Query("update AudioRoom r set r.backgroundUrl = :url, r.backgroundThumbUrl = :thumb, r.backgroundScrim = :scrim where r.backgroundItemId = :itemId")
    int refreshBackground(@Param("itemId") String itemId, @Param("url") String url, @Param("thumb") String thumb, @Param("scrim") Double scrim);

    @Modifying
    @Query("update AudioRoom r set r.backgroundItemId = null, r.backgroundUrl = null, r.backgroundThumbUrl = null, r.backgroundScrim = null, r.backgroundExpiresAt = null where r.backgroundItemId = :itemId")
    int clearBackground(@Param("itemId") String itemId);

    /** A host renewed a paid background: push the new end date onto their rooms that use it. */
    @Modifying
    @Query("update AudioRoom r set r.backgroundExpiresAt = :at where r.host.id = :hostId and r.backgroundItemId = :itemId")
    int extendBackground(@Param("hostId") String hostId, @Param("itemId") String itemId, @Param("at") java.time.Instant at);
}
