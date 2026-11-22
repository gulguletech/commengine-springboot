package com.commengine.repository;

import com.commengine.entity.AgencyInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgencyInviteRepository extends JpaRepository<AgencyInvite, String> {

    List<AgencyInvite> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

    List<AgencyInvite> findByAgencyIdAndStatusOrderByCreatedAtDesc(String agencyId, String status);

    Optional<AgencyInvite> findByAgencyIdAndUserIdAndStatus(String agencyId, String userId, String status);

    long countByAgencyIdAndStatus(String agencyId, String status);

    void deleteByAgencyId(String agencyId);

    @Modifying
    @Query("update AgencyInvite i set i.status = 'CANCELLED', i.respondedAt = :now where i.userId = :userId and i.status = 'PENDING'")
    int cancelPendingForUser(@Param("userId") String userId, @Param("now") java.time.Instant now);

    default int cancelPendingForUser(String userId) {
        return cancelPendingForUser(userId, java.time.Instant.now());
    }
}
