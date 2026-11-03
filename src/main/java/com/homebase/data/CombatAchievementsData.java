package com.homebase.data;

import java.util.Arrays;
import java.util.List;
import lombok.Data;

@Data
public class CombatAchievementsData
{
	public static final List<String> TIERS = Arrays.asList("Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster");

	private int points;

	/**
	 * How many tasks are done, across every tier.
	 */
	private int tasks;

	/**
	 * Highest entry of {@link #TIERS} whose points threshold has been reached, or -1 for none.
	 */
	private int tier = -1;

	public String tierName()
	{
		return tier >= 0 && tier < TIERS.size() ? TIERS.get(tier) : null;
	}
}
