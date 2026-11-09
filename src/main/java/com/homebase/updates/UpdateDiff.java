package com.homebase.updates;

import com.homebase.data.Clean;
import com.homebase.data.FeedData;
import com.homebase.data.KillCountsData;
import com.homebase.data.QuestsData;
import com.homebase.data.UpdateLine;
import com.homebase.data.UpdateLine.Kind;
import com.homebase.map.AreaNames;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.QuantityFormatter;

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

	/**
	 * Longest item, monster or place name shown from another member's feed.
	 */
	private static final int MAX_FEED_TEXT = 60;

	/**
	 * Times are shown to a hundredth of a second, so a time beaten by less than half of one looks the same.
	 */
	private static final double SAME_TIME_SECONDS = 0.005;

	/**
	 * A level line as {@link #skills} writes it, such as "Magic 15 → 32".
	 */
	private static final Pattern LEVEL_LINE = Pattern.compile("([A-Za-z]+) \\d+ → \\d+");

	private UpdateDiff()
	{
	}

	/**
	 * Skills that went up a level, in the order given.
	 *
	 * @param before skill display name to level, as we had it
	 * @param after skill display name to level, as it is now
	 */
	public static List<UpdateLine> skills(Map<String, Integer> before, Map<String, Integer> after)
	{
		return skills(before, after, 1);
	}

	/**
	 * Skills that went up a level, leaving out any that are still below {@code minLevel}.
	 */
	public static List<UpdateLine> skills(Map<String, Integer> before, Map<String, Integer> after, int minLevel)
	{
		List<UpdateLine> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : after.entrySet())
		{
			Integer old = before.get(entry.getKey());
			if (old != null && entry.getValue() != null && entry.getValue() > old && entry.getValue() >= minLevel)
			{
				out.add(UpdateLine.about(Kind.LEVEL, entry.getKey(), entry.getKey() + " " + old + " → " + entry.getValue()));
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
	public static List<UpdateLine> quests(Map<String, String> before, Map<String, String> after)
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
			return Collections.singletonList(UpdateLine.of(Kind.QUEST, "completed " + names.size() + " quests"));
		}
		List<UpdateLine> out = new ArrayList<>();
		for (String name : names)
		{
			out.add(UpdateLine.of(Kind.QUEST, "completed " + name));
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
	public static List<UpdateLine> diaries(Map<String, Integer> before, Map<String, Integer> after, List<String> regions, List<String> tiers)
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
			return Collections.singletonList(UpdateLine.of(Kind.DIARY, "completed " + names.size() + " diaries"));
		}
		List<UpdateLine> out = new ArrayList<>();
		for (String name : names)
		{
			out.add(UpdateLine.of(Kind.DIARY, "completed the " + name + " diary"));
		}
		return out;
	}

	/**
	 * Combat achievement tasks and tiers completed since before. A member we first saw with no tasks
	 * gets no line, since their tasks may simply not have loaded yet.
	 */
	public static List<UpdateLine> combatAchievements(int tasksBefore, int tasksAfter, int tierBefore, int tierAfter, List<String> tiers)
	{
		List<UpdateLine> out = new ArrayList<>();
		if (tasksBefore <= 0)
		{
			return out;
		}
		int done = tasksAfter - tasksBefore;
		if (done == 1)
		{
			out.add(UpdateLine.of(Kind.COMBAT_ACHIEVEMENT, "completed a combat achievement"));
		}
		else if (done > 1)
		{
			out.add(UpdateLine.of(Kind.COMBAT_ACHIEVEMENT, "completed " + done + " combat achievements"));
		}
		if (tierAfter > tierBefore && tierAfter >= 0 && tierAfter < tiers.size())
		{
			out.add(UpdateLine.of(Kind.COMBAT_ACHIEVEMENT, "reached " + tiers.get(tierAfter) + " combat achievements"));
		}
		return out;
	}

	/**
	 * Collection log slots filled since before.
	 *
	 * @param recentBefore newest slots as we had them, newest first
	 * @param recentAfter newest slots as they are now, newest first
	 */
	public static List<UpdateLine> collectionLog(int obtainedBefore, int obtainedAfter, List<String> recentBefore, List<String> recentAfter)
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
				return Collections.singletonList(UpdateLine.of(Kind.COLLECTION_LOG, "filled a collection log slot"));
			}
			return filled > 1
				? Collections.singletonList(UpdateLine.of(Kind.COLLECTION_LOG, "filled " + filled + " collection log slots"))
				: Collections.emptyList();
		}
		if (names.size() > MAX_LISTED)
		{
			return Collections.singletonList(UpdateLine.of(Kind.COLLECTION_LOG, "filled " + names.size() + " collection log slots"));
		}
		// in the order they were filled
		Collections.reverse(names);
		List<UpdateLine> out = new ArrayList<>();
		for (String name : names)
		{
			out.add(UpdateLine.of(Kind.COLLECTION_LOG, "new collection log slot: " + name));
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
	public static List<UpdateLine> killCounts(Map<String, Integer> before, Map<String, Integer> after)
	{
		List<UpdateLine> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : new TreeMap<>(after).entrySet())
		{
			Integer old = before.get(entry.getKey());
			if (old != null && entry.getValue() != null && entry.getValue() > old)
			{
				out.add(UpdateLine.about(Kind.KILL_COUNT, entry.getKey(), entry.getKey() + " kill count " + old + " → " + entry.getValue()));
			}
		}
		if (out.size() > MAX_LISTED)
		{
			return Collections.singletonList(UpdateLine.of(Kind.KILL_COUNT, "kill counts went up for " + out.size() + " bosses"));
		}
		return out;
	}

	/**
	 * Best kill times beaten since before. A boss with no time before only gets a line if the member
	 * already had other times, since everyone's times arrive at once the first time they're shared.
	 *
	 * @param before boss name to best time in seconds, as we had it
	 * @param after boss name to best time in seconds, as it is now
	 */
	public static List<UpdateLine> bestTimes(Map<String, Double> before, Map<String, Double> after)
	{
		List<UpdateLine> out = new ArrayList<>();
		int firstTimes = 0;
		for (Map.Entry<String, Double> entry : new TreeMap<>(after).entrySet())
		{
			Double old = before.get(entry.getKey());
			String time = KillCountsData.formatTime(entry.getValue());
			if (old == null)
			{
				firstTimes++;
				out.add(UpdateLine.about(Kind.BEST_TIME, entry.getKey(), entry.getKey() + " best time " + time));
			}
			else if (entry.getValue() < old - SAME_TIME_SECONDS)
			{
				out.add(UpdateLine.about(Kind.BEST_TIME, entry.getKey(),
					entry.getKey() + " best time " + KillCountsData.formatTime(old) + " → " + time));
			}
		}
		if (before.isEmpty() && firstTimes > 0)
		{
			return Collections.emptyList();
		}
		if (out.size() > MAX_LISTED)
		{
			return Collections.singletonList(UpdateLine.of(Kind.BEST_TIME, "new best times on " + out.size() + " bosses"));
		}
		return out;
	}

	/**
	 * Drops, pets and deaths since before.
	 *
	 * @param before the member's feed as we had it
	 * @param after their feed as it is now
	 * @param drops whether to include drops and pets
	 * @param minValue the least a drop must be worth to be included
	 * @param deaths whether to include deaths
	 */
	public static List<UpdateLine> feed(List<FeedData.Entry> before, List<FeedData.Entry> after, boolean drops, long minValue, boolean deaths)
	{
		long newestSeen = 0;
		for (FeedData.Entry entry : before)
		{
			newestSeen = Math.max(newestSeen, entry.getAt());
		}
		List<UpdateLine> gotten = new ArrayList<>();
		List<UpdateLine> pets = new ArrayList<>();
		List<UpdateLine> died = new ArrayList<>();
		for (FeedData.Entry entry : after)
		{
			if (entry.getAt() <= newestSeen || entry.getType() == null)
			{
				continue;
			}
			switch (entry.getType())
			{
				case FeedData.DROP:
					if (drops && entry.getValue() >= minValue)
					{
						String line = dropLine(entry);
						if (line != null)
						{
							gotten.add(new UpdateLine(Kind.DROP, null, entry.getItem(), line));
						}
					}
					break;
				case FeedData.PET:
					if (drops)
					{
						pets.add(UpdateLine.of(Kind.PET, "got a pet!"));
					}
					break;
				case FeedData.DEATH:
					if (deaths)
					{
						String place = Clean.text(entry.getName(), MAX_FEED_TEXT);
						died.add(UpdateLine.of(Kind.DEATH, place == null || AreaNames.UNKNOWN.equals(place) ? "died" : "died: " + place));
					}
					break;
				default:
					// a kind of entry from a newer version of the plugin
			}
		}
		List<UpdateLine> out = new ArrayList<>();
		if (gotten.size() > MAX_LISTED)
		{
			out.add(UpdateLine.of(Kind.DROP, "got " + gotten.size() + " valuable drops"));
		}
		else
		{
			out.addAll(gotten);
		}
		// too good to fold into a count
		out.addAll(pets);
		if (died.size() > MAX_LISTED)
		{
			out.add(UpdateLine.of(Kind.DEATH, "died " + died.size() + " times"));
		}
		else
		{
			out.addAll(died);
		}
		return out;
	}

	private static String dropLine(FeedData.Entry entry)
	{
		String item = Clean.text(entry.getName(), MAX_FEED_TEXT);
		if (item == null)
		{
			return null;
		}
		String source = Clean.text(entry.getSource(), MAX_FEED_TEXT);
		return "got " + (entry.getQuantity() > 1 ? QuantityFormatter.formatNumber(entry.getQuantity()) + " x " : "") + item
			+ (source == null ? "" : " from " + source)
			+ " (" + QuantityFormatter.quantityToStackSize(Math.max(0, entry.getValue())) + ")";
	}

	/**
	 * @return the line with its kind and subject worked out from its words. Updates saved by older
	 * versions of the plugin have only the words.
	 */
	public static UpdateLine fromText(String text)
	{
		if (text == null)
		{
			return null;
		}
		Matcher level = LEVEL_LINE.matcher(text);
		if (level.matches())
		{
			return UpdateLine.about(Kind.LEVEL, level.group(1), text);
		}
		int kills = text.indexOf(" kill count ");
		if (kills > 0)
		{
			return UpdateLine.about(Kind.KILL_COUNT, text.substring(0, kills), text);
		}
		int best = text.indexOf(" best time ");
		if (best > 0)
		{
			return UpdateLine.about(Kind.BEST_TIME, text.substring(0, best), text);
		}
		if (text.startsWith("kill counts went up"))
		{
			return UpdateLine.of(Kind.KILL_COUNT, text);
		}
		if (text.startsWith("new best times"))
		{
			return UpdateLine.of(Kind.BEST_TIME, text);
		}
		if (text.contains("collection log slot"))
		{
			return UpdateLine.of(Kind.COLLECTION_LOG, text);
		}
		if (text.contains("combat achievement"))
		{
			return UpdateLine.of(Kind.COMBAT_ACHIEVEMENT, text);
		}
		if (text.endsWith(" diary") || text.endsWith(" diaries"))
		{
			return UpdateLine.of(Kind.DIARY, text);
		}
		if (text.startsWith("completed "))
		{
			return UpdateLine.of(Kind.QUEST, text);
		}
		if (text.startsWith("got a pet"))
		{
			return UpdateLine.of(Kind.PET, text);
		}
		if (text.startsWith("got "))
		{
			return UpdateLine.of(Kind.DROP, text);
		}
		if (text.startsWith("died"))
		{
			return UpdateLine.of(Kind.DEATH, text);
		}
		return UpdateLine.of(null, text);
	}

	/**
	 * @return just the words of each line
	 */
	public static List<String> texts(List<UpdateLine> lines)
	{
		List<String> out = new ArrayList<>();
		for (UpdateLine line : lines)
		{
			out.add(line.getText());
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
