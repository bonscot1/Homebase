package com.homebase.overlay;

import java.awt.Color;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GroupHudOverlayTest
{
	private static double brightness(Color c)
	{
		return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
	}

	@Test
	public void darkColoursAreLightenedAndBrightOnesLeftAlone()
	{
		assertTrue(brightness(GroupHudOverlay.readable(new Color(0x0057FF))) >= 129);
		assertTrue(brightness(GroupHudOverlay.readable(Color.BLACK)) >= 129);
		assertEquals(Color.WHITE, GroupHudOverlay.readable(Color.WHITE));
		assertEquals(Color.ORANGE, GroupHudOverlay.readable(Color.ORANGE));
	}
}
