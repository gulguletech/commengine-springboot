package com.commengine.repository;

import com.commengine.entity.GameBet;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameBetRepository extends JpaRepository<GameBet, String> {

    List<GameBet> findByUserIdAndGameAndRound(String userId, String game, long round);

    @Query("select coalesce(sum(b.amount), 0) from GameBet b where b.userId = :uid and b.game = :game and b.round = :round")
    long sumAmount(@Param("uid") String uid, @Param("game") String game, @Param("round") long round);

    @Query("select b from GameBet b where b.game = :game and b.settled = false and b.round <= :maxRound order by b.round asc")
    List<GameBet> findUnsettled(@Param("game") String game, @Param("maxRound") long maxRound, Pageable pageable);

    // Only one caller can flip settled false -> true, so a bet is paid at most once.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update GameBet b set b.settled = true, b.payout = :payout where b.id = :id and b.settled = false")
    int markSettled(@Param("id") String id, @Param("payout") long payout);
}
