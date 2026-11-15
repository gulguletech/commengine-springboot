package com.commengine.repository;

import com.commengine.entity.DailyRewardClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRewardClaimRepository extends JpaRepository<DailyRewardClaim, String> {
}
