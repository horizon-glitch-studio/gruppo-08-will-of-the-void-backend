package com.horizonglitch.wotv.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

	/** True when the two players are friends: an ACCEPTED friendship in either direction. */
	@Query("""
			select count(f) > 0 from Friendship f
			where f.status = com.horizonglitch.wotv.social.FriendshipStatus.ACCEPTED
			and ((f.requester.id = :a and f.addressee.id = :b) or (f.requester.id = :b and f.addressee.id = :a))
			""")
	boolean areFriends(@Param("a") Long a, @Param("b") Long b);

	@Query("""
			select f from Friendship f where f.status = com.horizonglitch.wotv.social.FriendshipStatus.ACCEPTED
			and (f.requester.id = :playerId or f.addressee.id = :playerId)
			""")
	Page<Friendship> findAccepted(@Param("playerId") long playerId, Pageable pageable);

	@Query("""
			select f from Friendship f where f.status = com.horizonglitch.wotv.social.FriendshipStatus.PENDING
			and ((:direction = 'incoming' and f.addressee.id = :playerId)
			or (:direction = 'outgoing' and f.requester.id = :playerId))
			""")
	Page<Friendship> findPending(@Param("playerId") long playerId, @Param("direction") String direction, Pageable pageable);

	@Query("select count(f) from Friendship f where f.status = com.horizonglitch.wotv.social.FriendshipStatus.ACCEPTED and (f.requester.id = :id or f.addressee.id = :id)")
	long countFriends(@Param("id") long playerId);

	long countByRequester_IdAndStatus(long requesterId, FriendshipStatus status);

	@Query("select f from Friendship f where f.id = :id")
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Friendship> lockById(@Param("id") long id);

	@Query("select count(f) > 0 from Friendship f where (f.requester.id = :a and f.addressee.id = :b) or (f.requester.id = :b and f.addressee.id = :a)")
	boolean existsBetween(@Param("a") long a, @Param("b") long b);
}
