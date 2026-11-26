package com.commengine.repository;

import com.commengine.entity.TeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, String> {

    List<TeamInvite> findByToUserIdAndStatusOrderByCreatedAtDesc(String toUserId, String status);

    List<TeamInvite> findByFromUserIdAndStatusOrderByCreatedAtDesc(String fromUserId, String status);

    Optional<TeamInvite> findByFromUserIdAndToUserIdAndKindAndStatus(String fromUserId, String toUserId, String kind, String status);

    long countByToUserIdAndStatus(String toUserId, String status);
}
