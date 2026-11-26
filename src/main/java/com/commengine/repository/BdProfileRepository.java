package com.commengine.repository;

import com.commengine.entity.BdProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BdProfileRepository extends JpaRepository<BdProfile, String> {

    List<BdProfile> findBySuperAdminId(String superAdminId);
}
