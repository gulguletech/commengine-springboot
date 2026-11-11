package com.commengine.repository;

import com.commengine.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** Wallet history without the given types (game bets/wins are hidden from the wallet). */
    List<WalletTransaction> findByUserIdAndTypeNotInOrderByCreatedAtDesc(String userId, Collection<String> types, Pageable pageable);

    boolean existsByUserIdAndTypeAndRef(String userId, String type, String ref);

    // ---- admin panel ----
    Page<WalletTransaction> findByType(String type, Pageable pageable);

    Page<WalletTransaction> findByUserId(String userId, Pageable pageable);

    long countByTypeAndCreatedAtAfter(String type, Instant after);

    @Query("select coalesce(sum(-t.amount), 0) from WalletTransaction t where t.type = :type and t.createdAt >= :after")
    long sumSpentSince(@Param("type") String type, @Param("after") Instant after);
}
