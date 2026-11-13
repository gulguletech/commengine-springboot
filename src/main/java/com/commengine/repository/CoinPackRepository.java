package com.commengine.repository;

import com.commengine.entity.CoinPack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoinPackRepository extends JpaRepository<CoinPack, String> {

    List<CoinPack> findByActiveTrueOrderBySortOrderAscPriceInrAscCreatedAtAsc();

    List<CoinPack> findAllByOrderBySortOrderAscPriceInrAscCreatedAtAsc();

    Optional<CoinPack> findByIdAndActiveTrue(String id);
}
