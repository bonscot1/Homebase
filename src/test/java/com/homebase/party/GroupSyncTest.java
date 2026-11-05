package com.homebase.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.GameState;
import org.junit.Test;

public class GroupSyncTest
{
	@Test
	public void memberNamesMatchHoweverTheGameWritesThem()
	{
		// the group's member list and the party can spell the same name with different spaces and case
		assertEquals(GroupSync.rosterKey("Bonscott Kid"), GroupSync.rosterKey("bonscott\u00A0kid"));
		assertEquals(GroupSync.rosterKey("Mundell"), GroupSync.rosterKey("MUNDELL"));
		assertNotEquals(GroupSync.rosterKey("Mundell"), GroupSync.rosterKey("Mundel1"));
	}

	@Test
	public void aLoggedOutMemberOnlyFinishesSendingBeforeGoingQuiet()
	{
		assertTrue(GroupSync.isFinishingAfterLogout(GameState.LOGIN_SCREEN, 0));
		assertTrue(GroupSync.isFinishingAfterLogout(GameState.LOGIN_SCREEN, GroupSync.LOGOUT_SEND_MS - 1));
		assertFalse(GroupSync.isFinishingAfterLogout(GameState.LOGIN_SCREEN, GroupSync.LOGOUT_SEND_MS));
		// logged in again, even on another account, there's nothing to finish
		assertFalse(GroupSync.isFinishingAfterLogout(GameState.LOGGED_IN, 0));
	}

	@Test
	public void quietNamelessConnectionsAreTakenToBeClosed()
	{
		long lastSign = 1_000_000;
		long later = lastSign + GroupSync.SILENT_CONNECTION_MS;
		// still within the time anyone actually there would answer in, so it counts as a stranger
		assertFalse(GroupSync.isClosedConnection(false, lastSign, lastSign + 1_000));
		// no name, and nothing said for long enough
		assertTrue(GroupSync.isClosedConnection(false, lastSign, later));
		// a name means they're really there, however quiet
		assertFalse(GroupSync.isClosedConnection(true, lastSign, later));
	}

	@Test
	public void onlyNamelessMembersWhoHaveSaidNothingAreSilent()
	{
		assertTrue(GroupSync.isSilent(false, false));
		// said something, so there's someone there
		assertFalse(GroupSync.isSilent(false, true));
		// anyone with a name is judged by the name
		assertFalse(GroupSync.isSilent(true, false));
	}
}
