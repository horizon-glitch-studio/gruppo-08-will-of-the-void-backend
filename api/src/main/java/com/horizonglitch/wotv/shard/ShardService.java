package com.horizonglitch.wotv.shard;

import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.notification.NotificationService;
import com.horizonglitch.wotv.notification.NotificationType;
import com.horizonglitch.wotv.player.Player;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shard income: every change is a {@link ShardTransaction} row plus an atomic update of the balance, in the
 * same transaction. Amounts are multiples of the daily bonus (app.economy.*).
 */
@Service
public class ShardService {

	private final ShardTransactionRepository transactions;
	private final NotificationService notifications;
	private final AppProperties.Economy economy;
	private final Clock clock;

	public ShardService(ShardTransactionRepository transactions, NotificationService notifications,
			AppProperties properties, Clock clock) {
		this.transactions = transactions;
		this.notifications = notifications;
		this.economy = properties.economy();
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void grantRegistrationBonus(Player player) {
		grant(player, ShardReason.REGISTRATION_BONUS, economy.dailyBonus() * economy.registrationBonusMultiplier());
	}

	/**
	 * Daily bonus at the first login of the day in the player's time zone, and comeback bonus after at least
	 * {@code comebackAbsenceDays} without logging in, at most once every {@code comebackIntervalDays}.
	 * The caller must hold the lock on the player's row, so two simultaneous logins cannot both collect them.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public LoginBonuses grantLoginBonuses(Player lockedPlayer) {
		Instant now = clock.instant();
		ZoneId zone = ZoneId.of(lockedPlayer.getTimezone());
		Instant last = lockedPlayer.getLastDailyBonusAt();

		boolean dailyDue = last == null || LocalDate.ofInstant(last, zone).isBefore(LocalDate.ofInstant(now, zone));
		if (!dailyDue) {
			return LoginBonuses.none();
		}

		int comeback = 0;
		boolean longAbsence = last != null && !last.isAfter(now.minus(Duration.ofDays(economy.comebackAbsenceDays())));
		if (longAbsence && !transactions.existsByPlayer_IdAndReasonAndCreatedAtAfter(lockedPlayer.getId(),
				ShardReason.COMEBACK_BONUS, now.minus(Duration.ofDays(economy.comebackIntervalDays())))) {
			comeback = economy.dailyBonus() * economy.comebackBonusMultiplier();
			grant(lockedPlayer, ShardReason.COMEBACK_BONUS, comeback);
			notifications.notify(lockedPlayer, NotificationType.COMEBACK_BONUS);
		}

		int daily = economy.dailyBonus();
		grant(lockedPlayer, ShardReason.DAILY_BONUS, daily);
		lockedPlayer.setLastDailyBonusAt(now);
		return new LoginBonuses(daily, comeback);
	}

	private void grant(Player player, ShardReason reason, int amount) {
		ShardTransaction transaction = new ShardTransaction();
		transaction.setPlayer(player);
		transaction.setReason(reason);
		transaction.setAmount(amount);
		transactions.save(transaction);
		transactions.addToBalance(player.getId(), amount);
	}
}
