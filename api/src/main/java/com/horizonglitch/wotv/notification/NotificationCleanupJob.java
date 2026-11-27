package com.horizonglitch.wotv.notification;

import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationCleanupJob {

	private final NotificationRepository notifications;
	private final Clock clock;

	public NotificationCleanupJob(NotificationRepository notifications, Clock clock) {
		this.notifications = notifications;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.notifications.cleanup-check-interval:PT6H}")
	@Transactional
	public void cleanup() {
		notifications.deleteOlderThan(clock.instant().minus(Duration.ofDays(90)));
		notifications.trimToLimit();
	}
}
