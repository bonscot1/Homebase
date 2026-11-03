package com.homebase.updates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.homebase.data.CombatAchievementsData;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ActivityDiffTest
{
	@Test
	public void combatAchievementTasksAndTier()
	{
		assertEquals(Arrays.asList("completed 3 combat achievements", "reached Medium combat achievements"),
			UpdateDiff.combatAchievements(10, 13, 0, 1, CombatAchievementsData.TIERS));
		assertEquals(Collections.singletonList("completed a combat achievement"),
			UpdateDiff.combatAchievements(10, 11, 1, 1, CombatAchievementsData.TIERS));
	}

	@Test
	public void noCombatAchievementLineFromNothing()
	{
		// tasks that hadn't loaded yet shouldn't look like a huge burst of completions
		assertTrue(UpdateDiff.combatAchievements(0, 300, -1, 4, CombatAchievementsData.TIERS).isEmpty());
	}

	@Test
	public void newCollectionLogSlotsByName()
	{
		List<String> before = Collections.singletonList("Abyssal whip");
		List<String> after = Arrays.asList("Dragon pickaxe", "Draconic visage", "Abyssal whip");
		assertEquals(Arrays.asList("new collection log slot: Draconic visage", "new collection log slot: Dragon pickaxe"),
			UpdateDiff.collectionLog(100, 102, before, after));
	}

	@Test
	public void collectionLogFallsBackToCount()
	{
		assertEquals(Collections.singletonList("filled 3 collection log slots"),
			UpdateDiff.collectionLog(100, 103, Collections.emptyList(), Collections.emptyList()));
		assertTrue(UpdateDiff.collectionLog(0, 103, Collections.emptyList(), Collections.emptyList()).isEmpty());
	}

	@Test
	public void killCountsThatWentUp()
	{
		Map<String, Integer> before = new HashMap<>();
		before.put("Vorkath", 10);
		before.put("Zulrah", 5);
		Map<String, Integer> after = new HashMap<>(before);
		after.put("Vorkath", 12);
		after.put("Barrows", 300);
		// Barrows is new to us, so its whole count isn't announced
		assertEquals(Collections.singletonList("Vorkath kill count 10 → 12"), UpdateDiff.killCounts(before, after));
	}

	@Test
	public void manyKillCountsAreSummarised()
	{
		Map<String, Integer> before = new HashMap<>();
		Map<String, Integer> after = new HashMap<>();
		for (int i = 0; i < 6; i++)
		{
			before.put("Boss " + i, 1);
			after.put("Boss " + i, 2);
		}
		assertEquals(Collections.singletonList("kill counts went up for 6 bosses"), UpdateDiff.killCounts(before, after));
	}
}
