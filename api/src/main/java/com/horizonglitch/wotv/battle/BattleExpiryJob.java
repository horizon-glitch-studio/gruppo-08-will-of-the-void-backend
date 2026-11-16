package com.horizonglitch.wotv.battle;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires the challenges left pending past their deadline (MODE-06) and deletes the replays older than the
 * retention (MATCH-18). Each challenge is expired in its own transaction, with the battle locked, so an acceptance
 * at the same instant either happens first or finds it expired.
 */
@Component
public class BattleExpiryJob {

	private final BattleRepository battles;
	private final BattleService battleService;
	private final Clock clock;

	public BattleExpiryJob(BattleRepository battles, BattleService battleService, Clock clock) {
		this.battles = battles;
		this.battleService = battleService;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.battles.expiry-check-interval:PT1M}")
	public void expireDueChallenges() {
		for (Long id : battles.findExpiredPendingIds(clock.instant())) {
			battleService.expireIfDue(id);
		}
	}

	@Scheduled(fixedDelayString = "${app.battles.replay-cleanup-interval:PT6H}")
	public void deleteOldReplays() {
		battleService.deleteOldReplays();
	}
}
