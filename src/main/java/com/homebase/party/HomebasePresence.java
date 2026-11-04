package com.homebase.party;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * "Still here" while a member plays, and "going offline" just before they leave the party. The class
 * name is the message type on the wire, so it must not be renamed.
 */
@Getter
@RequiredArgsConstructor
public class HomebasePresence extends PartyMemberMessage
{
	private final boolean online;

	/**
	 * Where the member is, in the form {@link WorldPoint#fromCoord} reads. Null when going offline, and
	 * from members on a version of the plugin that didn't send it.
	 */
	private final Integer coord;

	/**
	 * @return the point packed the way {@link #coord} carries it
	 */
	static int packCoord(WorldPoint point)
	{
		return point.getPlane() << 28 | point.getX() << 14 | point.getY();
	}
}
