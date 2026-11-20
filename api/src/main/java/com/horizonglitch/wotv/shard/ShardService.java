package com.horizonglitch.wotv.shard;

import com.horizonglitch.wotv.battle.BattleStatsService;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.config.AppProperties;
import com.horizonglitch.wotv.notification.NotificationService;
import com.horizonglitch.wotv.notification.NotificationType;
import com.horizonglitch.wotv.pack.PackOpening;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.player.PlayerRepository;
import com.horizonglitch.wotv.season.Orbit;
import com.horizonglitch.wotv.season.Season;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shard income: every change is a {@link ShardTransaction} row plus an atomic update of the balance, in the
 * same transaction. Amounts are multiples of the daily bonus (app.economy.*).
 */
@Service
public class ShardService {

	private record Credit(ShardReason reason, int amount, Season season, Short orbit, Player invitedPlayer,
			NotificationType notificationType) {
	}

	private final ShardTransactionRepository transactions;
	private final PlayerRepository players;
	private final BattleStatsService battleStats;
	private final NotificationService notifications;
	private final AppProperties.Economy economy;
	private final Clock clock;

	public ShardService(ShardTransactionRepository transactions, PlayerRepository players, BattleStatsService battleStats, NotificationService notifications,
			AppProperties properties, Clock clock) {
		this.transactions = transactions;
		this.players = players;
		this.battleStats = battleStats;
		this.notifications = notifications;
		this.economy = properties.economy();
		this.clock = clock;
	}

	/** ECO-10: awards an inviter after the invited Summoner verifies email and plays against 10 distinct opponents. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void grantInviteBonusIfEligible(Player invitedPlayer) {
		Player inviter = invitedPlayer.getInvitedBy();
		if (inviter == null || invitedPlayer.getEmailVerifiedAt() == null
				|| battleStats.distinctPlayedOpponents(invitedPlayer.getId()) < 10
				|| transactions.existsByInvitedPlayer_Id(invitedPlayer.getId())) {
			return;
		}

		grant(inviter, ShardReason.INVITE_BONUS, economy.dailyBonus() * economy.inviteBonusMultiplier(),
				null, null, invitedPlayer, NotificationType.INVITE_BONUS);
	}

	/** ECO-07: grants the verification bonus once, when the email verification token is consumed. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void grantEmailVerificationBonus(Player player) {
		if (transactions.existsByPlayer_IdAndReason(player.getId(), ShardReason.EMAIL_BONUS)) {
			return;
		}
		grant(player, ShardReason.EMAIL_BONUS, economy.dailyBonus() * economy.emailBonusMultiplier());
	}

	/** RANK-11: grants a reached Orbit once per Summoner and season. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void grantOrbitRewardIfEligible(Player player, Season season, Orbit reachedOrbit) {
		short orbit = (short) (reachedOrbit.ordinal() + 1);
		if (reachedOrbit == Orbit.ORBIT_1 || transactions.existsByPlayer_IdAndReasonAndSeason_IdAndOrbit(
				player.getId(), ShardReason.ORBIT_REWARD, season.getId(), orbit)) {
			return;
		}

		grant(player, ShardReason.ORBIT_REWARD, economy.dailyBonus() * economy.orbitRewardMultiplier(), season, orbit,
				null, NotificationType.ORBIT_REWARD);
	}

	/** RANK-09: rewards active Summoners according to their Orbit before the season reset. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void grantSeasonReward(Player player, Season season, Orbit finalOrbit) {
		if (transactions.existsByPlayer_IdAndReasonAndSeason_Id(player.getId(), ShardReason.SEASON_REWARD,
				season.getId())) {
			return;
		}
		int multiplier = switch (finalOrbit) {
			case ORBIT_1 -> economy.seasonRewardOrbit1Multiplier();
			case ORBIT_2 -> economy.seasonRewardOrbit2Multiplier();
			case ORBIT_3 -> economy.seasonRewardOrbit3Multiplier();
			case ORBIT_4 -> economy.seasonRewardOrbit4Multiplier();
		};
		grant(player, ShardReason.SEASON_REWARD, economy.dailyBonus() * multiplier, season, null, null,
				NotificationType.SEASON_REWARD);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void grantRegistrationBonus(Player player) {
		grant(player, ShardReason.REGISTRATION_BONUS, economy.dailyBonus() * economy.registrationBonusMultiplier());
	}

	/**
	 * Daily bonus at the first login or claim of the day in the player's time zone, and comeback bonus after at
	 * least {@code comebackAbsenceDays} without authenticated activity, at most once every
	 * {@code comebackIntervalDays}.
	 * The caller must hold the lock on the player's row, so two simultaneous logins cannot both collect them.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public LoginBonuses grantLoginBonuses(Player lockedPlayer) {
		Instant now = clock.instant();
		ZoneId zone = ZoneId.of(lockedPlayer.getTimezone());
		Instant lastDailyBonus = lockedPlayer.getLastDailyBonusAt();

		boolean dailyDue = lastDailyBonus == null
				|| LocalDate.ofInstant(lastDailyBonus, zone).isBefore(LocalDate.ofInstant(now, zone));
		if (!dailyDue) {
			return LoginBonuses.none();
		}

		int comeback = 0;
		if (lockedPlayer.isComebackEligible()
				&& !transactions.existsByPlayer_IdAndReasonAndCreatedAtAfter(lockedPlayer.getId(),
				ShardReason.COMEBACK_BONUS, now.minus(Duration.ofDays(economy.comebackIntervalDays())))) {
			comeback = economy.dailyBonus() * economy.comebackBonusMultiplier();
			grantComebackBonus(lockedPlayer, comeback);
		}

		int daily = economy.dailyBonus();
		grantDailyBonus(lockedPlayer, daily);
		lockedPlayer.setLastDailyBonusAt(now);
		players.clearComebackEligibility(lockedPlayer.getId());
		lockedPlayer.setComebackEligible(false);
		return new LoginBonuses(daily, comeback);
	}

	/**
	 * Charges the pack with an atomic update that never takes the balance below 0, and records the PACK_PURCHASE
	 * row linked to the opening. Throws 409 INSUFFICIENT_SHARDS, with balance and cost, when the balance is too
	 * low: the caller's transaction is rolled back.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void chargePack(Player player, PackOpening opening, int cost) {
		if (transactions.addToBalance(player.getId(), -cost) == 0) {
			int balance = transactions.balanceOf(player.getId());
			throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_SHARDS", "Not enough Shards to buy this pack.",
					Map.of("shardBalance", balance, "shardCost", cost));
		}
		ShardTransaction transaction = new ShardTransaction();
		transaction.setPlayer(player);
		transaction.setReason(ShardReason.PACK_PURCHASE);
		transaction.setAmount(-cost);
		transaction.setPackOpening(opening);
		transactions.save(transaction);
	}

	/** The balance in the database, which the Player entity does not follow (see Player). */
	@Transactional(readOnly = true)
	public int balanceOf(long playerId) {
		return transactions.balanceOf(playerId);
	}

