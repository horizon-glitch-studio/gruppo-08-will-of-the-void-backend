package com.horizonglitch.wotv.notification;

import java.time.Instant;

public record NotificationResponse(Long id, NotificationType type, boolean isRead, Instant createdAt,
		Long battle, Long trade, Long card, Long friendship) {

	public static NotificationResponse of(Notification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.isRead(),
				notification.getCreatedAt(),
				notification.getBattle() == null ? null : notification.getBattle().getId(),
				notification.getTrade() == null ? null : notification.getTrade().getId(),
				notification.getCard() == null ? null : notification.getCard().getId(),
				notification.getFriendship() == null ? null : notification.getFriendship().getId());
	}
}
