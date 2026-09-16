package com.commengine.repository;

import com.commengine.entity.FriendRequest;
import com.commengine.entity.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {

    Optional<FriendRequest> findBySenderIdAndReceiverId(String senderId, String receiverId);

    @Query("select f from FriendRequest f where f.status = com.commengine.entity.FriendRequestStatus.ACCEPTED " +
           "and (f.sender.id = :userId or f.receiver.id = :userId)")
    List<FriendRequest> findAcceptedForUser(@Param("userId") String userId);

    List<FriendRequest> findByReceiverIdAndStatusOrderByCreatedAtDesc(String receiverId, FriendRequestStatus status);

    @Query("select f from FriendRequest f where (f.sender.id = :userA and f.receiver.id = :userB) or (f.sender.id = :userB and f.receiver.id = :userA)")
    Optional<FriendRequest> findBetween(@Param("userA") String userA, @Param("userB") String userB);
}
