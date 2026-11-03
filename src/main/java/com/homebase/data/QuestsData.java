package com.homebase.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import net.runelite.api.Quest;

@Data
public class QuestsData implements Checkable
{
	public static final String FINISHED = "F";
	public static final String STARTED = "S";

	/**
	 * Miniquests, which the game counts apart from quests. RuneLite's quest list doesn't say which
	 * are which.
	 */
	public static final Set<String> MINIQUESTS = new HashSet<>();

	static
	{
		for (Quest quest : new Quest[]{
			Quest.ALFRED_GRIMHANDS_BARCRAWL, Quest.BARBARIAN_TRAINING, Quest.BEAR_YOUR_SOUL,
			Quest.CURSE_OF_THE_EMPTY_LORD, Quest.DADDYS_HOME, Quest.THE_ENCHANTED_KEY, Quest.ENTER_THE_ABYSS,
			Quest.FAMILY_PEST, Quest.THE_FROZEN_DOOR, Quest.THE_GENERALS_SHADOW, Quest.HIS_FAITHFUL_SERVANTS,
			Quest.HOPESPEARS_WILL, Quest.IN_SEARCH_OF_KNOWLEDGE, Quest.INTO_THE_TOMBS, Quest.LAIR_OF_TARN_RAZORLOR,
			Quest.MAGE_ARENA_I, Quest.MAGE_ARENA_II, Quest.SKIPPY_AND_THE_MOGRES, Quest.VALE_TOTEMS})
		{
			MINIQUESTS.add(quest.getName());
		}
	}

	/**
	 * Quest name to {@link #FINISHED} or {@link #STARTED}. Quests not started are left out. Keyed by
	 * name rather than RuneLite's quest list so members on different RuneLite versions still agree.
	 */
	private Map<String, String> quests = new HashMap<>();

	/**
	 * @return how many quests, not counting miniquests, are in the given state
	 */
	public int count(String state)
	{
		return count(state, false);
	}

	/**
	 * @return how many miniquests are in the given state
	 */
	public int countMiniquests(String state)
	{
		return count(state, true);
	}

	public static boolean isMiniquest(String name)
	{
		return MINIQUESTS.contains(name);
	}

	private int count(String state, boolean miniquests)
	{
		int n = 0;
		for (Map.Entry<String, String> e : quests.entrySet())
		{
			if (state.equals(e.getValue()) && isMiniquest(e.getKey()) == miniquests)
			{
				n++;
			}
		}
		return n;
	}

	@Override
	public void check()
	{
		quests = Checkable.map(quests);
	}
}
