package com.commengine.repository;

import com.commengine.entity.Gift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GiftRepository extends JpaRepository<Gift, String> {

    List<Gift> findByActiveTrueOrderBySortOrderAscPriceAscCreatedAtAsc();

    List<Gift> findAllByOrderBySortOrderAscPriceAscCreatedAtAsc();
}
