package com.homebase.updates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.homebase.data.CombatAchievementsData;
import com.homebase.data.FeedData;
import com.homebase.data.QuestsData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class UpdateDiffTest
{
	private static final List<String> REGIONS = Arrays.asList("Varrock", "Wilderness");
	private static final List<String> TIERS = Arrays.asList("Easy", "Medium", "Hard", "Elite");

	private static FeedData.Entry drop(long at, String item, int quantity, long value, String source)
	{
		return new FeedData.Entry(at, FeedData.DROP, item, quantity, value, source);
	}

	@Test
	public void lowLevelsCanBeLeftOut()
	{
		Map<String, Integer> before = new LinkedHashMap<>();
		before.put("Attack", 40);
		before.put("Magic", 89);
		Map<String, Integer> after = new LinkedHashMap<>();
		after.put("Attack", 41);
		after.put("Magic", 90);

		assertEquals(Arrays.asList("Attack 40 → 41", "Magic 89 → 90"), UpdateDiff.skills(before, after));
		assertEquals(Collections.singletonList("Magic 89 → 90"), UpdateDiff.skills(before, after, 50));
	}

	@Test
	public void onlyNewFeedEntriesAreReported()
	{
		List<FeedData.Entry> before = Collections.singletonList(drop(100, "Old thing", 1, 500_000, "Goblin"));
		List<FeedData.Entry> after = Arrays.asList(
			before.get(0),
			drop(200, "Dragon warhammer", 1, 41_200_000, "Lizardman shaman"),
			new FeedData.Entry(300, FeedData.PET, null, 0, 0, null),
			new FeedData.Entry(400, FeedData.DEATH, "Wilderness (level 11)", 0, 0, null));

		assertEquals(Arrays.asList("got Dragon warhammer from Lizardman shaman (41.2M)", "got a pet!", "died: Wilderness (level 11)"),
			UpdateDiff.feed(before, after, true, 100_000, true));
	}

	@Test
	public void dropsBelowTheChosenValueAreLeftOut()
	{
		List<FeedData.Entry> after = Arrays.asList(
			drop(100, "Coal", 200, 30_000, "Mining"),
			drop(200, "Runite ore", 10, 110_000, "Rune dragon"));

		assertEquals(Collections.singletonList("got 10 x Runite ore from Rune dragon (110K)"),
			UpdateDiff.feed(Collections.emptyList(), after, true, 100_000, true));
	}

	@Test
	public void dropsAndDeathsCanEachBeTurnedOff()
	{
		List<FeedData.Entry> after = Arrays.asList(
			drop(100, "Abyssal whip", 1, 2_000_000, "Abyssal demon"),
			new FeedData.Entry(200, FeedData.PET, null, 0, 0, null),
			new FeedData.Entry(300, FeedData.DEATH, null, 0, 0, null));

		assertEquals(Collections.singletonList("died"), UpdateDiff.feed(Collections.emptyList(), after, false, 0, true));
		assertEquals(Arrays.asList("got Abyssal whip from Abyssal demon (2M)", "got a pet!"),
			UpdateDiff.feed(Collections.emptyList(), after, true, 0, false));
	}

	@Test
	public void lotsOfDropsAreSummarised()
	{
		List<FeedData.Entry> after = new ArrayList<>();
		for (int i = 1; i <= 7; i++)
		{
			after.add(drop(i, "Item " + i, 1, 200_000, "Boss"));
		}
		assertEquals(Collections.singletonList("got 7 valuable drops"),
			UpdateDiff.feed(Collections.emptyList(), after, true, 100_000, true));
	}

	@Test
	public void aPetIsNeverFoldedIntoACountOfDrops()
	{
		List<FeedData.Entry> after = new ArrayList<>();
		for (int i = 1; i <= 7; i++)
		{
			after.add(drop(i, "Item " + i, 1, 200_000, "Boss"));
		}
		after.add(new FeedData.Entry(8, FeedData.PET, null, 0, 0, null));

		assertEquals(Arrays.asList("got 7 valuable drops", "got a pet!"),
			UpdateDiff.feed(Collections.emptyList(), after, true, 100_000, true));
	}

	@Test
	public void strangeFeedEntriesAreSafelyIgnored()
	{
		List<FeedData.Entry> after = Arrays.asList(
			// a kind of entry from a newer version
			new FeedData.Entry(100, "something new", "x", 1, 1, "y"),
			// no kind at all
			new FeedData.Entry(200, null, "x", 1, 1, "y"),
			// nothing left of the name once cleaned
			drop(300, "<>", 1, 500_000, null),
			// tags in what another client sent don't reach the chat box
			drop(400, "<img=1>Sword", 1, 500_000, "<col=ff0000>Boss"));

		assertEquals(Collections.singletonList("got img=1Sword from col=ff0000Boss (500K)"),
			UpdateDiff.feed(Collections.emptyList(), after, true, 100_000, true));
	}

	@Test
	public void skillQuestAndDiaryChangesMakeOneChatLine()
	{
		Map<String, Integer> skillsBefore = new LinkedHashMap<>();
		skillsBefore.put("Attack", 40);
		skillsBefore.put("Magic", 15);
		Map<String, Integer> skillsAfter = new LinkedHashMap<>(skillsBefore);
		skillsAfter.put("Magic", 32);

		Map<String, String> questsBefore = new HashMap<>();
		Map<String, String> questsAfter = new HashMap<>();
		questsAfter.put("The Restless Ghost", QuestsData.FINISHED);

		Map<String, Integer> diariesBefore = new HashMap<>();
		Map<String, Integer> diariesAfter = Collections.singletonMap("Varrock", 0b0001);

		List<String> changes = new ArrayList<>();
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
	public void aSkillMissingFromTheOldCopyIsNotAGain()
	{
		// a skill added in a game update shouldn't read as going from nothing to its level
		assertTrue(UpdateDiff.skills(Collections.emptyMap(), Collections.singletonMap("Sailing", 1)).isEmpty());
	}

	@Test
	public void startingAQuestIsNotNewsButFinishingItIs()
	{
		Map<String, String> before = Collections.singletonMap("Cook's Assistant", QuestsData.STARTED);
		assertTrue(UpdateDiff.quests(Collections.emptyMap(), before).isEmpty());
		assertEquals(Collections.singletonList("completed Cook's Assistant"),
			UpdateDiff.quests(before, Collections.singletonMap("Cook's Assistant", QuestsData.FINISHED)));
	}

	@Test
	public void lotsOfQuestsAreCounted()
	{
		Map<String, String> after = new HashMap<>();
		for (int i = 0; i < 12; i++)
		{
			after.put("Quest " + i, QuestsData.FINISHED);
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
