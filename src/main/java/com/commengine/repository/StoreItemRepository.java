package com.commengine.repository;

import com.commengine.entity.StoreCategory;
import com.commengine.entity.StoreItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreItemRepository extends JpaRepository<StoreItem, String> {

    List<StoreItem> findByCategoryAndActiveTrueOrderBySortOrderAscCreatedAtAsc(StoreCategory category);

    List<StoreItem> findAllByOrderByCategoryAscSortOrderAscCreatedAtAsc();

    List<StoreItem> findByCategoryOrderBySortOrderAscCreatedAtAsc(StoreCategory category);
}
