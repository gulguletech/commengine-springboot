package com.commengine.repository;

import com.commengine.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBadgeRepository extends JpaRepository<UserBadge, String> {

    Optional<UserBadge> findByUserIdAndBadgeId(String userId, String badgeId);

    List<UserBadge> findByUserId(String userId);

    void deleteByBadgeId(String badgeId);
}
