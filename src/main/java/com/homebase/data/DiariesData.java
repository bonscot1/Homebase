package com.homebase.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DiariesData implements Checkable
{
	public static final List<String> TIERS = Arrays.asList("Easy", "Medium", "Hard", "Elite");

	public static final List<String> REGIONS = Arrays.asList(
		"Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Karamja", "Kourend & Kebos",
		"Lumbridge & Draynor", "Morytania", "Varrock", "Western Provinces", "Wilderness");

	/**
	 * One bit set for each entry of {@link #TIERS}.
	 */
	private static final int ALL_TIERS = (1 << TIERS.size()) - 1;

	/**
	 * Region name to the tiers completed, one bit per entry of {@link #TIERS}.
	 */
	private Map<String, Integer> done = new HashMap<>();

	public boolean isDone(String region, int tier)
	{
		Integer bits = done.get(region);
		return bits != null && (bits & (1 << tier)) != 0;
	}

	public int countDone()
	{
		int n = 0;
		for (Integer bits : done.values())
		{
			n += bits == null ? 0 : Integer.bitCount(bits);
		}
		return n;
	}

	@Override
	public void check()
	{
		done = Checkable.counts(done, Integer.MAX_VALUE);
		// unknown regions and extra bits would push the number done past the total
		done.keySet().retainAll(REGIONS);
		done.replaceAll((region, tiers) -> tiers & ALL_TIERS);
	}
}
