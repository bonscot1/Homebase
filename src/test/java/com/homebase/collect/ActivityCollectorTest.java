package com.homebase.collect;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ActivityCollectorTest
{
	@Test
	public void readsKillCountMessages()
	{
		assertArrayEquals(new String[]{"Vorkath", "12"}, ActivityCollector.parseKillCount("Your Vorkath kill count is: 12."));
		assertArrayEquals(new String[]{"Chambers of Xeric", "5"},
			ActivityCollector.parseKillCount("Your completed Chambers of Xeric count is: 5."));
		assertArrayEquals(new String[]{"Barrows", "1250"}, ActivityCollector.parseKillCount("Your Barrows chest count is: 1,250."));
		assertArrayEquals(new String[]{"Corrupted Gauntlet", "2"},
			ActivityCollector.parseKillCount("Your Corrupted Gauntlet completion count is: 2."));
		assertArrayEquals(new String[]{"Wintertodt", "100"}, ActivityCollector.parseKillCount("Your subdued Wintertodt count is: 100."));
		assertArrayEquals(new String[]{"K'ril Tsutsaroth", "40"},
			ActivityCollector.parseKillCount("Your K'ril Tsutsaroth kill count is: 40."));
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertNull(ActivityCollector.parseKillCount("Your Seers' Village Rooftop lap count is: 50."));
		assertNull(ActivityCollector.parseKillCount("You have completed 10 medium Treasure Trails."));
		assertNull(ActivityCollector.parseKillCount("Welcome to Old School RuneScape."));
	}

	@Test
	public void picksUsefulFurniture()
	{
		assertTrue(ActivityCollector.isFurniture("Ornate rejuvenation pool"));
		assertTrue(ActivityCollector.isFurniture("Portal Nexus"));
		assertTrue(ActivityCollector.isFurniture("Occult altar"));
		assertTrue(ActivityCollector.isFurniture("Ornate jewellery box"));
		assertFalse(ActivityCollector.isFurniture("Oak chair"));
		assertFalse(ActivityCollector.isFurniture("Portal"));
	}
}
