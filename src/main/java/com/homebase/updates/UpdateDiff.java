package com.homebase.updates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Describes what changed between two copies of a member's data, in the words shown to players:
 * "Magic 15 → 32", "completed The Restless Ghost", "completed the Varrock Easy diary".
 */
public final class UpdateDiff
{
	/**
	 * More than this many quests or diaries in one go are summarised as a count, so a member who was
	 * away for months doesn't fill the chat box.
	 */
	static final int MAX_LISTED = 4;

	static final String FINISHED = "F";

	private UpdateDiff()
	{
	}

	/**
	 * Skills that went up a level, in the order given.
	 *
	 * @param before skill display name to level, as we had it
	 * @param after skill display name to level, as it is now
	 */
	public static List<String> skills(Map<String, Integer> before, Map<String, Integer> after)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : after.entrySet())
		{
			Integer old = before.get(entry.getKey());
			if (old != null && entry.getValue() != null && entry.getValue() > old)
			{
				out.add(entry.getKey() + " " + old + " → " + entry.getValue());
			}
		}
		return out;
	}

	/**
	 * Quests finished since before.
	 *
	 * @param before quest name to state, as we had it
	 * @param after quest name to state, as it is now
	 */
	public static List<String> quests(Map<String, String> before, Map<String, String> after)
	{
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, String> entry : after.entrySet())
		{
			if (FINISHED.equals(entry.getValue()) && !FINISHED.equals(before.get(entry.getKey())))
			{
				names.add(entry.getKey());
			}
		}
		Collections.sort(names);
		if (names.size() > MAX_LISTED)
		{
			return Collections.singletonList("completed " + names.size() + " quests");
		}
		List<String> out = new ArrayList<>();
		for (String name : names)
		{
			out.add("completed " + name);
		}
		return out;
	}

	/**
	 * Diary tiers completed since before.
	 *
	 * @param before region to completed tier bits, as we had it
	 * @param after region to completed tier bits, as it is now
	 * @param regions region names in display order
	 * @param tiers tier names, one per bit
	 */
	public static List<String> diaries(Map<String, Integer> before, Map<String, Integer> after, List<String> regions, List<String> tiers)
	{
		List<String> names = new ArrayList<>();
		for (String region : regions)
		{
			int was = before.getOrDefault(region, 0);
			int now = after.getOrDefault(region, 0);
			for (int tier = 0; tier < tiers.size(); tier++)
			{
				int bit = 1 << tier;
				if ((now & bit) != 0 && (was & bit) == 0)
				{
					names.add(region + " " + tiers.get(tier));
				}
			}
		}
		if (names.size() > MAX_LISTED)
		{
			return Collections.singletonList("completed " + names.size() + " diaries");
		}
		List<String> out = new ArrayList<>();
		for (String name : names)
		{
			out.add("completed the " + name + " diary");
		}
		return out;
	}

	/**
	 * One line for the chat box, such as "bonscott1: Magic 15 -> 32, completed The Restless Ghost."
	 */
	public static String chatLine(String name, List<String> changes)
	{
		return name + ": " + String.join(", ", changes).replace("→", "->") + ".";
	}
}
