package com.homebase.data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class DiariesData
{
	public static final List<String> TIERS = Arrays.asList("Easy", "Medium", "Hard", "Elite");

	public static final List<String> REGIONS = Arrays.asList(
		"Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Karamja", "Kourend & Kebos",
		"Lumbridge & Draynor", "Morytania", "Varrock", "Western Provinces", "Wilderness");

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
}
