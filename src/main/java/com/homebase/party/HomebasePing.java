package com.homebase.party;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Points at a tile or a monster for a few seconds. Pings aren't saved or synced later, so they only
 * reach members who are online. The class name is the message type on the wire, so it must not be
 * renamed.
 */
@Getter
@RequiredArgsConstructor
public class HomebasePing extends PartyMemberMessage
{
	/**
	 * The world the ping was made on. Monsters are only the same monster on the same world.
	 */
	private final int world;

	/**
	 * Where it is, in the game's template map when inside an instance, as for marked tiles.
	 */
	private final int x;
	private final int y;
	private final int plane;

	/**
	 * The monster pinged, or -1 for a tile.
	 */
	private final int npcIndex;
	private final int npcId;

	/**
	 * The pinger's colour as RGB.
	 */
	private final int color;
}
