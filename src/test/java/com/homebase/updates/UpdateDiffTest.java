package com.homebase.updates;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class UpdateDiffTest
{
	private static final List<String> REGIONS = Arrays.asList("Varrock", "Wilderness");
	private static final List<String> TIERS = Arrays.asList("Easy", "Medium", "Hard", "Elite");

	@Test
	public void theExampleFromTheBrief()
	{
		Map<String, Integer> skillsBefore = new LinkedHashMap<>();
		skillsBefore.put("Attack", 40);
		skillsBefore.put("Magic", 15);
		Map<String, Integer> skillsAfter = new LinkedHashMap<>(skillsBefore);
		skillsAfter.put("Magic", 32);

		Map<String, String> questsBefore = new HashMap<>();
		Map<String, String> questsAfter = new HashMap<>();
		questsAfter.put("The Restless Ghost", "F");

		Map<String, Integer> diariesBefore = new HashMap<>();
		Map<String, Integer> diariesAfter = Collections.singletonMap("Varrock", 0b0001);

		List<String> changes = new java.util.ArrayList<>();
		changes.addAll(UpdateDiff.skills(skillsBefore, skillsAfter));
		changes.addAll(UpdateDiff.quests(questsBefore, questsAfter));
		changes.addAll(UpdateDiff.diaries(diariesBefore, diariesAfter, REGIONS, TIERS));

		assertEquals(Arrays.asList("Magic 15 → 32", "completed The Restless Ghost", "completed the Varrock Easy diary"), changes);
		assertEquals("bonscott1: Magic 15 -> 32, completed The Restless Ghost, completed the Varrock Easy diary.",
			UpdateDiff.chatLine("bonscott1", changes));
	}

	@Test
	public void xpWithoutALevelIsNotNews()
	{
		Map<String, Integer> levels = Collections.singletonMap("Magic", 32);
		assertTrue(UpdateDiff.skills(levels, levels).isEmpty());
	}

	@Test
	public void startingAQuestIsNotNewsButFinishingItIs()
	{
		Map<String, String> before = Collections.singletonMap("Cook's Assistant", "S");
		assertTrue(UpdateDiff.quests(Collections.emptyMap(), before).isEmpty());
		assertEquals(Collections.singletonList("completed Cook's Assistant"),
			UpdateDiff.quests(before, Collections.singletonMap("Cook's Assistant", "F")));
	}

	@Test
	public void lotsOfQuestsAreCounted()
	{
		Map<String, String> after = new HashMap<>();
		for (int i = 0; i < 12; i++)
		{
			after.put("Quest " + i, "F");
		}
		assertEquals(Collections.singletonList("completed 12 quests"), UpdateDiff.quests(Collections.emptyMap(), after));
	}

	@Test
	public void onlyNewDiaryTiersAreListed()
	{
		Map<String, Integer> before = Collections.singletonMap("Varrock", 0b0001);
		Map<String, Integer> after = new HashMap<>();
		after.put("Varrock", 0b0011);
		after.put("Wilderness", 0b0001);
		assertEquals(Arrays.asList("completed the Varrock Medium diary", "completed the Wilderness Easy diary"),
			UpdateDiff.diaries(before, after, REGIONS, TIERS));
	}

	@Test
	public void aSkillMissingFromTheOldCopyIsNotAGain()
	{
		// a skill added in a game update shouldn't read as going from nothing to its level
		assertTrue(UpdateDiff.skills(Collections.emptyMap(), Collections.singletonMap("Sailing", 1)).isEmpty());
	}
}
