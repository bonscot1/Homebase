package com.homebase.map;

import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Point;
import org.junit.Test;

public class MapMarkerTest
{
	@Test
	public void anglesAreClockwiseFromNorth()
	{
		assertEquals(0, MapMarker.angleBetween(3200, 3200, 3200, 3300));
		assertEquals(90, MapMarker.angleBetween(3200, 3200, 3300, 3200));
		assertEquals(180, MapMarker.angleBetween(3200, 3200, 3200, 3100));
		assertEquals(270, MapMarker.angleBetween(3200, 3200, 3100, 3200));
		assertEquals(45, MapMarker.angleBetween(3200, 3200, 3300, 3300));
		// rounded to the nearest eighth of a turn
		assertEquals(0, MapMarker.angleBetween(3200, 3200, 3205, 3300));
		assertEquals(0, MapMarker.angleBetween(3200, 3200, 3195, 3300));
	}

	@Test
	public void theArrowIsInTheMembersColourWithItsTipOnTheirPosition()
	{
		Color color = new Color(0x20C040);
		BufferedImage image = MapMarker.draw("Bonscott kid", color, MapMarker.DOWN);
		Point tip = MapMarker.tip(image);

		assertTrue(tip.getX() >= 0 && tip.getX() < image.getWidth());
		assertTrue(tip.getY() >= 0 && tip.getY() < image.getHeight());
		// just above the tip is inside the arrow
		assertEquals(color.getRGB(), image.getRGB(tip.getX(), tip.getY() - 6));
		// and below the name's label, the corners stay see-through so the map shows round it
		assertEquals(0, image.getRGB(0, image.getHeight() - 1) >>> 24);
	}

	@Test
	public void aLongNameMakesTheMarkerWiderSoItIsntCutOff()
	{
		Color color = Color.WHITE;
		int shortName = MapMarker.draw("Bo", color, MapMarker.DOWN).getWidth();
		int longName = MapMarker.draw("A really long name12", color, MapMarker.DOWN).getWidth();
		assertTrue(longName > shortName);
	}
}
