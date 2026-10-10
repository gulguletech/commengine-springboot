package com.commengine.repository;

import com.commengine.entity.WalletTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    boolean existsByUserIdAndTypeAndRef(String userId, String type, String ref);
}
