package com.homebase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Test;

/**
 * Records passing through a member whose version has never heard of them. They're written here by hand, as a
 * newer version writes them, so these tests only use what every version has and can also be run against the
 * version on the Plugin Hub.
 */
public class UnknownRecordsRelayTest
{
	private static final long SECOND = SimulatedParty.SECOND;
	private static final long CATCH_UP = 20 * SECOND;

	private final SimulatedParty party = new SimulatedParty();
	private final SimulatedClient alice = party.member("Alice");
	private final SimulatedClient bob = party.member("Bob");
	private final SimulatedClient carol = party.member("Carol");

	@After
	public void nothingThrew()
	{
		party.assertNoErrors();
	}

	private List<SyncRecord> publishNewerKinds()
	{
		long now = party.now();
		String me = alice.id();
		alice.engine().publish(Keys.member(me, "storagelog"), me,
			new JsonParser().parse("{\"entries\":[{\"item\":453,\"amount\":-5,\"at\":" + now + "}]}"));
		alice.engine().publish(Keys.member(me, "feed"), me,
			new JsonParser().parse("{\"entries\":[{\"at\":" + now + ",\"type\":\"drop\",\"name\":\"Draconic visage\",\"quantity\":1,\"value\":3000000,\"source\":\"Vorkath\"}]}"));
		alice.engine().publish(Keys.member(me, "pin"), me,
			new JsonParser().parse("{\"placed\":true,\"x\":3222,\"y\":3218,\"plane\":0,\"color\":2146368,\"at\":" + now + "}"));
		alice.engine().publish(Keys.shared("return", "453"), me, new JsonParser().parse("{\"item\":453}"));
		alice.engine().publish(Keys.shared("return", "385"), me, new JsonParser().parse("{\"item\":385}"));
		alice.engine().delete(Keys.shared("return", "385"), me);
		JsonObject profile = new JsonObject();
		profile.addProperty("name", "Alice");
		profile.addProperty("world", 301);
		profile.addProperty("groupName", party.groupName());
		profile.addProperty("color", 2146368);
		alice.engine().publish(Keys.member(me, "profile"), me, profile);

		List<SyncRecord> out = new ArrayList<>();
		for (String key : Arrays.asList(Keys.member(me, "storagelog"), Keys.member(me, "feed"), Keys.member(me, "pin"),
			Keys.shared("return", "453"), Keys.member(me, "profile")))
		{
			out.add(alice.store().get(key));
		}
		return out;
	}

	@Test
	public void recordsOfUnknownKindsGoThroughAMemberAndTheirSaveFileUnchanged()
	{
		party.run(5 * SECOND);
		List<SyncRecord> records = publishNewerKinds();
		alice.connect();
		bob.connect();
		party.run(CATCH_UP);
		alice.disconnect();

		// Bob restarts, so all he has to pass on is what went through his save file
		bob.restartPlugin();
		party.run(5 * SECOND);
		bob.connect();
		carol.connect();
		party.run(CATCH_UP);

		for (SyncRecord record : records)
		{
			assertEquals(record, carol.store().get(record.getKey()));
		}
		assertEquals("the delete is passed on too", 0, carol.store().withPrefix(Keys.shared("return", "385")).size());
		assertTrue(carol.recordsAsText().stream().anyMatch(text -> text.startsWith(Keys.shared("return", "385"))));
		SimulatedParty.assertSameRecords(bob, carol);
	}

	@Test
	public void aNewerCopyOfAnUnknownKindReplacesTheOldOneOnTheWayThrough()
	{
		party.run(5 * SECOND);
		String me = alice.id();
		alice.engine().publish(Keys.member(me, "pin"), me, new JsonParser().parse("{\"placed\":true,\"x\":1}"));
		alice.connect();
		bob.connect();
		party.run(CATCH_UP);

		alice.engine().publish(Keys.member(me, "pin"), me, new JsonParser().parse("{\"placed\":false,\"x\":2}"));
		party.run(5 * SECOND);
		alice.disconnect();
		carol.connect();
		party.run(CATCH_UP);

		SyncRecord pin = carol.store().get(Keys.member(me, "pin"));
		assertNotNull(pin);
		assertEquals(2, pin.getData().getAsJsonObject().get("x").getAsInt());
	}
}
