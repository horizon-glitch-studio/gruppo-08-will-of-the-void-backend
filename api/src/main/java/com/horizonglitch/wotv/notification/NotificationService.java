package com.horizonglitch.wotv.notification;

import com.horizonglitch.wotv.battle.Battle;
import com.horizonglitch.wotv.player.Player;
import com.horizonglitch.wotv.trade.Trade;
import org.springframework.stereotype.Service;

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
}
