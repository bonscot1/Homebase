package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class KillCountsDataTest
{
	@Test
	public void timesAreWrittenTheWayTheGameWritesThem()
	{
		assertEquals("1:23.40", KillCountsData.formatTime(83.4));
		assertEquals("0:59", KillCountsData.formatTime(59));
		assertEquals("1:02:05", KillCountsData.formatTime(3725));
		assertEquals("25:31.20", KillCountsData.formatTime(1531.2));
	}

	@Test
	public void bestTimesAreFoundHoweverTheBossIsNamed()
	{
		KillCountsData kills = new KillCountsData();
		kills.getBestTimes().put("The Gauntlet", 400.0);
		assertEquals(Double.valueOf(400.0), kills.bestTimeFor("Gauntlet"));
		assertNull(kills.bestTimeFor("Vorkath"));
	}

	@Test
	public void badTimesFromAnotherClientAreKeptInRange()
	{
		KillCountsData kills = new KillCountsData();
		kills.getBestTimes().put("Vorkath", -5.0);
		kills.getBestTimes().put("Zulrah", Double.NaN);
		kills.getBestTimes().put("Nex", 1e12);
		kills.check();
		assertEquals(Double.valueOf(0), kills.getBestTimes().get("Vorkath"));
		assertEquals(Double.valueOf(0), kills.getBestTimes().get("Zulrah"));
		assertEquals(Double.valueOf(KillCountsData.MAX_SECONDS), kills.getBestTimes().get("Nex"));
	}

	@Test
	public void hiscoreAndChatNamesMatch()
	{
		// the hiscores' name first, then what the game's messages call the same boss
		assertEquals(KillCountsData.bossKey("The Gauntlet"), KillCountsData.bossKey("Gauntlet"));
		assertEquals(KillCountsData.bossKey("Barrows Chests"), KillCountsData.bossKey("Barrows"));
		assertEquals(KillCountsData.bossKey("Lunar Chests"), KillCountsData.bossKey("Lunar"));
		assertEquals(KillCountsData.bossKey("Kree'Arra"), KillCountsData.bossKey("Kree'arra"));
		assertEquals(KillCountsData.bossKey("Chambers of Xeric: Challenge Mode"), KillCountsData.bossKey("Chambers of Xeric Challenge Mode"));
		assertNotEquals(KillCountsData.bossKey("Chambers of Xeric"), KillCountsData.bossKey("Chambers of Xeric: Challenge Mode"));
		assertNotEquals(KillCountsData.bossKey("Corrupted Gauntlet"), KillCountsData.bossKey("Gauntlet"));
	}

	@Test
	public void countsAreFoundUnderTheNameAlreadyKept()
	{
		KillCountsData kills = new KillCountsData();
		kills.getCounts().put("Gauntlet", 12);
		assertEquals("Gauntlet", kills.existingName("The Gauntlet"));
		assertEquals(Integer.valueOf(12), kills.countFor("the gauntlet"));
		assertNull(kills.countFor("Zulrah"));
	}
}
