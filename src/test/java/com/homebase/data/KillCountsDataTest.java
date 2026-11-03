package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class KillCountsDataTest
{
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
