package com.homebase.party;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Constructor;
import net.runelite.api.Client;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import org.junit.Before;
import org.junit.Test;

public class PresenceTest
{
	private Presence presence;

	@Before
	public void setUp() throws Exception
	{
		Constructor<Presence> c = Presence.class.getDeclaredConstructor(Client.class, PartyService.class, WSClient.class);
		c.setAccessible(true);
		presence = c.newInstance(null, null, null);
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
		HomebasePresence offline = new HomebasePresence(false);
		offline.setMemberId(7);
		presence.onHomebasePresence(offline);
		assertFalse(presence.isOnline(7, now));

		// and coming back counts again
		HomebasePresence online = new HomebasePresence(true);
		online.setMemberId(7);
		presence.onHomebasePresence(online);
		assertTrue(presence.isOnline(7, System.currentTimeMillis()));
	}
}
