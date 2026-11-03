package com.homebase.pings;

import java.awt.Color;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Something a group member pointed at, shown for a few seconds.
 */
@Value
public class Ping
{
	long memberId;
	String name;
	Color color;
	int world;

	/**
	 * Where it is, in the template map when inside an instance.
	 */
	WorldPoint point;

	/**
	 * The monster pointed at, or -1 for a tile.
	 */
	int npcIndex;
	int npcId;

	long createdAt;

	public boolean isNpc()
	{
		return npcIndex >= 0;
	}

	/**
	 * @return how far through its life the ping is, from 0 when made to 1 when it's gone
	 */
	public float age(long now, long lifetimeMs)
	{
		return Math.min(1f, Math.max(0f, (now - createdAt) / (float) lifetimeMs));
	}
}
