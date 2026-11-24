package com.commengine.repository;

import com.commengine.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {

    /** Row lock so the webhook and the app's verify call can never credit the same order twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.orderId = :id")
    Optional<PaymentOrder> findForUpdate(@Param("id") String id);

    /** [paidAt, coins, amountInr] of PAID orders by these users inside [from, to). */
    @Query("select o.paidAt, o.coins, o.amountInr from PaymentOrder o where o.status = 'PAID' and o.userId in :ids and o.paidAt >= :from and o.paidAt < :to")
    java.util.List<Object[]> paidRows(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids,
                                      @org.springframework.data.repository.query.Param("from") java.time.Instant from,
                                      @org.springframework.data.repository.query.Param("to") java.time.Instant to);
}
