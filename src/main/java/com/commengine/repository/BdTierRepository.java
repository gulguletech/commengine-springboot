package com.commengine.repository;

import com.commengine.entity.BdTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface BdTierRepository extends JpaRepository<BdTier, String> {

    List<BdTier> findAllByOrderByMinSalaryAsc();

    boolean existsByMinSalary(BigDecimal minSalary);
}
