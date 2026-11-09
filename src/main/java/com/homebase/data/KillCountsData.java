package com.homebase.data;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * Boss kill counts, from the "Your Vorkath kill count is: 12" messages the game sends, the collection
 * log's page headers and the hiscores. Also the player's best kill times.
 */
@Data
public class KillCountsData implements Checkable
{
	/**
	 * Most bosses kept, so a bad client can't flood everyone with junk.
	 */
	public static final int MAX_BOSSES = 200;

	/**
	 * Longest best time kept, in seconds: a day.
	 */
	static final double MAX_SECONDS = 24 * 60 * 60;

	/**
	 * Boss name to kill count.
	 */
	private Map<String, Integer> counts = new HashMap<>();

	/**
	 * Boss name to the player's best kill time, in seconds. Older versions of the plugin don't send this.
	 */
	private Map<String, Double> bestTimes = new HashMap<>();

	/**
	 * @return the boss's kill count, or null if there's none
	 */
	public Integer countFor(String boss)
	{
		String name = existingName(boss);
		return name == null ? null : counts.get(name);
	}

	/**
	 * @return the name this boss is already kept under, or null if it isn't. The game's messages, the
	 * collection log and the hiscores don't always name a boss the same way ("Gauntlet" and
	 * "The Gauntlet", "Barrows" and "Barrows Chests"), so names are matched loosely.
	 */
	public String existingName(String boss)
	{
		return matching(counts.keySet(), boss);
	}

	/**
	 * @return the boss's best kill time in seconds, or null if there's none
	 */
	public Double bestTimeFor(String boss)
	{
		String name = existingTimeName(boss);
		return name == null ? null : bestTimes.get(name);
	}

	/**
	 * @return the name this boss's best time is already kept under, or null if it isn't
	 */
	public String existingTimeName(String boss)
	{
		return matching(bestTimes.keySet(), boss);
	}

	private static String matching(Set<String> names, String boss)
	{
		if (boss == null)
		{
			return null;
		}
		if (names.contains(boss))
		{
			return boss;
		}
		String key = bossKey(boss);
		for (String name : names)
		{
			if (bossKey(name).equals(key))
			{
				return name;
			}
		}
		return null;
	}

	/**
	 * @return the boss name in lower case, without "The", punctuation, a closing "Chest" or "Chests",
	 * or a plural "s"
	 */
	public static String bossKey(String name)
	{
		String key = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
		if (key.startsWith("the "))
		{
			key = key.substring(4);
		}
		key = key.replaceAll(" chests?$", "");
		return key.endsWith("s") ? key.substring(0, key.length() - 1) : key;
	}

	@Override
	public void check()
	{
		counts = Checkable.counts(Checkable.map(counts, MAX_BOSSES), Integer.MAX_VALUE);
		bestTimes = Checkable.map(bestTimes, MAX_BOSSES);
		bestTimes.replaceAll((boss, seconds) -> Double.isFinite(seconds) ? Math.max(0, Math.min(MAX_SECONDS, seconds)) : 0);
	}

	/**
	 * @return a time the way the game writes it, such as "1:23.40" or "1:02:05"
	 */
	public static String formatTime(double seconds)
	{
		long hundredths = Math.round(seconds * 100);
		long hours = hundredths / 360_000;
		long minutes = hundredths / 6_000 % 60;
		long secs = hundredths / 100 % 60;
		String time = hours > 0
			? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
			: String.format(Locale.ROOT, "%d:%02d", minutes, secs);
		return hundredths % 100 == 0 ? time : time + String.format(Locale.ROOT, ".%02d", hundredths % 100);
	}
}
