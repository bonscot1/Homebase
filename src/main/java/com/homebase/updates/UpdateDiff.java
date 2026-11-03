package com.homebase.updates;

import com.homebase.data.QuestsData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Describes what changed between two copies of a member's data, in the words shown to players:
 * "Magic 15 → 32", "completed The Restless Ghost", "completed the Varrock Easy diary".
 */
public final class UpdateDiff
{
	/**
	 * More than this many quests, diaries, slots or bosses in one go are summarised as a count, so a
	 * member who was away for months doesn't fill the chat box.
	 */
	private static final int MAX_LISTED = 4;

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
			if (QuestsData.FINISHED.equals(entry.getValue()) && !QuestsData.FINISHED.equals(before.get(entry.getKey())))
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
	 * Combat achievement tasks and tiers completed since before. A member we first saw with no tasks
	 * gets no line, since their tasks may simply not have loaded yet.
	 */
	public static List<String> combatAchievements(int tasksBefore, int tasksAfter, int tierBefore, int tierAfter, List<String> tiers)
	{
		List<String> out = new ArrayList<>();
		if (tasksBefore <= 0)
		{
			return out;
		}
		int done = tasksAfter - tasksBefore;
		if (done == 1)
		{
			out.add("completed a combat achievement");
		}
		else if (done > 1)
		{
			out.add("completed " + done + " combat achievements");
		}
		if (tierAfter > tierBefore && tierAfter >= 0 && tierAfter < tiers.size())
		{
			out.add("reached " + tiers.get(tierAfter) + " combat achievements");
		}
		return out;
	}

	/**
	 * Collection log slots filled since before.
	 *
	 * @param recentBefore newest slots as we had them, newest first
	 * @param recentAfter newest slots as they are now, newest first
	 */
	public static List<String> collectionLog(int obtainedBefore, int obtainedAfter, List<String> recentBefore, List<String> recentAfter)
	{
		// the slots newer than the newest one we already knew of
		List<String> names = new ArrayList<>();
		for (String name : recentAfter)
		{
			if (recentBefore.contains(name))
			{
				break;
			}
			names.add(name);
		}
		if (names.isEmpty())
		{
			int filled = obtainedBefore > 0 ? obtainedAfter - obtainedBefore : 0;
			if (filled == 1)
			{
				return Collections.singletonList("filled a collection log slot");
			}
			return filled > 1 ? Collections.singletonList("filled " + filled + " collection log slots") : Collections.emptyList();
		}
		if (names.size() > MAX_LISTED)
		{
			return Collections.singletonList("filled " + names.size() + " collection log slots");
		}
		// in the order they were filled
		Collections.reverse(names);
		List<String> out = new ArrayList<>();
		for (String name : names)
		{
			out.add("new collection log slot: " + name);
		}
		return out;
	}

	/**
	 * Bosses killed since before, by name. Bosses we hadn't seen a count for get no line, since the
	 * count may cover years of kills.
	 *
	 * @param before boss name to kill count, as we had it
	 * @param after boss name to kill count, as it is now
	 */
	public static List<String> killCounts(Map<String, Integer> before, Map<String, Integer> after)
	{
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : new TreeMap<>(after).entrySet())
		{
			Integer old = before.get(entry.getKey());
			if (old != null && entry.getValue() != null && entry.getValue() > old)
			{
				out.add(entry.getKey() + " kill count " + old + " → " + entry.getValue());
			}
		}
		if (out.size() > MAX_LISTED)
		{
			return Collections.singletonList("kill counts went up for " + out.size() + " bosses");
		}
		return out;
	}

	/**
	 * One line for the chat box, such as "bonscott1: Magic 15 -> 32, completed The Restless Ghost."
	 */
	public static String chatLine(String name, List<String> changes)
	{
		// the chat box font has no arrow
		return name + ": " + String.join(", ", changes).replace("→", "->") + ".";
	}
}
