package com.homebase.pings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.homebase.data.HighlightsData;
import java.awt.Color;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class PingsTest
{
	@Test
	public void highlightsToggleAndAreCapped()
	{
		HighlightsData data = new HighlightsData();
		assertTrue(data.toggle(5, 100));
		assertTrue(data.contains(5, 100));
		// the same index on another kind of monster is a different monster
		assertFalse(data.contains(5, 101));
		assertFalse(data.toggle(5, 100));
		assertFalse(data.contains(5, 100));

		for (int i = 0; i < HighlightsData.MAX_HIGHLIGHTS + 3; i++)
		{
			data.toggle(i, 1);
		}
		assertEquals(HighlightsData.MAX_HIGHLIGHTS, data.getNpcs().size());
		// the oldest ones made room
		assertFalse(data.contains(0, 1));
		assertTrue(data.contains(HighlightsData.MAX_HIGHLIGHTS + 2, 1));
	}

	@Test
	public void pingsAgeAndFade()
	{
		Ping ping = new Ping(1, "Alice", Color.RED, 301, new WorldPoint(3200, 3200, 0), -1, -1, 1_000);
		assertFalse(ping.isNpc());
		assertEquals(0f, ping.age(1_000, 4_000), 0.001f);
		assertEquals(0.5f, ping.age(3_000, 4_000), 0.001f);
		assertEquals(1f, ping.age(9_000, 4_000), 0.001f);

		// bright for most of its life, gone at the end
		assertEquals(255, PingOverlay.fade(Color.RED, 0f, 255).getAlpha());
		assertTrue(PingOverlay.fade(Color.RED, 0.5f, 255).getAlpha() > 180);
		assertEquals(0, PingOverlay.fade(Color.RED, 1f, 255).getAlpha());
	}
}
