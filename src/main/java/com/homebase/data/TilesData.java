package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The tiles one member has marked for the group, and the colour they chose for them.
 */
@Data
public class TilesData implements Checkable
{
	/**
	 * Most tiles one member can mark, so a runaway client can't flood everyone else.
	 */
	public static final int MAX_TILES = 250;

	/**
	 * Colour as 0xRRGGBB.
	 */
	private int color;

	private List<Tile> tiles = new ArrayList<>();

	/**
	 * A marked tile. Tiles inside instances, such as raids, are stored at their location in the game's
	 * template map, so they line up for everyone who enters a copy of that instance.
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Tile
	{
		private int x;
		private int y;
		private int plane;
	}

	public boolean contains(int x, int y, int plane)
	{
		for (Tile tile : tiles)
		{
			if (tile.x == x && tile.y == y && tile.plane == plane)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Mark the tile, or unmark it if it's already marked.
	 *
	 * @return true if the tile is now marked
	 */
	public boolean toggle(int x, int y, int plane)
	{
		if (tiles.removeIf(t -> t.x == x && t.y == y && t.plane == plane))
		{
			return false;
		}
		if (tiles.size() >= MAX_TILES)
		{
			tiles.remove(0);
		}
		tiles.add(new Tile(x, y, plane));
		return true;
	}

	@Override
	public void check()
	{
		tiles = Checkable.list(tiles, MAX_TILES);
	}
}
