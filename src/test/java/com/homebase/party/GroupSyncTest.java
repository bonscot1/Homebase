package com.homebase.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
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
	public void silentNamelessConnectionsAreTakenToBeClosed()
	{
		long listed = 1_000_000;
		long later = listed + GroupSync.SILENT_CONNECTION_MS;
		// still within the time anyone actually there would answer in, so it counts as a stranger
		assertFalse(GroupSync.isClosedConnection(false, false, listed, listed + 1_000));
		// silent all that time, with no name: an old connection the server hasn't dropped yet
		assertTrue(GroupSync.isClosedConnection(false, false, listed, later));
		// said something, so it's open, and someone without a name is a stranger
		assertFalse(GroupSync.isClosedConnection(false, true, listed, later));
		// anyone with a name is judged by the name
		assertFalse(GroupSync.isClosedConnection(true, false, listed, later));
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
