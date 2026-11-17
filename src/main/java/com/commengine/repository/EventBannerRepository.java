package com.commengine.repository;

import com.commengine.entity.EventBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventBannerRepository extends JpaRepository<EventBanner, String> {
    List<EventBanner> findByActiveTrueOrderBySortOrderAscCreatedAtDesc();
    List<EventBanner> findAllByOrderBySortOrderAscCreatedAtDesc();
}
