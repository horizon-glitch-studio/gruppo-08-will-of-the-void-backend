package com.horizonglitch.wotv.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

	/** True when the two players are friends: an ACCEPTED friendship in either direction. */
	@Query("""
			select count(f) > 0 from Friendship f
			where f.status = com.horizonglitch.wotv.social.FriendshipStatus.ACCEPTED
			and ((f.requester.id = :a and f.addressee.id = :b) or (f.requester.id = :b and f.addressee.id = :a))
			""")
	boolean areFriends(@Param("a") Long a, @Param("b") Long b);
}
