package com.homebase.sync;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Simulates a party of clients passing messages through the same encode/decode path the plugin uses.
 */
public class SyncEngineTest
{
	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	private static final long TODAY = 1_790_000_000_000L;

	private final Gson gson = new Gson();
	private final List<Node> nodes = new ArrayList<>();
	private long now = TODAY + 18 * HOUR;
	private int recordMessages;

	private final class Node
	{
		final String id;
		final GroupStore store = new GroupStore();
		final SyncEngine engine;
		boolean online;
		boolean summaryWanted;

		Node(String id)
		{
			this.id = id;
			this.engine = new SyncEngine(store, () -> now, new Random(id.hashCode()));
			nodes.add(this);
		}

		void publish(String section, String value)
		{
			engine.publish(Keys.member(id, section), id, new JsonPrimitive(value));
		}

		String valueOf(Node member, String section)
		{
			SyncRecord record = store.get(Keys.member(member.id, section));
			return record == null ? null : record.getData().getAsString();
		}
	}

	private static SyncRecord record(String member, String section, long version, String value)
	{
		return new SyncRecord(Keys.member(member, section), version, member, false, new JsonPrimitive(value));
	}

	private void join(Node node)
	{
		node.online = true;
		// the party service tells everyone, including the newcomer, that someone joined
		for (Node n : nodes)
		{
			if (n.online)
			{
				n.summaryWanted = true;
			}
		}
	}

	private void leave(Node node)
	{
		node.online = false;
		node.summaryWanted = false;
	}

	private void runFor(long millis)
	{
		for (long t = 0; t < millis; t += 1000)
		{
			now += 1000;
			for (Node n : nodes)
			{
				if (n.online)
				{
					flush(n);
				}
			}
		}
	}

