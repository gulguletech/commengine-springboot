package com.commengine.repository;

import com.commengine.entity.AgencyRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgencyRequestRepository extends JpaRepository<AgencyRequest, String> {

    Optional<AgencyRequest> findFirstByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByUserIdAndStatus(String userId, String status);

    long countByStatus(String status);

    @Query("select r from AgencyRequest r where (:status = '' or r.status = :status) and (:q = '' or lower(r.agencyName) like lower(concat('%', :q, '%')) or lower(r.fullName) like lower(concat('%', :q, '%')))")
    Page<AgencyRequest> search(@Param("status") String status, @Param("q") String q, Pageable pageable);
}
