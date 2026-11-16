package com.horizonglitch.wotv.trade;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

	/** Locks the trade, so accept, reject, cancel and expiry of the same trade happen one at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Trade t where t.id = :id")
	Optional<Trade> lockById(@Param("id") Long id);

	/** The pending trade between two players, in either direction (uq_trade_pending_pair). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select t from Trade t where t.status = com.horizonglitch.wotv.trade.TradeStatus.PENDING
			and ((t.proposer.id = :a and t.recipient.id = :b) or (t.proposer.id = :b and t.recipient.id = :a))
			""")
	Optional<Trade> lockPendingBetween(@Param("a") Long a, @Param("b") Long b);

	@Query("select t.id from Trade t where t.status = com.horizonglitch.wotv.trade.TradeStatus.PENDING "
			+ "and t.expiresAt <= :now order by t.id")
	List<Long> findExpiredPendingIds(@Param("now") Instant now);

	@Query("select t from Trade t where t.status = com.horizonglitch.wotv.trade.TradeStatus.PENDING "
			+ "and (t.proposer.id = :playerId or t.recipient.id = :playerId)")
	List<Trade> findPendingOf(@Param("playerId") Long playerId);

}
