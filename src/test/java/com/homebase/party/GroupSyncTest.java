package com.homebase.party;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
}
