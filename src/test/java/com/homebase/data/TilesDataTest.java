package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TilesDataTest
{
	@Test
	public void toggleMarksThenUnmarks()
	{
		TilesData data = new TilesData();
		assertTrue(data.toggle(3222, 3218, 0));
		assertTrue(data.contains(3222, 3218, 0));
		assertFalse(data.contains(3222, 3218, 1));
		assertFalse(data.toggle(3222, 3218, 0));
		assertFalse(data.contains(3222, 3218, 0));
	}

	@Test
	public void oldestTileDropsWhenFull()
	{
		TilesData data = new TilesData();
		for (int i = 0; i < TilesData.MAX_TILES + 5; i++)
		{
			data.toggle(i, 0, 0);
		}
		assertEquals(TilesData.MAX_TILES, data.getTiles().size());
		assertFalse(data.contains(0, 0, 0));
		assertTrue(data.contains(TilesData.MAX_TILES + 4, 0, 0));
	}
}
