package com.commengine.repository;

import com.commengine.entity.WalletTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** Wallet history without the given types (game bets/wins are hidden from the wallet). */
    List<WalletTransaction> findByUserIdAndTypeNotInOrderByCreatedAtDesc(String userId, Collection<String> types, Pageable pageable);

    boolean existsByUserIdAndTypeAndRef(String userId, String type, String ref);
}
