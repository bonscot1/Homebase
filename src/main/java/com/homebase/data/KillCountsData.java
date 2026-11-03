package com.homebase.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

/**
 * Boss kill counts, read from the "Your Vorkath kill count is: 12" messages the game sends.
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

	@Override
	public void check()
	{
		counts = Checkable.counts(counts, Integer.MAX_VALUE);
	}
}
