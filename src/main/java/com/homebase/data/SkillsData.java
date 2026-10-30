package com.homebase.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

@Data
public class SkillsData
{
	/**
	 * Experience by {@link Skill} name. Stored by name rather than position so a skill added in a game
	 * update doesn't shift everyone else's numbers.
	 */
	private Map<String, Integer> xp = new HashMap<>();

	public int getXp(Skill skill)
	{
		Integer value = xp.get(skill.name());
		return value == null ? 0 : value;
	}

	public int getLevel(Skill skill)
	{
		return Experience.getLevelForXp(getXp(skill));
	}

	public int getTotalLevel()
	{
		int total = 0;
		for (Skill skill : Skill.values())
		{
			total += getLevel(skill);
		}
		return total;
	}
}
