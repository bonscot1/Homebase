package com.homebase;

import com.google.gson.JsonObject;
import com.homebase.bounties.BountyService;
import com.homebase.data.FeedData;
import com.homebase.data.GoalData;
import com.homebase.data.GroupIndex;
import com.homebase.data.ItemsData;
import com.homebase.data.PinData;
import com.homebase.data.ProfileData;
import com.homebase.data.RequestData;
import com.homebase.data.Section;
import com.homebase.data.StorageLogData;
import com.homebase.goals.GoalService;
import com.homebase.party.GroupSync;
import com.homebase.requests.RequestService;
import com.homebase.storage.ReturnTags;
import com.homebase.storage.StorageHistory;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Skill;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Two or three members' clients running the real sync, with only the party server, the game and the disk
 * faked: everything in the group storage history, drops and deaths, map pins, return marks, bounties, goals
 * and requests, read back the way the panel reads it.
 */
public class GroupSyncSimulationTest
{
	private static final long SECOND = SimulatedParty.SECOND;
	private static final long MINUTE = SimulatedParty.MINUTE;
	private static final long CATCH_UP = 20 * SECOND;

	private static final int COAL = 453;
	private static final int SHARK = 385;
	private static final int LOBSTER = 379;
	private static final int RUNE_SCIMITAR = 1333;
	private static final int LEVEL_50_XP = 101_333;

	private final SimulatedParty party = new SimulatedParty();
	private final SimulatedClient alice = party.member("Alice");
	private final SimulatedClient bob = party.member("Bob");
	private final SimulatedClient carol = party.member("Carol");

	@Before
	public void setUp()
	{
		for (SimulatedClient member : Arrays.asList(alice, bob, carol))
		{
			member.onGroupOpened(client -> client.get(ReturnTags.class).setEngine(client.engine()));
		}
		// the game takes a few ticks to say which group an account is in
		party.run(5 * SECOND);
	}

	@After
	public void nothingThrew()
	{
		party.assertNoErrors();
	}

	private void connect(SimulatedClient... members)
	{
		for (SimulatedClient member : members)
		{
			member.connect();
		}
		party.run(CATCH_UP);
		for (SimulatedClient member : members)
		{
			assertEquals(member.name() + " should be connected", GroupSync.Status.CONNECTED, member.status());
		}
	}

	/**
	 * Group storage was saved with some of an item put in or taken out, as the storage watcher records it.
	 */
	private void saveStorage(SimulatedClient who, int item, int amount)
	{
		StorageLogData log = who.collector().shared(Section.STORAGE_LOG, StorageLogData.class);
		if (log == null)
		{
			log = new StorageLogData();
		}
		log.add(item, amount, party.now());
		who.collector().share(Section.STORAGE_LOG, log);
	}

	private void drop(SimulatedClient who, String item)
	{
		addToFeed(who, new FeedData.Entry(party.now(), FeedData.DROP, item, 1, 50_000, "Vorkath"));
	}

	private void die(SimulatedClient who, String where)
	{
		addToFeed(who, new FeedData.Entry(party.now(), FeedData.DEATH, where, 0, 0, null));
	}

	private static void addToFeed(SimulatedClient who, FeedData.Entry entry)
	{
		FeedData feed = who.collector().shared(Section.FEED, FeedData.class);
		if (feed == null)
		{
			feed = new FeedData();
		}
		feed.add(entry);
		who.collector().share(Section.FEED, feed);
	}

	private void pin(SimulatedClient who, int x, int y)
	{
		who.collector().share(Section.PIN, new PinData(true, x, y, 0, 0x20C040, party.now()));
	}

	private static void takePinAway(SimulatedClient who)
	{
		PinData pin = who.collector().shared(Section.PIN, PinData.class);
		pin.setPlaced(false);
		who.collector().share(Section.PIN, pin);
	}

	private static ReturnTags marks(SimulatedClient who)
	{
		return who.get(ReturnTags.class);
	}

	private static String setGoal(SimulatedClient who)
	{
		return who.goals().create(GoalData.Type.SKILL, "Firemaking", 50, Collections.emptyList());
	}

	private static void setBounty(SimulatedClient who, String goal, int item, SimulatedClient locked)
	{
		who.bounties().create(item, "Item " + item, 5, goal, Collections.singletonList(locked.id()));
	}

	private static String askFor(SimulatedClient who, String item)
	{
		who.requests().create(item, 2, null);
		for (RequestService.Request request : who.requests().list())
		{
			if (item.equals(request.getData().getItem()))
			{
				return request.getId();
			}
		}
		throw new AssertionError("The request wasn't made");
	}

	private static RequestData request(SimulatedClient reader, String id)
	{
		for (RequestService.Request request : reader.requests().list())
		{
			if (request.getId().equals(id))
			{
				return request.getData();
			}
		}
		return null;
	}

	private static StorageLogData logOf(SimulatedClient reader, SimulatedClient member)
	{
		return reader.view().get(member.id(), Section.STORAGE_LOG, StorageLogData.class);
	}

	private static FeedData feedOf(SimulatedClient reader, SimulatedClient member)
	{
		return reader.view().get(member.id(), Section.FEED, FeedData.class);
	}

