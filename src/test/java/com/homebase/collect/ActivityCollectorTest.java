package com.homebase.collect;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
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
	public void readsKillCountsAndRanksFromTheHiscores()
	{
		Map<HiscoreSkill, Skill> hiscores = new EnumMap<>(HiscoreSkill.class);
		hiscores.put(HiscoreSkill.OVERALL, new Skill(152_334, 1_870, 98_000_000L));
		hiscores.put(HiscoreSkill.ATTACK, new Skill(90_120, 99, 13_100_000L));
		hiscores.put(HiscoreSkill.CLUE_SCROLL_ALL, new Skill(41_002, 212, -1));
		hiscores.put(HiscoreSkill.VORKATH, new Skill(20_455, 340, -1));
		hiscores.put(HiscoreSkill.ZULRAH, new Skill(-1, -1, -1));

		Map<String, Integer> kills = ActivityCollector.hiscoreKills(hiscores);
		assertEquals(1, kills.size());
		assertEquals(Integer.valueOf(340), kills.get("Vorkath"));

		Map<String, Integer> ranks = ActivityCollector.hiscoreRanks(hiscores);
		assertEquals(4, ranks.size());
		assertEquals(Integer.valueOf(152_334), ranks.get(HiscoreSkill.OVERALL.getName()));
		assertEquals(Integer.valueOf(90_120), ranks.get(HiscoreSkill.ATTACK.getName()));
		assertEquals(Integer.valueOf(41_002), ranks.get(HiscoreSkill.CLUE_SCROLL_ALL.getName()));
		assertEquals(Integer.valueOf(20_455), ranks.get("Vorkath"));
		assertFalse(ranks.containsKey(HiscoreSkill.ZULRAH.getName()));
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
