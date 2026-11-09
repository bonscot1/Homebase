package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RanksDataTest
{
	@Test
	public void keepsOnlyRealRanks()
	{
		Map<String, Integer> ranks = new HashMap<>();
		ranks.put("Overall", 152_334);
		ranks.put("Vorkath", 0);
		ranks.put("Zulrah", -1);
		ranks.put("", 5);
		ranks.put("A table name far too long to be anything the hiscores really have", 9);
		ranks.put("Attack", null);
		RanksData data = new RanksData();
		data.setRanks(ranks);

		data.check();

		assertEquals(1, data.getRanks().size());
		assertEquals(Integer.valueOf(152_334), data.rankFor("Overall"));
		assertNull(data.rankFor("Vorkath"));
	}

	@Test
	public void keepsAFewHundredAtMost()
	{
		RanksData data = new RanksData();
		for (int i = 0; i < 500; i++)
		{
			data.getRanks().put("Table " + i, i + 1);
		}

		data.check();

		assertEquals(RanksData.MAX_ENTRIES, data.getRanks().size());
	}

	@Test
	public void aMissingListIsEmpty()
	{
		RanksData data = new RanksData();
		data.setRanks(null);

		data.check();

		assertEquals(0, data.getRanks().size());
	}
}
