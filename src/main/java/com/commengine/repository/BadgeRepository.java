package com.commengine.repository;

import com.commengine.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BadgeRepository extends JpaRepository<Badge, String> {

    List<Badge> findAllByOrderBySortOrderAscCreatedAtAsc();

    List<Badge> findByActiveTrueOrderBySortOrderAscCreatedAtAsc();
}
