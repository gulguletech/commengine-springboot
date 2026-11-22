package com.commengine.repository;

import com.commengine.entity.Agency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgencyRepository extends JpaRepository<Agency, String> {

    boolean existsByCode(String code);

    Optional<Agency> findByOwnerId(String ownerId);

    @Query("select a from Agency a where :q = '' or lower(a.name) like lower(concat('%', :q, '%')) or lower(a.code) like lower(concat('%', :q, '%'))")
    Page<Agency> adminSearch(@Param("q") String q, Pageable pageable);
}
