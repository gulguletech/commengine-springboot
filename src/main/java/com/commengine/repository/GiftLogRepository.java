package com.commengine.repository;

import com.commengine.entity.GiftLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface GiftLogRepository extends JpaRepository<GiftLog, String> {

    /** Star list: [receiverId, totalCoins] rows, biggest first. */
    @Query("select g.receiverId, sum(g.coins) from GiftLog g where g.createdAt >= :since "
            + "group by g.receiverId order by sum(g.coins) desc, g.receiverId asc")
    List<Object[]> topReceivers(@Param("since") Instant since, Pageable page);

    /** Contribution list: [senderId, totalCoins] rows, biggest first. */
    @Query("select g.senderId, sum(g.coins) from GiftLog g where g.createdAt >= :since "
            + "group by g.senderId order by sum(g.coins) desc, g.senderId asc")
    List<Object[]> topSenders(@Param("since") Instant since, Pageable page);

    @Query("select coalesce(sum(g.coins), 0) from GiftLog g where g.receiverId = :uid and g.createdAt >= :since")
    long receivedSince(@Param("uid") String uid, @Param("since") Instant since);

    @Query("select coalesce(sum(g.coins), 0) from GiftLog g where g.senderId = :uid and g.createdAt >= :since")
    long sentSince(@Param("uid") String uid, @Param("since") Instant since);

    /** How many people received more than `mine` in the period (rank = this + 1). */
    @Query(value = "select count(*) from (select receiver_id from gift_logs where created_at >= :since "
            + "group by receiver_id having sum(coins) > :mine) t", nativeQuery = true)
    long receiversAbove(@Param("since") Instant since, @Param("mine") long mine);

    @Query(value = "select count(*) from (select sender_id from gift_logs where created_at >= :since "
            + "group by sender_id having sum(coins) > :mine) t", nativeQuery = true)
    long sendersAbove(@Param("since") Instant since, @Param("mine") long mine);

    /** Coins received by any of these users inside [from, to). */
    @Query("select coalesce(sum(g.coins), 0) from GiftLog g where g.receiverId in :ids and g.createdAt >= :from and g.createdAt < :to")
    long sumReceivedByIds(@Param("ids") java.util.Collection<String> ids, @Param("from") Instant from, @Param("to") Instant to);

    /** [receiverId, coins] per receiver inside [from, to). */
    @Query("select g.receiverId, sum(g.coins) from GiftLog g where g.receiverId in :ids and g.createdAt >= :from and g.createdAt < :to group by g.receiverId")
    List<Object[]> sumByReceiver(@Param("ids") java.util.Collection<String> ids, @Param("from") Instant from, @Param("to") Instant to);

    /** [createdAt, coins] rows, grouped by day in Java. */
    @Query("select g.createdAt, g.coins from GiftLog g where g.receiverId in :ids and g.createdAt >= :from and g.createdAt < :to")
    List<Object[]> rowsReceivedByIds(@Param("ids") java.util.Collection<String> ids, @Param("from") Instant from, @Param("to") Instant to);
}
