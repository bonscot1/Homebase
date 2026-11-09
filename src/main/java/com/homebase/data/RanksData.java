package com.homebase.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * Where the player is on the hiscores, read once each time they log in.
 */
@Data
public class RanksData implements Checkable
{
	/**
	 * The hiscores have around 120 tables, so this leaves room for more.
	 */
	static final int MAX_ENTRIES = 200;

	private static final int MAX_NAME = 40;

	/**
	 * The hiscores' name for each table the player is ranked on, such as "Overall", "Attack" or "Vorkath",
	 * to their rank on it.
	 */
	private Map<String, Integer> ranks = new HashMap<>();

	/**
	 * @return the player's rank on that table, or null if they aren't ranked on it
	 */
	public Integer rankFor(String name)
	{
		return ranks.get(name);
	}

	@Override
	public void check()
	{
		ranks = Checkable.map(ranks, MAX_ENTRIES);
		ranks.entrySet().removeIf(entry -> entry.getValue() <= 0 || entry.getKey().isEmpty() || entry.getKey().length() > MAX_NAME);
	}
}
