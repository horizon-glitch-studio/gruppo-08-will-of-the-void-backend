package com.horizonglitch.wotv.collection;

import java.time.Clock;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollectionExpiryJob {

	private final CollectionCardRepository collection;
	private final CollectionExpiryService expiry;
	private final Clock clock;

	public CollectionExpiryJob(CollectionCardRepository collection, CollectionExpiryService expiry, Clock clock) {
		this.collection = collection;
		this.expiry = expiry;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.collection.expiry-check-interval:PT1M}")
	public void departDueWanderers() {
		List<CollectionCardId> ids = collection.findExpired(clock.instant()).stream()
				.map(CollectionCard::getId)
				.toList();
		for (CollectionCardId id : ids) {
			expiry.departIfDue(id);
		}
	}
}
