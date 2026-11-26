package com.commengine.repository;

import com.commengine.entity.DirectHost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DirectHostRepository extends JpaRepository<DirectHost, String> {

    List<DirectHost> findBySuperAdminId(String superAdminId);
}
