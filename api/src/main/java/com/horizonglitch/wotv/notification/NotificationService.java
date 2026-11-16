package com.horizonglitch.wotv.notification;

import com.horizonglitch.wotv.player.Player;
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
}