	private void flush(Node sender)
	{
		try
		{
			if (sender.summaryWanted)
			{
				sender.summaryWanted = false;
				String packed = WireCodec.pack(gson.toJson(sender.engine.summary()));
				for (Node n : nodes)
				{
					if (n.online && n != sender)
					{
						Map<String, Long> summary = gson.fromJson(WireCodec.unpack(packed), WireCodec.SUMMARY_TYPE);
						if (n.engine.onSummary(summary))
						{
							n.summaryWanted = true;
						}
					}
				}
			}

			List<SyncRecord> due = sender.engine.takeDue(48_000, r -> 64);
			if (!due.isEmpty())
			{
				recordMessages++;
				String packed = WireCodec.pack(gson.toJson(due));
				for (Node n : nodes)
				{
					if (n.online && n != sender)
					{
						List<SyncRecord> records = gson.fromJson(WireCodec.unpack(packed), WireCodec.RECORDS_TYPE);
						n.engine.onRecords(records);
					}
				}
			}
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void newestCopyOfEachMemberWinsIndependently()
	{
		// I last got bonscott1's data at 5pm and mundell's at 1pm.
		// Fred last got mundell's data at 4pm and bonscott1's at 2pm.
		Node me = new Node("me");
		Node fred = new Node("fred");
		me.engine.load(Arrays.asList(
			record("bonscott1", "inv", TODAY + 17 * HOUR, "bonscott1 at 5pm"),
			record("mundell", "inv", TODAY + 13 * HOUR, "mundell at 1pm")));
		fred.engine.load(Arrays.asList(
			record("bonscott1", "inv", TODAY + 14 * HOUR, "bonscott1 at 2pm"),
			record("mundell", "inv", TODAY + 16 * HOUR, "mundell at 4pm")));

		join(me);
		join(fred);
		runFor(10_000);

		Node bonscott1 = new Node("bonscott1");
		Node mundell = new Node("mundell");
		// I update Fred's copy of bonscott1, Fred updates my copy of mundell
		assertEquals("bonscott1 at 5pm", me.valueOf(bonscott1, "inv"));
		assertEquals("bonscott1 at 5pm", fred.valueOf(bonscott1, "inv"));
		assertEquals("mundell at 4pm", me.valueOf(mundell, "inv"));
		assertEquals("mundell at 4pm", fred.valueOf(mundell, "inv"));
	}

	@Test
	public void dataIsRelayedToMembersWhoNeverOverlap()
	{
		Node alice = new Node("alice");
		Node bob = new Node("bob");
		Node carol = new Node("carol");

		join(alice);
		join(bob);
		bob.publish("bank", "bob's bank");
		runFor(5_000);
		leave(bob);

		// carol has never been online with bob
		join(carol);
		runFor(10_000);

		assertEquals("bob's bank", carol.valueOf(bob, "bank"));
	}

	@Test
	public void sectionsAreVersionedSeparately()
	{
		Node alice = new Node("alice");
		Node bob = new Node("bob");
		alice.engine.load(Arrays.asList(
			record("bob", "bank", TODAY + 9 * HOUR, "bank at 9am"),
			record("bob", "skills", TODAY + 15 * HOUR, "skills at 3pm")));
		bob.engine.load(Arrays.asList(
			record("bob", "bank", TODAY + 12 * HOUR, "bank at noon"),
			record("bob", "skills", TODAY + 10 * HOUR, "skills at 10am")));

		join(alice);
		join(bob);
		runFor(10_000);

		assertEquals("bank at noon", alice.valueOf(bob, "bank"));
		assertEquals("skills at 3pm", alice.valueOf(bob, "skills"));
		assertEquals("bank at noon", bob.valueOf(bob, "bank"));
		assertEquals("skills at 3pm", bob.valueOf(bob, "skills"));
	}

	@Test
	public void onlyOneMemberRelaysWhenSeveralHoldTheSameData()
	{
		Node alice = new Node("alice");
		Node bob = new Node("bob");
		Node carol = new Node("carol");
		Node dave = new Node("dave");
		SyncRecord bank = record("alice", "bank", TODAY + 9 * HOUR, "alice's bank");
		alice.engine.load(Arrays.asList(bank));
		bob.engine.load(Arrays.asList(bank));
		carol.engine.load(Arrays.asList(bank));

		join(bob);
		join(carol);
		runFor(5_000);
		recordMessages = 0;

		join(dave);
		runFor(10_000);

		assertEquals("alice's bank", dave.valueOf(alice, "bank"));
		assertEquals("only one of bob and carol should send it", 1, recordMessages);
	}

	@Test
	public void memberDataCanOnlyBeWrittenByItsOwner()
	{
		Node alice = new Node("alice");
		SyncRecord forged = new SyncRecord(Keys.member("bob", "bank"), now, "mallory", false, new JsonPrimitive("forged"));
		assertEquals(0, alice.engine.onRecords(Arrays.asList(forged)));
		assertNull(alice.store.get(forged.getKey()));
	}

	@Test
	public void sharedDataCanBeWrittenByAnyone()
	{
		Node alice = new Node("alice");
		SyncRecord goal = new SyncRecord(Keys.shared("goal", "wt"), now, "bob", false, new JsonPrimitive("50 firemaking"));
		assertEquals(1, alice.engine.onRecords(Arrays.asList(goal)));
	}

	@Test
	public void versionsFarInTheFutureAreRejected()
	{
		Node alice = new Node("alice");
		SyncRecord future = record("bob", "bank", now + TimeUnit.DAYS.toMillis(30), "from the future");
		assertEquals(0, alice.engine.onRecords(Arrays.asList(future)));
	}

	@Test
	public void deletedItemsStayDeletedWhenAnOldCopyArrives()
	{
		Node alice = new Node("alice");
		Node bob = new Node("bob");
		String key = Keys.shared("request", "rune-scimitar");
		SyncRecord oldCopy = new SyncRecord(key, now - HOUR, "carol", false, new JsonPrimitive("make me a rune scimitar"));
		alice.engine.load(Arrays.asList(oldCopy));
		bob.engine.load(Arrays.asList(oldCopy));
		alice.engine.delete(key, "alice");

		join(alice);
		join(bob);
		runFor(10_000);

		assertNull(alice.store.get(key));
		assertNull(bob.store.get(key));
	}

	@Test
	public void unchangedDataIsNotRepublished()
	{
		Node alice = new Node("alice");
		assertTrue(alice.engine.publish(Keys.member("alice", "inv"), "alice", new JsonPrimitive("x")) != null);
		assertNull(alice.engine.publish(Keys.member("alice", "inv"), "alice", new JsonPrimitive("x")));
	}

	@Test
	public void versionsKeepIncreasingEvenIfTheClockGoesBack()
	{
		Node alice = new Node("alice");
		String key = Keys.member("alice", "inv");
		SyncRecord first = alice.engine.publish(key, "alice", new JsonPrimitive("first"));
		now -= HOUR;
		SyncRecord second = alice.engine.publish(key, "alice", new JsonPrimitive("second"));
		assertTrue(second.getVersion() > first.getVersion());
	}

	@Test
	public void summaryReportsWhenThePeerIsAhead()
	{
		GroupStore store = new GroupStore();
		SyncEngine engine = new SyncEngine(store, () -> now, new Random(1));
		engine.load(Arrays.asList(record("bob", "bank", now - HOUR, "old")));
		assertTrue(engine.onSummary(java.util.Collections.singletonMap(Keys.member("bob", "bank"), now)));
		assertFalse(engine.onSummary(java.util.Collections.singletonMap(Keys.member("bob", "bank"), now - 2 * HOUR)));
	}
}
