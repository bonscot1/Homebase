package com.homebase.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The spot a member has pinned on the world map for the group. Each member has one pin at a time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PinData implements Checkable
{
	/**
	 * The largest number the game's coordinates can hold.
	 */
	private static final int MAX_COORD = 16_383;
	private static final int MAX_PLANE = 3;

	/**
	 * False once the member has taken the pin away.
	 */
	private boolean placed;

	private int x;
	private int y;
	private int plane;

	/**
	 * Colour as 0xRRGGBB.
	 */
	private int color;

	/**
	 * When it was placed, so old pins can stop showing.
	 */
	private long at;

	@Override
	public void check()
	{
		x = Math.max(0, Math.min(MAX_COORD, x));
		y = Math.max(0, Math.min(MAX_COORD, y));
		plane = Math.max(0, Math.min(MAX_PLANE, plane));
	}
}
