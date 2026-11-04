package com.homebase.map;

import java.awt.Rectangle;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MapPinsTest
{
	private static final Rectangle MAP = new Rectangle(10, 30, 480, 300);

	/**
	 * Where RuneLite draws a tile on the map, written out the way its world map overlay works it out.
	 */
	private static int[] screenPosition(int x, int y, float zoom, int centreX, int centreY)
	{
		int widthInTiles = (int) Math.ceil(MAP.getWidth() / zoom);
		int heightInTiles = (int) Math.ceil(MAP.getHeight() / zoom);
		int yTileMax = centreY - heightInTiles / 2;
		int yTileOffset = (yTileMax - y - 1) * -1;
		int xTileOffset = x + widthInTiles / 2 - centreX;
		int xPixels = (int) (xTileOffset * zoom);
		int yPixels = (int) (yTileOffset * zoom);
		yPixels -= zoom - Math.ceil(zoom / 2);
		xPixels += zoom - Math.ceil(zoom / 2);
		return new int[]{xPixels + MAP.x, MAP.height - yPixels + MAP.y};
	}

	private static void assertFindsTile(int x, int y, float zoom, int centreX, int centreY)
	{
		int[] screen = screenPosition(x, y, zoom, centreX, centreY);
		WorldPoint found = MapPins.pointAt(screen[0], screen[1], MAP, zoom, centreX, centreY);
		// a pixel can land on the neighbouring tile at the far zoomed-in or zoomed-out levels, which is close enough
		assertTrue(found + " for " + x + "," + y + " at zoom " + zoom,
			Math.abs(found.getX() - x) <= 1 && Math.abs(found.getY() - y) <= 1);
	}

	@Test
	public void findsTheTileUnderTheMouseAtEveryZoom()
	{
		for (float zoom : new float[]{1f, 2f, 4f, 8f})
		{
			assertFindsTile(3222, 3218, zoom, 3222, 3218);
			assertFindsTile(3240, 3200, zoom, 3222, 3218);
			assertFindsTile(3210, 3230, zoom, 3222, 3218);
		}
	}
}
