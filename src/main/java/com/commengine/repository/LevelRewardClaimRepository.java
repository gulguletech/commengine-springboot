package com.commengine.repository;

import com.commengine.entity.LevelRewardClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LevelRewardClaimRepository extends JpaRepository<LevelRewardClaim, String> {
    List<LevelRewardClaim> findByUserId(String userId);
    boolean existsByUserIdAndLevel(String userId, int level);
}
