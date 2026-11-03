package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import net.runelite.api.Skill;
import org.junit.Test;

public class CheckableTest
{
	private final Gson gson = new Gson();

	private <T> T read(String json, Class<T> type)
	{
		return Checkable.checked(gson.fromJson(json, type));
	}

	@Test
	public void missingListsBecomeEmpty()
	{
		assertTrue(read("{\"items\":null}", ItemsData.class).toItems().isEmpty());
		assertTrue(read("{\"tiles\":null}", TilesData.class).getTiles().isEmpty());
		assertTrue(read("{\"npcs\":[null]}", HighlightsData.class).getNpcs().isEmpty());
		assertTrue(read("{\"pages\":{\"Zulrah\":null},\"recent\":null}", CollectionLogData.class).getPages().isEmpty());
	}

	@Test
	public void skillsStayInRange()
	{
		SkillsData skills = read("{\"xp\":{\"ATTACK\":-1,\"MAGIC\":2000000000,\"PRAYER\":null}}", SkillsData.class);
		assertEquals(1, skills.getLevel(Skill.ATTACK));
		assertEquals(99, skills.getLevel(Skill.MAGIC));
		assertEquals(1, skills.getLevel(Skill.PRAYER));

		assertEquals(0, read("{\"xp\":null}", SkillsData.class).getXp(Skill.ATTACK));
	}
}
