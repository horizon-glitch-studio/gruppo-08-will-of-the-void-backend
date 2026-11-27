package com.horizonglitch.wotv.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@Query("""
			select n from Notification n
			where n.player.id = :playerId
			  and (:unreadOnly = false or n.read = false)
			  and (:type is null or n.type = :type)
			""")
	Page<Notification> findForPlayer(@Param("playerId") Long playerId,
			@Param("unreadOnly") boolean unreadOnly, @Param("type") NotificationType type, Pageable pageable);

	@Query("select count(n) from Notification n where n.player.id = :playerId and n.read = false")
	long countUnread(@Param("playerId") Long playerId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Notification n set n.read = true where n.player.id = :playerId and n.read = false")
	int markAllRead(@Param("playerId") Long playerId);

	@Modifying(flushAutomatically = true)
	@Query("delete from Notification n where n.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") Instant cutoff);

	@Modifying(flushAutomatically = true)
	@Query(value = """
			WITH ranked AS (
			  SELECT id, count(*) OVER (PARTITION BY player_id) AS total,
			         row_number() OVER (PARTITION BY player_id
			            ORDER BY is_read DESC, created_at ASC, id ASC) AS position
			  FROM notification
			)
			DELETE FROM notification
			WHERE id IN (SELECT id FROM ranked WHERE total > 500 AND position <= total - 500)
			""", nativeQuery = true)
	int trimToLimit();
}
