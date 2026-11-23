package com.commengine.service;

/**
 * Hook between "invite someone to the battle" and your PK code.
 *
 * The PK (battle) server code was not in the backend that was shared, so the invite feature cannot look these two
 * things up itself. Make your existing PK service (or any @Component) implement this interface and it is picked up
 * automatically. Without an implementation: nobody counts as "in a PK", and only the room HOST can send invites.
 */
public interface PkIntegration {

    /** True while the user is a participant of a RUNNING PK battle in any room. Such users can't be invited. */
    boolean isInRunningPk(String userId);

    /** True if the user is a room admin of this audio room (admins may manage PK like the host does). */
    boolean isRoomAdmin(String roomId, String userId);
}
