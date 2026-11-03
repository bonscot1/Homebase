package com.homebase.party;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PartyMembersTest
{
	@Test
	public void newestConnectionPerPlayerIsKept()
	{
		// connections in the order they joined: Bonscott Kid's old one, then Goulashnicov, then
		// Bonscott Kid again after logging back in, then someone whose name hasn't arrived yet
		List<String[]> members = Arrays.asList(
			new String[]{"old", "Bonscott Kid"},
			new String[]{"alice", "Goulashnicov"},
			new String[]{"new", "Bonscott Kid"},
			new String[]{"joining", null});

		List<String[]> current = PartyMembers.newestPerName(members, m -> m[1]);

		assertEquals(3, current.size());
		assertEquals("alice", current.get(0)[0]);
		assertEquals("new", current.get(1)[0]);
		assertEquals("joining", current.get(2)[0]);
	}
}
