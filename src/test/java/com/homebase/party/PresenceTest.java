package com.homebase.party;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyData;
import org.junit.Test;

public class PresenceTest
{
	private final Presence presence = new Presence(null, null, null, null);

	@Test
	public void aMemberCanChooseToHideTheirPosition()
	{
		WorldPoint where = new WorldPoint(3222, 3218, 0);
		WorldPoint fromParty = new WorldPoint(3000, 3000, 0);
		HomebasePresence shown = new HomebasePresence(true, HomebasePresence.packCoord(where));
		shown.setMemberId(7);
		presence.onHomebasePresence(shown);
		assertEquals(where, presence.position(7, fromParty));

		// hiding also ignores whatever RuneLite's party last had for them
		HomebasePresence hidden = new HomebasePresence(true, HomebasePresence.NO_POSITION);
		hidden.setMemberId(7);
		presence.onHomebasePresence(hidden);
		assertNull(presence.position(7, fromParty));

		// and showing it again brings it back
		presence.onHomebasePresence(shown);
		assertEquals(where, presence.position(7, fromParty));
	}

	@Test
	public void specialAttackCountsOnceItHasArrived()
	{
		assertFalse(presence.knowsSpec(7));

		PartyData stats = new PartyData();
		stats.setMemberId(7);
		stats.setRunEnergy(50);
		presence.onPartyData(stats);
		assertFalse(presence.knowsSpec(7));

		PartyData spec = new PartyData();
		spec.setMemberId(7);
		spec.setSpecEnergy(0);
		presence.onPartyData(spec);
		assertTrue(presence.knowsSpec(7));
		assertFalse(presence.knowsSpec(8));
	}

	@Test
	public void noRealPositionLooksLikeHidingOne()
	{
		// the top corner of the map, on the top floor, is as big as a position gets
		WorldPoint furthest = new WorldPoint(16_383, 16_383, 3);
		assertNotEquals(HomebasePresence.NO_POSITION, HomebasePresence.packCoord(furthest));
	}

	@Test
	public void quietMembersGoOffline()
	{
		long now = System.currentTimeMillis();
		presence.heard(7);
		assertTrue(presence.isOnline(7, now));
		assertTrue(presence.isOnline(7, now + Presence.TIMEOUT_MS - 1));
		assertFalse(presence.isOnline(7, now + Presence.TIMEOUT_MS + 1));
		// never heard from at all
		assertFalse(presence.isOnline(8, now));
	}

	@Test
	public void goingOfflineIsImmediate()
	{
		long now = System.currentTimeMillis();
		presence.heard(7);
		HomebasePresence offline = new HomebasePresence(false, null);
		offline.setMemberId(7);
		presence.onHomebasePresence(offline);
		assertFalse(presence.isOnline(7, now));

		// and coming back counts again
		HomebasePresence online = new HomebasePresence(true, null);
		online.setMemberId(7);
		presence.onHomebasePresence(online);
		assertTrue(presence.isOnline(7, System.currentTimeMillis()));
	}

	@Test
	public void sendsWhenItsTimeOrAfterMovingButNotTooOften()
	{
		// standing still, only the regular "still here"
		assertFalse(Presence.isSendDue(Presence.HEARTBEAT_MS - 1, false));
		assertTrue(Presence.isSendDue(Presence.HEARTBEAT_MS, false));
		// on the move, more often, but with a gap
		assertFalse(Presence.isSendDue(Presence.POSITION_GAP_MS - 1, true));
		assertTrue(Presence.isSendDue(Presence.POSITION_GAP_MS, true));
	}

	@Test
	public void positionsSurviveTheTripThroughAMessage()
	{
		WorldPoint where = new WorldPoint(3222, 9618, 2);
		assertEquals(where, WorldPoint.fromCoord(HomebasePresence.packCoord(where)));

		HomebasePresence message = new HomebasePresence(true, HomebasePresence.packCoord(where));
		message.setMemberId(7);
		presence.onHomebasePresence(message);
		// what they told us beats RuneLite's party, which is often a little behind
		assertEquals(where, presence.position(7, new WorldPoint(3000, 3000, 0)));
	}

	@Test
	public void aMemberOnAnOlderVersionFallsBackToRuneLitesParty()
	{
		// the message from a version of the plugin that didn't send positions
		HomebasePresence older = new Gson().fromJson("{\"online\":true}", HomebasePresence.class);
		assertTrue(older.isOnline());
		assertNull(older.getCoord());

		older.setMemberId(7);
		presence.onHomebasePresence(older);
		WorldPoint fromParty = new WorldPoint(3000, 3000, 0);
		assertEquals(fromParty, presence.position(7, fromParty));
		assertNull(presence.position(7, null));
	}
}
