package com.commengine.repository;

import com.commengine.entity.AgencyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgencyMemberRepository extends JpaRepository<AgencyMember, String> {

    Optional<AgencyMember> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<AgencyMember> findByAgencyIdOrderByJoinedAtAsc(String agencyId);

    long countByAgencyId(String agencyId);

    List<AgencyMember> findByAgencyIdIn(java.util.Collection<String> agencyIds);

    void deleteByAgencyId(String agencyId);
}
