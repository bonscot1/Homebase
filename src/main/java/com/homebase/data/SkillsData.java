package com.homebase.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

@Data
public class SkillsData implements Checkable
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

	/**
	 * @return the skill's real level, 1 to 99. RuneLite's own lookup goes on to virtual levels past 99,
	 * which would throw off total and combat levels.
	 */
	public int getLevel(Skill skill)
	{
		return Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp(getXp(skill)));
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

	@Override
	public void check()
	{
		xp = Checkable.counts(xp, Experience.MAX_SKILL_XP);
	}
}
