package com.horizonglitch.wotv.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
			select f from Friendship f
			where (f.requester.id = :a and f.addressee.id = :b)
			   or (f.requester.id = :b and f.addressee.id = :a)
			""")
	Optional<Friendship> findBetween(@Param("a") Long a, @Param("b") Long b);

	@Query("""
			select count(f) from Friendship f
			where f.status = com.horizonglitch.wotv.social.FriendshipStatus.ACCEPTED
			  and (f.requester.id = :playerId or f.addressee.id = :playerId)
			""")
	long countFriends(@Param("playerId") Long playerId);

	@Query("""
			select count(f) from Friendship f
			where f.status = com.horizonglitch.wotv.social.FriendshipStatus.PENDING
			  and f.requester.id = :playerId
			""")
	long countPendingSent(@Param("playerId") Long playerId);

	@Query("""
			select f from Friendship f
			where f.status = com.horizonglitch.wotv.social.FriendshipStatus.ACCEPTED
			  and (f.requester.id = :playerId or f.addressee.id = :playerId)
			""")
	Page<Friendship> findFriends(@Param("playerId") Long playerId, Pageable pageable);

	@Query("""
			select f from Friendship f
			where f.status = com.horizonglitch.wotv.social.FriendshipStatus.PENDING
			  and (:direction is null
			       or (:direction = 'received' and f.addressee.id = :playerId)
			       or (:direction = 'sent' and f.requester.id = :playerId))
			  and (f.requester.id = :playerId or f.addressee.id = :playerId)
			""")
	Page<Friendship> findRequests(@Param("playerId") Long playerId,
			@Param("direction") String direction, Pageable pageable);
}
