package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The monsters one member has highlighted for the group. A monster is known by its index on the
 * world, which is the same for everyone there, so highlights only count on the world they were made.
 */
@Data
public class HighlightsData
{
	/**
	 * Most monsters one member can highlight at once.
	 */
	public static final int MAX_HIGHLIGHTS = 20;

	/**
	 * Colour as 0xRRGGBB.
	 */
	private int color;

	private int world;

	private List<Npc> npcs = new ArrayList<>();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Npc
	{
		private int index;

		/**
		 * What kind of monster it is, so a new monster that later gets the same index isn't highlighted.
		 */
		private int id;
	}

	public boolean contains(int index, int id)
	{
		for (Npc npc : npcs)
		{
			if (npc.index == index && npc.id == id)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Highlight the monster, or stop highlighting it if it already is.
	 *
	 * @return true if it's now highlighted
	 */
	public boolean toggle(int index, int id)
	{
		if (remove(index, id))
		{
			return false;
		}
		if (npcs.size() >= MAX_HIGHLIGHTS)
		{
			npcs.remove(0);
		}
		npcs.add(new Npc(index, id));
		return true;
	}

	public boolean remove(int index, int id)
	{
		return npcs.removeIf(n -> n.index == index && n.id == id);
	}
}
