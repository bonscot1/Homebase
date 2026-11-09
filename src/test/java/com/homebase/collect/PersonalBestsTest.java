package com.homebase.collect;

import com.homebase.data.KillCountsData;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PersonalBestsTest
{
	@Test
	public void raidTimesLoseTheirTeamSize()
	{
		assertEquals("Chambers of Xeric", PersonalBests.bossName("chambers of xeric 3 players"));
		assertEquals("Chambers of Xeric", PersonalBests.bossName("chambers of xeric solo"));
		assertEquals("Theatre of Blood", PersonalBests.bossName("theatre of blood 5 players"));
		assertEquals("Chambers of Xeric", PersonalBests.bossName("chambers of xeric 11-15 players"));
		assertEquals("Vorkath", PersonalBests.bossName("vorkath"));
	}

	@Test
	public void timesGoUnderTheNameTheKillCountHas()
	{
		KillCountsData kills = new KillCountsData();
		kills.getCounts().put("The Gauntlet", 12);
		Map<String, Double> best = new HashMap<>();
		best.put("Gauntlet", 400.0);
		best.put("Vorkath", 83.4);

		assertTrue(PersonalBests.merge(kills, best));
		assertEquals(Double.valueOf(400.0), kills.getBestTimes().get("The Gauntlet"));
		assertEquals(Double.valueOf(83.4), kills.getBestTimes().get("Vorkath"));
		assertFalse("nothing new the second time", PersonalBests.merge(kills, best));
	}

	@Test
	public void aTimeMovesOnceItsKillCountTurnsUp()
	{
		KillCountsData kills = new KillCountsData();
		PersonalBests.merge(kills, Collections.singletonMap("Gauntlet", 400.0));
		kills.getCounts().put("The Gauntlet", 12);

		PersonalBests.merge(kills, Collections.singletonMap("Gauntlet", 390.0));
		assertNull(kills.getBestTimes().get("Gauntlet"));
		assertEquals(Double.valueOf(390.0), kills.getBestTimes().get("The Gauntlet"));
	}
}
