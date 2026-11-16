package com.horizonglitch.wotv.trade;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires the trades left pending past their deadline (TRADE-07). Each trade is expired in its own transaction,
 * with the trade locked, so an acceptance at the same instant either happens first or finds it expired.
 */
@Component
public class TradeExpiryJob {

	private final TradeRepository trades;
	private final TradeService tradeService;
	private final Clock clock;

	public TradeExpiryJob(TradeRepository trades, TradeService tradeService, Clock clock) {
		this.trades = trades;
		this.tradeService = tradeService;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.trades.expiry-check-interval:PT1M}")
	public void expireDueTrades() {
		for (Long id : trades.findExpiredPendingIds(clock.instant())) {
			tradeService.expireIfDue(id);
		}
	}
}
