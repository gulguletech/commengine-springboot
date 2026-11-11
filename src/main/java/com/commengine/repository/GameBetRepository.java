package com.commengine.repository;

import com.commengine.entity.GameBet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface GameBetRepository extends JpaRepository<GameBet, String> {

    List<GameBet> findByUserIdAndGameAndRound(String userId, String game, long round);

    /** How many bets (one chip tap = one row) this player already placed in a round. */
    long countByUserIdAndGameAndRound(String userId, String game, long round);

    @Query("select coalesce(sum(b.amount), 0) from GameBet b where b.userId = :uid and b.game = :game and b.round = :round")
    long sumAmount(@Param("uid") String uid, @Param("game") String game, @Param("round") long round);

    @Query("select b from GameBet b where b.game = :game and b.settled = false and b.round <= :maxRound order by b.round asc")
    List<GameBet> findUnsettled(@Param("game") String game, @Param("maxRound") long maxRound, Pageable pageable);

    // Only one caller can flip settled false -> true, so a bet is paid at most once.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GameBet b set b.settled = true, b.payout = :payout where b.id = :id and b.settled = false")
    int markSettled(@Param("id") String id, @Param("payout") long payout);

    // ---- Galaxy Feast: in-game history, daily revenue rank, per-round ranking ----------------

    List<GameBet> findByUserIdAndGameAndSettledTrueOrderByRoundDescCreatedAtAsc(String userId, String game, Pageable pageable);

    @Query("select coalesce(sum(b.payout), 0) from GameBet b where b.userId = :uid and b.game = :game and b.settled = true and b.createdAt >= :from")
    long sumPayoutSince(@Param("uid") String uid, @Param("game") String game, @Param("from") Instant from);

    /** rows of [userId, totalPayout], best first */
    @Query("select b.userId, sum(b.payout) from GameBet b where b.game = :game and b.settled = true and b.createdAt >= :from group by b.userId having sum(b.payout) > 0 order by sum(b.payout) desc")
    List<Object[]> topRevenue(@Param("game") String game, @Param("from") Instant from, Pageable pageable);

    /** rows of [userId, totalPayout] for one round, best first */
    @Query("select b.userId, sum(b.payout) from GameBet b where b.game = :game and b.round = :round and b.settled = true group by b.userId having sum(b.payout) > 0 order by sum(b.payout) desc")
    List<Object[]> roundRevenue(@Param("game") String game, @Param("round") long round, Pageable pageable);
}
