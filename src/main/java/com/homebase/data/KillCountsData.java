package com.homebase.data;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Data;

/**
 * Boss kill counts, from the "Your Vorkath kill count is: 12" messages the game sends, the collection
 * log's page headers and the hiscores.
 */
@Data
public class KillCountsData implements Checkable
{
	/**
	 * Most bosses kept, so a bad client can't flood everyone with junk.
	 */
	public static final int MAX_BOSSES = 200;

	/**
	 * Boss name to kill count.
	 */
	private Map<String, Integer> counts = new HashMap<>();

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
		if (boss == null)
		{
			return null;
		}
		if (counts.containsKey(boss))
		{
			return boss;
		}
		String key = bossKey(boss);
		for (String name : counts.keySet())
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
	static String bossKey(String name)
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
	}
}
