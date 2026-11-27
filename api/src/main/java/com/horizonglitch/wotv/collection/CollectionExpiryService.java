package com.horizonglitch.wotv.collection;

import com.horizonglitch.wotv.deck.DeckService;
import com.horizonglitch.wotv.notification.NotificationService;
import com.horizonglitch.wotv.notification.NotificationType;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionExpiryService {

	private final CollectionCardRepository collection;
	private final MemoryArchiveRepository archive;
	private final DeckService decks;
	private final NotificationService notifications;
	private final Clock clock;

	public CollectionExpiryService(CollectionCardRepository collection, MemoryArchiveRepository archive,
			DeckService decks, NotificationService notifications, Clock clock) {
		this.collection = collection;
		this.archive = archive;
		this.decks = decks;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional
	public void departIfDue(CollectionCardId id) {
		CollectionCard ended = collection.lockById(id).orElse(null);
		if (ended == null || ended.getExpiresAt() == null) {
			return;
		}
		Instant now = clock.instant();
		if (ended.getExpiresAt().isAfter(now)) {
			return;
		}
		MemoryArchive stay = new MemoryArchive();
		stay.setPlayer(ended.getPlayer());
		stay.setCard(ended.getCard());
		stay.setArrivedAt(ended.getAcquiredAt());
		stay.setDepartedAt(ended.getExpiresAt());
		archive.save(stay);
		collection.delete(ended);
		notifications.notifyCard(ended.getPlayer(), NotificationType.WANDERER_DEPARTED, ended.getCard());
		if (decks.recheckAll(ended.getPlayer())) {
			notifications.notify(ended.getPlayer(), NotificationType.DECK_INVALID);
		}
	}
}
