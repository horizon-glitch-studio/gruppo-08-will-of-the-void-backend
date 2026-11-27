package com.horizonglitch.wotv.notification;

import com.horizonglitch.wotv.battle.Battle;
import com.horizonglitch.wotv.card.Card;
import com.horizonglitch.wotv.common.ApiException;
import com.horizonglitch.wotv.common.PageResponse;
import com.horizonglitch.wotv.common.Paging;
import com.horizonglitch.wotv.social.Friendship;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.trade.Trade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the notifications shown when the Summoner comes back. */
@Service
public class NotificationService {

	private final NotificationRepository notifications;

	public NotificationService(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	/** A notification with no linked battle, trade, card or friendship. */
	public Notification notify(Player player, NotificationType type) {
		Notification notification = new Notification();
		notification.setPlayer(player);
		notification.setType(type);
		return notifications.save(notification);
	}

	public Notification notifyCard(Player player, NotificationType type, Card card) {
		Notification notification = new Notification();
		notification.setPlayer(player);
		notification.setType(type);
		notification.setCard(card);
		return notifications.save(notification);
	}

	public Notification notifyFriendship(Player player, NotificationType type, Friendship friendship) {
		Notification notification = new Notification();
		notification.setPlayer(player);
		notification.setType(type);
		notification.setFriendship(friendship);
		return notifications.save(notification);
	}

	/** A notification about a trade: received, accepted, rejected, cancelled, expired or failed. */
	public Notification notifyTrade(Player player, NotificationType type, Trade trade) {
		Notification notification = new Notification();
		notification.setPlayer(player);
		notification.setType(type);
		notification.setTrade(trade);
		return notifications.save(notification);
	}

	/** A notification about a battle: attack received, or challenge received, accepted, rejected, expired, cancelled. */
	public Notification notifyBattle(Player player, NotificationType type, Battle battle) {
		Notification notification = new Notification();
		notification.setPlayer(player);
		notification.setType(type);
		notification.setBattle(battle);
		return notifications.save(notification);
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(long playerId, Boolean unreadOnly, NotificationType type,
			Integer page, Integer size) {
		return PageResponse.of(notifications.findForPlayer(playerId, Boolean.TRUE.equals(unreadOnly), type,
				Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))),
				NotificationResponse::of);
	}

	@Transactional(readOnly = true)
	public long unreadCount(long playerId) {
		return notifications.countUnread(playerId);
	}

	@Transactional
	public NotificationResponse markRead(long playerId, long notificationId, boolean read) {
		Notification notification = notifications.findById(notificationId)
				.orElseThrow(NotificationService::notFound);
		if (!notification.getPlayer().getId().equals(playerId)) {
			throw forbidden();
		}
		notification.setRead(read);
		return NotificationResponse.of(notifications.save(notification));
	}

	@Transactional
	public void markAllRead(long playerId) {
		notifications.markAllRead(playerId);
	}

	@Transactional
	public void delete(long playerId, long notificationId) {
		Notification notification = notifications.findById(notificationId)
				.orElseThrow(NotificationService::notFound);
		if (!notification.getPlayer().getId().equals(playerId)) {
			throw forbidden();
		}
		notifications.delete(notification);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "The notification does not exist.");
	}

	private static ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "NOTIFICATION_FORBIDDEN", "The notification does not belong to the player.");
	}
}
