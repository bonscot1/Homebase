package com.homebase.map;

import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Point;
import org.junit.Test;

public class MapMarkerTest
{
	private static final Color COLOR = new Color(0x20C040);

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
		BufferedImage image = MapMarker.arrow("Bonscott kid", COLOR, MapMarker.DOWN, 1);
		assertTipIsInside(image, 1);
		// just above the tip is inside the arrow
		Point tip = MapMarker.tip(image, 1);
		assertEquals(COLOR.getRGB(), image.getRGB(tip.getX(), tip.getY() - 6));
		// and below the name's label, the corners stay see-through so the map shows round it
		assertEquals(0, image.getRGB(0, image.getHeight() - 1) >>> 24);
	}

	@Test
	public void markerSizesKeepTheirTipOnThePosition()
	{
		for (double scale : new double[]{0.75, 1, 1.5})
		{
			assertTipIsInside(MapMarker.arrow("Mundell", COLOR, MapMarker.DOWN, scale), scale);
			assertTipIsInside(MapMarker.arrow(null, COLOR, MapMarker.DOWN, scale), scale);
			assertTipIsInside(MapMarker.pin("Mundell", COLOR, scale), scale);
			assertTipIsInside(MapMarker.pin(null, COLOR, scale), scale);
		}
		assertTrue(MapMarker.arrow(null, COLOR, MapMarker.DOWN, 1.5).getHeight() > MapMarker.arrow(null, COLOR, MapMarker.DOWN, 1).getHeight());
	}

	@Test
	public void aPinIsInTheMembersColourWithAHoleInItsHead()
	{
		BufferedImage image = MapMarker.pin(null, COLOR, 1);
		int middleX = image.getWidth() / 2;
		int middleY = image.getHeight() / 2;
		// the hole is in the middle of the round head, above the box's centre
		assertEquals(Color.BLACK.getRGB(), image.getRGB(middleX, middleY - 5));
		// the head is solid colour around it, and the stem below it
		assertEquals(COLOR.getRGB(), image.getRGB(middleX - 6, middleY - 5));
		assertEquals(COLOR.getRGB(), image.getRGB(middleX, middleY + 5));
	}

	@Test
	public void aLongNameMakesTheMarkerWiderSoItIsntCutOff()
	{
		int shortName = MapMarker.arrow("Bo", Color.WHITE, MapMarker.DOWN, 1).getWidth();
		int longName = MapMarker.arrow("A really long name12", Color.WHITE, MapMarker.DOWN, 1).getWidth();
		assertTrue(longName > shortName);
	}

	private static void assertTipIsInside(BufferedImage image, double scale)
	{
		Point tip = MapMarker.tip(image, scale);
		assertTrue(tip.getX() >= 0 && tip.getX() < image.getWidth());
		assertTrue(tip.getY() >= 0 && tip.getY() < image.getHeight());
	}
}
