package com.commengine.repository;

import com.commengine.entity.OfficialGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficialGrantRepository extends JpaRepository<OfficialGrant, String> {

    /** Newest first; the service drops expired ones. */
    List<OfficialGrant> findTop500ByRevokedAtIsNullOrderByCreatedAtDesc();
}
