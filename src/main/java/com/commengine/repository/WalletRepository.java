package com.commengine.repository;

import com.commengine.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, String> {

    @Modifying
    @Query(value = "INSERT INTO wallets (user_id, coins, beans) VALUES (:uid, :coins, 0) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    int createIfAbsent(@Param("uid") String uid, @Param("coins") long coins);

    // Atomic: the balance can never go below zero, even with concurrent requests.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Wallet w set w.coins = w.coins + :amt where w.userId = :uid and w.coins + :amt >= 0")
    int adjustCoins(@Param("uid") String uid, @Param("amt") long amt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Wallet w set w.beans = w.beans + :amt where w.userId = :uid and w.beans + :amt >= 0")
    int adjustBeans(@Param("uid") String uid, @Param("amt") long amt);
}
