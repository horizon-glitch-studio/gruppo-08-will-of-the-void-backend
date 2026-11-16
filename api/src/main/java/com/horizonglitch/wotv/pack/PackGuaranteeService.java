package com.horizonglitch.wotv.pack;

import com.horizonglitch.wotv.player.Player;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The guarantee counters of each Summoner (PACK-08 to PACK-10). */
@Service
public class PackGuaranteeService {

	private final PackGuaranteeRepository guarantees;

	public PackGuaranteeService(PackGuaranteeRepository guarantees) {
		this.guarantees = guarantees;
	}

	/** Every Summoner has a counters row from registration, with all counters at 0. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void createFor(Player player) {
		PackGuarantee guarantee = new PackGuarantee();
		guarantee.setPlayer(player);
		guarantees.save(guarantee);
	}
}