	/**
	 * @return every member's storage log, gathered the way the Storage tab does
	 */
	private static List<StorageHistory.MemberLog> logsOn(SimulatedClient reader)
	{
		List<StorageHistory.MemberLog> logs = new ArrayList<>();
		for (String id : reader.view().memberIds())
		{
			ProfileData profile = reader.view().get(id, Section.PROFILE, ProfileData.class);
			StorageLogData log = reader.view().get(id, Section.STORAGE_LOG, StorageLogData.class);
			if (profile != null && log != null)
			{
				logs.add(new StorageHistory.MemberLog(profile.getName(), log, reader.view().updatedAt(id, Section.STORAGE_LOG)));
			}
		}
		return logs;
	}

	// created, changed and deleted with both clients online

	@Test
	public void storageLogEntriesReachTheOtherClientAsTheyHappen()
	{
		connect(alice, bob);

		saveStorage(alice, COAL, 20);
		party.run(5 * SECOND);
		assertEquals(logOf(alice, alice).getEntries(), logOf(bob, alice).getEntries());
		assertEquals(1, logOf(bob, alice).getEntries().size());

		saveStorage(alice, SHARK, -5);
		saveStorage(bob, COAL, -3);
		party.run(5 * SECOND);

		assertEquals(2, logOf(bob, alice).getEntries().size());
		assertEquals(1, logOf(alice, bob).getEntries().size());
		List<StorageHistory.Line> lines = StorageHistory.lines(logsOn(bob));
		assertEquals(3, lines.size());
		assertEquals(lines, StorageHistory.lines(logsOn(alice)));
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void dropsAndDeathsReachTheOtherClientAndOldOnesFallOff()
	{
		connect(alice, bob);

		drop(alice, "Draconic visage");
		party.run(3 * SECOND);
		die(alice, "Wilderness");
		party.run(3 * SECOND);

		List<FeedData.Entry> entries = feedOf(bob, alice).getEntries();
		assertEquals(2, entries.size());
		assertEquals(FeedData.DROP, entries.get(0).getType());
		assertEquals("Draconic visage", entries.get(0).getName());
		assertEquals(FeedData.DEATH, entries.get(1).getType());

		for (int i = 0; i < FeedData.MAX_ENTRIES + 5; i++)
		{
			drop(alice, "Drop " + i);
		}
		party.run(3 * SECOND);

		entries = feedOf(bob, alice).getEntries();
		assertEquals(FeedData.MAX_ENTRIES, entries.size());
		assertEquals("Drop 5", entries.get(0).getName());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void aMapPinIsPlacedMovedAndTakenAwayForBothClients()
	{
		connect(alice, bob);

		pin(alice, 3222, 3218);
		party.run(3 * SECOND);
		assertEquals(1, bob.groupIndex().pins().size());
		GroupIndex.Pin seen = bob.groupIndex().pins().get(0);
		assertEquals("Alice", seen.getName());
		assertEquals(3222, seen.getPoint().getX());

		pin(alice, 3100, 3500);
		party.run(3 * SECOND);
		assertEquals(1, bob.groupIndex().pins().size());
		assertEquals(3500, bob.groupIndex().pins().get(0).getPoint().getY());

		takePinAway(alice);
		party.run(3 * SECOND);
		assertTrue(bob.groupIndex().pins().isEmpty());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void returnMarksComeAndGoAndDecideWhatsOwed()
	{
		connect(alice, bob);

		marks(alice).mark(COAL);
		party.run(3 * SECOND);
		assertTrue(marks(bob).marked().containsKey(COAL));

		saveStorage(bob, COAL, -5);
		party.run(3 * SECOND);
		List<StorageHistory.Owed> owed = StorageHistory.owed(logsOn(alice), marks(alice).marked());
		assertEquals(1, owed.size());
		assertEquals("Bob", owed.get(0).getWho());
		assertEquals(5, owed.get(0).getAmount());
		assertEquals(owed, StorageHistory.owed(logsOn(bob), marks(bob).marked()));

		// anyone can take the mark off, and then nothing is owed
		marks(bob).unmark(COAL);
		party.run(3 * SECOND);
		assertTrue(marks(alice).marked().isEmpty());
		assertTrue(StorageHistory.owed(logsOn(alice), marks(alice).marked()).isEmpty());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void bountiesAndGoalsAreSetAndRemovedForBothClients()
	{
		connect(alice, bob);

		String goal = setGoal(alice);
		setBounty(alice, goal, COAL, bob);
		setBounty(alice, goal, SHARK, bob);
		party.run(3 * SECOND);

		assertEquals(goal, bob.goals().list().get(0).getId());
		assertEquals(2, bob.bounties().list().size());
		BountyService.Lock lock = bob.bounties().lockFor(COAL);
		assertTrue(lock.isLocked());
		assertEquals(5, lock.getQuantity());
		assertEquals("50 Firemaking", lock.getGoal());
		assertFalse("Alice isn't on its list", alice.bounties().lockFor(COAL).isLocked());

		// reaching the goal lifts it, as worked out on each client from what's shared
		bob.setXp(Skill.FIREMAKING, LEVEL_50_XP);
		party.run(8 * SECOND);
		assertTrue(alice.goals().progress(alice.goals().get(goal).getData(), bob.id()).isDone());
		assertFalse(bob.bounties().lockFor(COAL).isLocked());
		assertFalse(alice.bounties().isLocked(alice.bounties().list().get(0).getData(), bob.id()));

		// anyone can remove a bounty, whoever set it, and the same goes for goals
		String coal = bountyId(bob, COAL);
		bob.bounties().delete(coal);
		party.run(3 * SECOND);
		assertNull(alice.bounties().lockFor(COAL));
		assertEquals(1, alice.bounties().list().size());

		alice.goals().delete(goal);
		party.run(3 * SECOND);
		assertTrue(bob.goals().list().isEmpty());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	private static String bountyId(SimulatedClient who, int item)
	{
		for (BountyService.Bounty bounty : who.bounties().list())
		{
			if (bounty.getData().getItemId() == item)
			{
				return bounty.getId();
			}
		}
		throw new AssertionError("No bounty on " + item);
	}

	@Test
	public void aRequestGoesFromAskedForToDeliveredOnBothClients()
	{
		connect(alice, bob);

		String id = askFor(alice, "Rune scimitar");
		party.run(3 * SECOND);
		assertEquals("Rune scimitar", request(bob, id).getItem());
		assertNull(request(bob, id).getAssignedTo());

		bob.requests().claim(id);
		party.run(3 * SECOND);
		assertEquals(bob.id(), request(alice, id).getAssignedTo());
		assertEquals("Bob", request(alice, id).getAssignedToName());

		bob.requests().keep(id, RUNE_SCIMITAR, 2);
		party.run(3 * SECOND);
		assertEquals(2, alice.requests().reservationsFor(RUNE_SCIMITAR).get(0).getQuantity());
		assertEquals(alice.id(), bob.requests().reservationsFor(RUNE_SCIMITAR).get(0).getRequestedBy());

		alice.requests().receive(id, 2, true);
		party.run(3 * SECOND);
		assertTrue(request(bob, id).isDone());
		assertTrue(bob.requests().reservationsFor(RUNE_SCIMITAR).isEmpty());

		bob.requests().delete(id);
		party.run(3 * SECOND);
		assertNull(request(alice, id));
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void onlyTheOwnerOfAMembersRecordCanDeleteIt()
	{
		connect(alice, bob);
		saveStorage(alice, COAL, 20);
		party.run(3 * SECOND);
		String key = Section.STORAGE_LOG.keyFor(alice.id());

		// another member's client claiming to, as a modified one could
		SyncRecord forged = new SyncRecord(key, party.now() + 1, bob.id(), true, null);
		assertEquals(0, alice.engine().onRecords(Collections.singletonList(forged)));
		assertNotNull(alice.store().get(key));

		alice.engine().delete(key, alice.id());
		party.run(3 * SECOND);
		assertNull(bob.store().get(key));
		SimulatedParty.assertSameRecords(alice, bob);
	}

	// offline, then catching up

	/**
	 * Something of everything for two members to have, with a goal and bounty on it.
	 */
	private String[] shareSomeOfEverything(SimulatedClient who, SimulatedClient other)
	{
		String goal = setGoal(who);
		setBounty(who, goal, COAL, other);
		String request = askFor(who, "Lobster");
		marks(who).mark(SHARK);
		saveStorage(who, COAL, 20);
		drop(who, "Draconic visage");
		pin(who, 3222, 3218);
		return new String[]{goal, request};
	}

	@Test
	public void aClientThatWasOfflineCatchesUpOnEverythingIncludingDeletes()
	{
		connect(alice, bob);
		String[] ids = shareSomeOfEverything(alice, bob);
		String oldGoal = ids[0];
		String oldRequest = ids[1];
		party.run(5 * SECOND);
		assertEquals(1, bob.bounties().list().size());
		String oldBounty = bountyId(bob, COAL);

		bob.disconnect();

		// changed
		saveStorage(alice, LOBSTER, -2);
		die(alice, "Wilderness");
		pin(alice, 3100, 3500);
		alice.requests().claim(oldRequest);
		// deleted
		alice.bounties().delete(oldBounty);
		alice.goals().delete(oldGoal);
		marks(alice).unmark(SHARK);
		// created
		String newGoal = alice.goals().create(GoalData.Type.TOTAL_LEVEL, null, 500, Collections.emptyList());
		setBounty(alice, newGoal, LOBSTER, bob);
		marks(alice).mark(RUNE_SCIMITAR);
		String newRequest = askFor(alice, "Rune scimitar");
		// created and deleted while Bob wasn't looking, so all he'll ever see of it is that it's gone
		String brief = askFor(alice, "Bones");
		alice.requests().delete(brief);
		party.run(5 * SECOND);

		assertNotNull("not told yet", bob.goals().get(oldGoal));

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertEquals(2, logOf(bob, alice).getEntries().size());
		assertEquals(2, feedOf(bob, alice).getEntries().size());
		assertEquals(3500, bob.groupIndex().pins().get(0).getPoint().getY());
		assertEquals(alice.id(), request(bob, oldRequest).getAssignedTo());
		assertNull(bob.goals().get(oldGoal));
		assertEquals(1, bob.bounties().list().size());
		assertEquals(LOBSTER, bob.bounties().list().get(0).getData().getItemId());
		assertTrue(bob.bounties().lockFor(LOBSTER).isLocked());
		assertEquals(Collections.singleton(RUNE_SCIMITAR), marks(bob).marked().keySet());
		assertNotNull(request(bob, newRequest));
		assertNull(request(bob, brief));
	}

	@Test
	public void changesMadeWhileOfflineGoOutOnReconnectingFromBothSides()
	{
		connect(alice, bob);
		bob.disconnect();

		String[] alices = shareSomeOfEverything(alice, bob);
		String[] bobs = shareSomeOfEverything(bob, alice);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertEquals(2, bob.goals().list().size());
		assertEquals(2, alice.bounties().list().size());
		assertNotNull(alice.goals().get(bobs[0]));
		assertNotNull(request(bob, alices[1]));
		assertEquals(2, alice.groupIndex().pins().size());
	}

	@Test
	public void aMemberStaysConnectedThroughALogoutAndHearsEverythingWhileAway()
	{
		connect(alice, bob);
		bob.logOut();
		party.run(MINUTE);

		shareSomeOfEverything(alice, bob);
		party.run(10 * SECOND);
		SimulatedParty.assertSameRecords(alice, bob);

		// no Connect needed to come back
		bob.logIn();
		party.run(CATCH_UP);
		assertEquals(GroupSync.Status.CONNECTED, bob.status());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	// A and B online, then B and C, with A and C never online together

	@Test
	public void dataReachesSomeoneItsOwnerIsNeverOnlineWith()
	{
		connect(alice, bob);
		String[] ids = shareSomeOfEverything(alice, bob);
		party.run(5 * SECOND);
		String bounty = bountyId(bob, COAL);
		alice.disconnect();
		bob.disconnect();

		// something Alice deleted, and something she changed, before going for good
		alice.connect();
		bob.connect();
		party.run(CATCH_UP);
		alice.bounties().delete(bounty);
		alice.requests().claim(ids[1]);
		party.run(5 * SECOND);
		alice.disconnect();

		carol.connect();
		party.run(CATCH_UP);
		assertEquals(GroupSync.Status.CONNECTED, carol.status());

		// Carol has everything of Alice's, read as anyone reads it, without ever being online with her
		SimulatedParty.assertSameRecords(bob, carol);
		assertEquals(1, logOf(carol, alice).getEntries().size());
		assertEquals(1, feedOf(carol, alice).getEntries().size());
		assertEquals(1, carol.groupIndex().pins().size());
		assertEquals("Alice", carol.groupIndex().pins().get(0).getName());
		assertEquals(Collections.singleton(SHARK), marks(carol).marked().keySet());
		assertNotNull(carol.goals().get(ids[0]));
		assertEquals(alice.id(), request(carol, ids[1]).getAssignedTo());
		assertTrue("the delete got there too", carol.bounties().list().isEmpty());

		// and it works the other way: Carol's goes through Bob to Alice
		String carols = setGoal(carol);
		saveStorage(carol, LOBSTER, 7);
		party.run(5 * SECOND);
		carol.disconnect();

		alice.connect();
		party.run(CATCH_UP);
		assertNotNull(alice.goals().get(carols));
		assertEquals(1, logOf(alice, carol).getEntries().size());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void relayedRecordsKeepWhoWroteThem()
	{
		connect(alice, bob);
		String goal = setGoal(alice);
		party.run(5 * SECOND);
		alice.disconnect();
		carol.connect();
		party.run(CATCH_UP);

		SyncRecord relayed = carol.store().get(Keys.shared(GoalService.TYPE, goal));
		assertEquals(alice.id(), relayed.getAuthor());
		assertEquals(alice.store().get(Keys.shared(GoalService.TYPE, goal)).getVersion(), relayed.getVersion());
	}

	// both clients changing the same record while apart

	@Test
	public void theNewestChangeToARequestWinsAfterBeingApart()
	{
		connect(alice, bob, carol);
		String id = askFor(alice, "Rune scimitar");
		party.run(5 * SECOND);
		alice.disconnect();
		bob.disconnect();
		carol.disconnect();

		bob.requests().claim(id);
		party.run(10 * SECOND);
		carol.requests().claim(id);
		party.run(10 * SECOND);

		alice.connect();
		bob.connect();
		carol.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob, carol);
		assertEquals("the later of the two", carol.id(), request(alice, id).getAssignedTo());
	}

	@Test
	public void changesToARequestWhileApartDontMerge()
	{
		connect(alice, bob);
		String id = askFor(alice, "Rune scimitar");
		party.run(3 * SECOND);
		bob.requests().claim(id);
		party.run(3 * SECOND);
		bob.disconnect();

		// Bob trades one over, while Alice, not knowing, gives the request back for someone else to take
		bob.requests().receive(id, 1, false);
		party.run(5 * SECOND);
		alice.requests().unclaim(id);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		// a request is one record, so the older change is lost rather than added together
		SimulatedParty.assertSameRecords(alice, bob);
		assertEquals(0, request(bob, id).getDelivered());
		assertNull(request(bob, id).getAssignedTo());
	}

	@Test
	public void aNewerEditBringsBackSomethingDeletedWhileApart()
	{
		connect(alice, bob);
		String id = askFor(alice, "Rune scimitar");
		party.run(3 * SECOND);
		bob.disconnect();

		alice.requests().delete(id);
		party.run(5 * SECOND);
		bob.requests().claim(id);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertEquals(bob.id(), request(alice, id).getAssignedTo());
	}

	@Test
	public void aNewerDeleteBeatsAnEditMadeWhileApart()
	{
		connect(alice, bob);
		String id = askFor(alice, "Rune scimitar");
		party.run(3 * SECOND);
		bob.disconnect();

		bob.requests().claim(id);
		party.run(5 * SECOND);
		alice.requests().delete(id);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertNull(request(bob, id));
	}

	@Test
	public void aBountySetOnAGoalDeletedWhileApartIsLeftWithNoGoal()
	{
		connect(alice, bob);
		String goal = setGoal(alice);
		party.run(3 * SECOND);
		bob.disconnect();

		alice.goals().delete(goal);
		setBounty(bob, goal, COAL, alice);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertNull(alice.goals().get(goal));
		assertEquals(1, alice.bounties().list().size());
		assertNull(alice.bounties().lockFor(COAL));
	}

	@Test
	public void markingAndUnmarkingWhileApartLeavesTheNewest()
	{
		connect(alice, bob);
		marks(alice).mark(COAL);
		party.run(3 * SECOND);
		bob.disconnect();

		marks(bob).unmark(COAL);
		party.run(5 * SECOND);
		marks(alice).unmark(COAL);
		marks(alice).mark(COAL);
		marks(bob).mark(SHARK);
		party.run(5 * SECOND);
		marks(alice).mark(SHARK);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertEquals(Arrays.asList(COAL, SHARK), sorted(marks(bob).marked()));
	}

	private static List<Integer> sorted(Map<Integer, Long> items)
	{
		List<Integer> out = new ArrayList<>(items.keySet());
		out.sort((a, b) -> Integer.compare(b, a));
		return out;
	}

	@Test
	public void storageLogsFromMembersApartDontOverwriteEachOther()
	{
		connect(alice, bob);
		bob.disconnect();

		saveStorage(alice, COAL, 20);
		saveStorage(bob, SHARK, -5);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		assertEquals(2, StorageHistory.lines(logsOn(alice)).size());
		assertEquals(2, StorageHistory.lines(logsOn(bob)).size());
	}

	@Test
	public void aFastClockWinsEvenIfItsChangeCameFirst()
	{
		alice.setClockAhead(10 * MINUTE);
		connect(alice, bob);
		String id = askFor(alice, "Rune scimitar");
		party.run(3 * SECOND);
		bob.disconnect();

		alice.requests().claim(id);
		party.run(MINUTE);
		// Bob changes it later, but his clock says it's earlier than Alice's change
		bob.requests().claim(id);
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertEquals(alice.id(), request(bob, id).getAssignedTo());
	}

	@Test
	@Ignore("Two clients holding the same version of a key by different authors never settle it, as summaries only compare version numbers")
	public void twoChangesWithTheSameVersionEndUpTheSameEverywhere()
	{
		connect(alice, bob);
		String id = askFor(alice, "Rune scimitar");
		party.run(3 * SECOND);
		alice.disconnect();
		bob.disconnect();

		// within the same millisecond, or both one past a version from a client whose clock is ahead
		alice.requests().claim(id);
		bob.requests().claim(id);

		// what's waiting to be sent would settle it, but restarting forgets that and only summaries are left
		alice.restartPlugin();
		bob.restartPlugin();
		party.run(5 * SECOND);
		alice.connect();
		bob.connect();
		party.run(CATCH_UP);

		assertEquals(request(alice, id).getAssignedTo(), request(bob, id).getAssignedTo());
	}

	// restarting, reloading from the saved copy, then catching up

	@Test
	public void aRestartedClientShowsItsSavedCopyThenCatchesUp()
	{
		connect(alice, bob);
		String[] ids = shareSomeOfEverything(alice, bob);
		party.run(5 * SECOND);
		List<String> beforeRestart = bob.recordsAsText();

		bob.restartPlugin();

		// all there before connecting, from the save file
		assertEquals(beforeRestart, bob.recordsAsText());
		assertEquals(GroupSync.Status.DISCONNECTED, statusOnceLoggedIn(bob));
		assertEquals(1, bob.bounties().list().size());
		assertNotNull(bob.goals().get(ids[0]));
		assertEquals(1, logOf(bob, alice).getEntries().size());

		String newGoal = alice.goals().create(GoalData.Type.TOTAL_LEVEL, null, 500, Collections.emptyList());
		alice.bounties().delete(bountyId(alice, COAL));
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		SimulatedParty.assertSameRecords(alice, bob);
		assertNotNull(bob.goals().get(newGoal));
		assertTrue(bob.bounties().list().isEmpty());
	}

	private GroupSync.Status statusOnceLoggedIn(SimulatedClient who)
	{
		party.run(5 * SECOND);
		return who.status();
	}

	@Test
	public void aRestartedClientSharesWhatItChangedBeforeRestarting()
	{
		String[] ids = shareSomeOfEverything(bob, alice);
		party.run(5 * SECOND);

		bob.restartPlugin();
		party.run(5 * SECOND);
		connect(alice, bob);

		// nothing was still waiting to be sent, but the summaries show what Alice lacks
		SimulatedParty.assertSameRecords(alice, bob);
		assertNotNull(alice.goals().get(ids[0]));
		assertEquals(1, alice.bounties().list().size());
		assertEquals(1, logOf(alice, bob).getEntries().size());
	}

	@Test
	public void somethingDeletedStaysDeletedThroughARestart()
	{
		connect(alice, bob, carol);
		String goal = setGoal(alice);
		party.run(3 * SECOND);
		// Carol goes away holding a copy
		carol.disconnect();

		alice.goals().delete(goal);
		party.run(5 * SECOND);
		bob.restartPlugin();
		assertNull(bob.goals().get(goal));
		alice.restartPlugin();
		party.run(5 * SECOND);

		// her old copy doesn't bring it back
		bob.connect();
		carol.connect();
		party.run(CATCH_UP);
		assertNull(bob.goals().get(goal));
		assertNull(carol.goals().get(goal));
		SimulatedParty.assertSameRecords(bob, carol);
	}

	@Test
	public void aMemberAwayLongerThanDeletesAreRememberedBringsThemBack()
	{
		connect(alice, carol);
		String goal = setGoal(alice);
		party.run(3 * SECOND);
		carol.disconnect();
		alice.goals().delete(goal);
		party.run(5 * SECOND);
		alice.disconnect();

		party.skip(TimeUnit.DAYS.toMillis(61));
		alice.restartPlugin();
		party.run(5 * SECOND);
		assertTrue("forgotten by now", alice.recordsAsText().stream().noneMatch(r -> r.startsWith(Keys.shared(GoalService.TYPE, goal))));

		connect(alice, carol);
		assertNotNull(alice.goals().get(goal));
	}

	@Test
	public void closingRuneLiteSavesWhatTheRegularSaveHadntGotTo()
	{
		connect(alice, bob);
		String goal = setGoal(alice);
		party.run(MINUTE);
		String lateGoal = alice.goals().create(GoalData.Type.TOTAL_LEVEL, null, 500, Collections.emptyList());
		party.run(2 * SECOND);

		alice.closeRuneLite();
		party.run(5 * SECOND);
		alice.openRuneLite();
		party.run(5 * SECOND);

		assertNotNull("saved by then", alice.goals().get(goal));
		assertNotNull("saved on the way out", alice.goals().get(lateGoal));

		alice.connect();
		party.run(CATCH_UP);
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void aLostSaveFileRefillsFromTheGroup()
	{
		connect(alice, bob);
		String[] ids = shareSomeOfEverything(bob, alice);
		party.run(MINUTE);

		bob.closeRuneLite();
		bob.loseSavedCopy();
		bob.openRuneLite();
		party.run(5 * SECOND);
		assertTrue(bob.bounties().list().isEmpty());
		connect(bob);

		assertNotNull(bob.goals().get(ids[0]));
		assertEquals(1, logOf(bob, bob).getEntries().size());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void savingGroupStorageOnAFreshInstallBeforeCatchingUpReplacesTheLog()
	{
		connect(alice, bob);
		saveStorage(bob, COAL, 20);
		saveStorage(bob, COAL, -3);
		party.run(MINUTE);

		bob.closeRuneLite();
		bob.loseSavedCopy();
		bob.openRuneLite();
		party.run(5 * SECOND);
		saveStorage(bob, SHARK, 1);
		party.run(5 * SECOND);
		connect(bob);

		// Bob's record is newer, and it's the only one anyone keeps
		assertEquals(1, logOf(alice, bob).getEntries().size());
		assertEquals(SHARK, logOf(alice, bob).getEntries().get(0).getItem());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	// a peer running the version on the Plugin Hub, which knows none of the newer sections or return marks

	@Test
	public void aMemberOnTheReleasedVersionPassesOnWhatItCantRead()
	{
		// SyncEngine, GroupStore, OutboundQueue, Keys, SyncRecord and WireCodec are unchanged from that version,
		// and nothing in them looks at what a section means, so Bob stands in for it just by what he doesn't do
		connect(alice, bob);
		marks(alice).mark(SHARK);
		saveStorage(alice, COAL, -5);
		drop(alice, "Draconic visage");
		pin(alice, 3222, 3218);
		party.run(5 * SECOND);
		alice.disconnect();

		bob.restartPlugin();
		party.run(5 * SECOND);
		connect(bob, carol);

		SimulatedParty.assertSameRecords(bob, carol);
		assertEquals(Collections.singleton(SHARK), marks(carol).marked().keySet());
		assertEquals(1, logOf(carol, alice).getEntries().size());
		assertEquals("Draconic visage", feedOf(carol, alice).getEntries().get(0).getName());
		assertEquals(3222, carol.groupIndex().pins().get(0).getPoint().getX());
	}

	@Test
	public void aProfileFromTheReleasedVersionHasNoColourAndStillCounts()
	{
		connect(alice, bob);
		JsonObject profile = new JsonObject();
		profile.addProperty("name", "Bob");
		profile.addProperty("world", 301);
		profile.addProperty("groupName", party.groupName());
		bob.engine().publish(Section.PROFILE.keyFor(bob.id()), bob.id(), profile);
		party.run(5 * SECOND);

		assertEquals("Bob", alice.groupIndex().nameOf(bob.id()));
		assertTrue(alice.groupIndex().isMember(bob.id()));
		assertEquals(GroupIndex.DEFAULT_COLOR, alice.groupIndex().color("Bob"));
	}

	// large amounts of data, through real batching

	@Test
	public void aFullLogAndHundredsOfRecordsArriveInBatchesWithinTheLimits()
	{
		for (int i = 0; i < StorageLogData.MAX_ENTRIES + 20; i++)
		{
			saveStorage(alice, 100 + i, i % 2 == 0 ? 1_000_000 + i : -1_000_000 - i);
		}
		for (int i = 0; i < FeedData.MAX_ENTRIES; i++)
		{
			drop(alice, "A drop with quite a long name " + i);
		}
		ItemsData bank = new ItemsData();
		int[] slots = new int[2 * 1_000];
		for (int i = 0; i < 1_000; i++)
		{
			slots[2 * i] = 10_000 + i;
			slots[2 * i + 1] = 1_000_000 + i * 7;
		}
		bank.setItems(slots);
		alice.collector().share(Section.BANK, bank);
		pin(alice, 3222, 3218);
		party.run(5 * SECOND);

		String goal = setGoal(alice);
		for (int i = 0; i < 300; i++)
		{
			alice.goals().create(GoalData.Type.TOTAL_LEVEL, null, 500 + i, Collections.emptyList());
			setBounty(alice, goal, 1_000 + i, bob);
			alice.requests().create("Requested item " + i, 2, "Some words about why " + i);
			marks(alice).mark(5_000 + i);
		}
		// some deleted, which are the smallest records there are
		for (GoalService.Goal each : alice.goals().list().subList(1, 100))
		{
			alice.goals().delete(each.getId());
		}
		int records = alice.store().all().size();

		party.forgetSent();
		connect(alice, bob);
		party.run(30 * SECOND);

		SimulatedParty.assertSameRecords(alice, bob);
		assertTrue(bob.store().all().size() >= records);
		assertEquals(StorageLogData.MAX_ENTRIES, logOf(bob, alice).getEntries().size());
		assertEquals(FeedData.MAX_ENTRIES, feedOf(bob, alice).getEntries().size());
		assertEquals(1_000, bob.view().get(alice.id(), Section.BANK, ItemsData.class).getItems().length / 2);
		assertEquals(300, bob.bounties().list().size());

		// the batches keep to the limits, and the receiving side's are never reached
		List<List<SyncRecord>> batches = party.recordBatches();
		assertTrue("should take several batches, not " + batches.size(), batches.size() > 1);
		for (List<SyncRecord> batch : batches)
		{
			assertTrue(batch.size() <= 2_000);
		}
		for (String json : party.syncPayloads("R"))
		{
			assertTrue("A batch should stay near the 48,000 limit, not " + json.length(), json.length() <= 48_000 + 1_000);
		}
		for (SimulatedParty.Sent message : party.sent())
		{
			assertTrue("Nothing sent should be big, not " + message.bytes, message.bytes < 64 * 1024);
		}
	}

	@Test
	public void aRecordLargerThanABatchGoesOnItsOwn()
	{
		connect(alice, bob);
		JsonObject big = new JsonObject();
		// doesn't compress away, so it's big on the wire as well
		Random random = new Random(1);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 100_000; i++)
		{
			text.append((char) ('a' + random.nextInt(26)));
		}
		big.addProperty("text", text.toString());
		String key = Keys.member(alice.id(), "clog");

		party.forgetSent();
		setGoal(alice);
		alice.engine().publish(key, alice.id(), big);
		setGoal(alice);
		party.run(5 * SECOND);

		assertEquals(big, bob.store().get(key).getData());
		assertEquals(2, bob.goals().list().size());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void aRecordPastTheSizeLimitIsRefusedAndNothingElseIsHeldUp()
	{
		connect(alice, bob);
		JsonObject huge = new JsonObject();
		// it compresses to nothing, so it gets through the party, and is only refused on being unpacked
		char[] text = new char[5 << 20];
		Arrays.fill(text, 'a');
		huge.addProperty("text", new String(text));
		String key = Keys.member(alice.id(), "clog");

		alice.engine().publish(key, alice.id(), huge);
		party.run(3 * SECOND);
		String goal = setGoal(alice);
		party.run(3 * SECOND);

		assertNull(bob.store().get(key));
		assertNotNull(bob.goals().get(goal));
	}

	@Test
	public void aLogNeverGrowsPastItsLimitOnAnyClient()
	{
		connect(alice, bob);
		for (int i = 0; i < StorageLogData.MAX_ENTRIES; i++)
		{
			saveStorage(alice, 100 + i, 1);
		}
		party.run(5 * SECOND);
		assertEquals(StorageLogData.MAX_ENTRIES, logOf(bob, alice).getEntries().size());

		saveStorage(alice, COAL, 5);
		party.run(5 * SECOND);

		List<StorageLogData.Entry> entries = logOf(bob, alice).getEntries();
		assertEquals(StorageLogData.MAX_ENTRIES, entries.size());
		assertEquals("the oldest went", 101, entries.get(0).getItem());
		assertEquals(COAL, entries.get(entries.size() - 1).getItem());
	}

	// everything at once

	@Test
	public void anyMixOfPlayingEndsWithEveryoneHoldingTheSame()
	{
		for (long seed = 1; seed <= 25; seed++)
		{
			playAtRandom(seed);
		}
	}

	private static void playAtRandom(long seed)
	{
		SimulatedParty party = new SimulatedParty();
		List<SimulatedClient> members = Arrays.asList(party.member("Alice"), party.member("Bob"), party.member("Carol"));
		for (SimulatedClient member : members)
		{
			member.onGroupOpened(client -> client.get(ReturnTags.class).setEngine(client.engine()));
		}
		party.setLatency(seed % 2 == 0 ? 0 : 400);
		party.setStayListed(seed % 3 == 0 ? 20 * SECOND : 0);
		party.run(5 * SECOND);

		Random random = new Random(seed);
		boolean[] closed = new boolean[members.size()];
		for (int turn = 0; turn < 150; turn++)
		{
			int index = random.nextInt(members.size());
			SimulatedClient member = members.get(index);
			if (closed[index])
			{
				member.openRuneLite();
				closed[index] = false;
			}
			else
			{
				act(member, members.get(random.nextInt(members.size())), random, closed, index);
			}
			party.run(100 + random.nextInt(15_000));
		}

		// everyone comes back and stays a while
		for (int i = 0; i < members.size(); i++)
		{
			if (closed[i])
			{
				members.get(i).openRuneLite();
			}
			members.get(i).logIn();
		}
		party.run(10 * SECOND);
		for (SimulatedClient member : members)
		{
			member.connect();
		}
		party.run(2 * MINUTE);

		party.assertNoErrors();
		for (SimulatedClient member : members)
		{
			assertEquals(member.name() + " should be connected", GroupSync.Status.CONNECTED, member.status());
		}
		SimulatedParty.assertSameRecords(members.get(0), members.get(1), members.get(2));
		assertTrue("there should have been plenty to agree on", members.get(0).store().all().size() > 20);
	}

	private static void act(SimulatedClient member, SimulatedClient other, Random random, boolean[] closed, int index)
	{
		List<GoalService.Goal> goals = member.goals().list();
		List<BountyService.Bounty> bounties = member.bounties().list();
		List<RequestService.Request> requests = member.requests().list();
		switch (random.nextInt(14))
		{
			case 0:
				member.connect();
				break;
			case 1:
				member.disconnect();
				break;
			case 2:
				member.logOut();
				break;
			case 3:
				member.logIn();
				break;
			case 4:
				if (random.nextBoolean())
				{
					member.restartPlugin();
				}
				else
				{
					member.closeRuneLite();
					closed[index] = true;
				}
				break;
			case 5:
				setGoal(member);
				break;
			case 6:
				if (!goals.isEmpty())
				{
					member.goals().delete(goals.get(random.nextInt(goals.size())).getId());
				}
				break;
			case 7:
				if (!goals.isEmpty())
				{
					setBounty(member, goals.get(random.nextInt(goals.size())).getId(), 1_000 + random.nextInt(50), other);
				}
				break;
			case 8:
				if (!bounties.isEmpty())
				{
					member.bounties().delete(bounties.get(random.nextInt(bounties.size())).getId());
				}
				break;
			case 9:
				member.requests().create("Item " + random.nextInt(50), 1 + random.nextInt(3), null);
				break;
			case 10:
				if (!requests.isEmpty())
				{
					String id = requests.get(random.nextInt(requests.size())).getId();
					if (random.nextBoolean())
					{
						member.requests().claim(id);
					}
					else
					{
						member.requests().delete(id);
					}
				}
				break;
			case 11:
				if (random.nextBoolean())
				{
					member.get(ReturnTags.class).mark(COAL + random.nextInt(5));
				}
				else
				{
					member.get(ReturnTags.class).unmark(COAL + random.nextInt(5));
				}
				break;
			case 12:
				if (member.collector().getMemberId() != null)
				{
					StorageLogData log = member.collector().shared(Section.STORAGE_LOG, StorageLogData.class);
					log = log == null ? new StorageLogData() : log;
					log.add(COAL, 1 + random.nextInt(9), 1_790_000_000_000L);
					member.collector().share(Section.STORAGE_LOG, log);
				}
				break;
			default:
				if (member.collector().getMemberId() != null)
				{
					member.collector().share(Section.PIN, new PinData(random.nextBoolean(), 3000 + random.nextInt(99), 3000, 0, 0, 1));
				}
		}
	}
}