	/** Every Shard income and expense of the player, the most recent first. */
	@Transactional(readOnly = true)
	public PageResponse<ShardResponses.Transaction> history(long playerId, Integer page, Integer size) {
		return PageResponse.of(transactions.findByPlayer_Id(playerId,
				Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))),
				ShardResponses.Transaction::of);
	}

	private void grant(Player player, ShardReason reason, int amount) {
		grant(player, new Credit(reason, amount, null, null, null, null));
	}

	private void grant(Player player, ShardReason reason, int amount, Season season, Short orbit,
			Player invitedPlayer, NotificationType notificationType) {
		grant(player, new Credit(reason, amount, season, orbit, invitedPlayer, notificationType));
	}

	private void grantComebackBonus(Player player, int amount) {
		grant(player, new Credit(ShardReason.COMEBACK_BONUS, amount, null, null, null,
				NotificationType.COMEBACK_BONUS));
	}

	private void grantDailyBonus(Player player, int amount) {
		grant(player, new Credit(ShardReason.DAILY_BONUS, amount, null, null, null, null));
	}

	/** Writes one history movement and performs one atomic balance update for its reason. */
	private void grant(Player player, Credit credit) {
		ShardTransaction transaction = new ShardTransaction();
		transaction.setPlayer(player);
		transaction.setReason(credit.reason());
		transaction.setAmount(credit.amount());
		transaction.setSeason(credit.season());
		transaction.setOrbit(credit.orbit());
		transaction.setInvitedPlayer(credit.invitedPlayer());
		transactions.save(transaction);
		transactions.addToBalance(player.getId(), credit.amount());
		if (credit.notificationType() != null) {
			notifications.notify(player, credit.notificationType());
		}
	}
}
