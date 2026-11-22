package com.commengine.repository;

import com.commengine.entity.AgencyLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgencyLevelRepository extends JpaRepository<AgencyLevel, String> {

    List<AgencyLevel> findAllByOrderByLevelAsc();

    boolean existsByLevel(int level);
}
