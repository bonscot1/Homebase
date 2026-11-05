package com.homebase;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.homebase.bounties.BountyService;
import com.homebase.data.BountyData;
import com.homebase.data.GoalData;
import com.homebase.data.Section;
import com.homebase.goals.GoalService;
import com.homebase.party.GroupSync;
import com.homebase.party.HomebaseSyncMessage;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncRecord;
import com.homebase.sync.WireCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.runelite.client.party.PartyService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Every way a bounty set by one member could fail to reach or show for another, between clients running the
 * real sync. Whether a bounty reached someone is read from what they hold; whether it shows from what the
 * panel and padlocks read. Only uses what the version on the Plugin Hub also has, so these can be run against
 * that version too.
 */
public class BountyDeliveryTest
{
	private static final long SECOND = SimulatedParty.SECOND;
	private static final long MINUTE = SimulatedParty.MINUTE;
	private static final long DAY = TimeUnit.DAYS.toMillis(1);

	/**
	 * Long enough for a joining member to be told who's there and catch up.
	 */
	private static final long CATCH_UP = 20 * SECOND;

	private static final int COAL = 453;
	private static final int LOBSTER = 379;

	private final SimulatedParty party = new SimulatedParty();
	private final SimulatedClient alice = party.member("Alice");
	private final SimulatedClient bob = party.member("Bob");
	private final SimulatedClient carol = party.member("Carol");

	@After
	public void nothingThrew()
	{
		party.assertNoErrors();
	}

	private void connect(SimulatedClient... members)
	{
		// the game takes a few ticks to say which group an account is in
		party.run(5 * SECOND);
		for (SimulatedClient member : members)
		{
			member.connect();
		}
		party.run(CATCH_UP);
	}

	private String goalFrom(SimulatedClient who)
	{
		return who.goals().create(GoalData.Type.SKILL, "Firemaking", 50, Collections.emptyList());
	}

	private void bountyFrom(SimulatedClient who, String goal, int item, SimulatedClient locked)
	{
		who.bounties().create(item, "Item " + item, 5, goal, Collections.singletonList(locked.id()));
	}

	/**
	 * @return who set each bounty the member holds
	 */
	private static List<String> settersOfBountiesHeldBy(SimulatedClient who)
	{
		List<String> out = new ArrayList<>();
		for (BountyService.Bounty bounty : who.bounties().list())
		{
			out.add(bounty.getData().getCreatedBy());
		}
		return out;
	}

	private void aliceSetsABountyOnCoal()
	{
		party.run(5 * SECOND);
		bountyFrom(alice, goalFrom(alice), COAL, bob);
	}

	@Test
	public void aBountyReachesAMemberWhoIsConnected()
	{
		connect(alice, bob);
		assertEquals(GroupSync.Status.CONNECTED, alice.status());
		assertEquals(GroupSync.Status.CONNECTED, bob.status());

		aliceSetsABountyOnCoal();
		party.run(3 * SECOND);

		assertEquals(Collections.singletonList(alice.id()), settersOfBountiesHeldBy(bob));
		assertTrue("it holds the coal back from Bob", bob.bounties().lockFor(COAL).isLocked());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void aBountySetBeforeAMemberConnectsArrivesWhenTheyDo()
	{
		connect(alice);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);

		bob.connect();
		party.run(CATCH_UP);

		assertEquals(Collections.singletonList(alice.id()), settersOfBountiesHeldBy(bob));
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void aBountySetWhileNotConnectedGoesOutOnConnecting()
	{
		aliceSetsABountyOnCoal();
		connect(alice, bob);
		assertEquals(Collections.singletonList(alice.id()), settersOfBountiesHeldBy(bob));
	}

	@Test
	public void nothingArrivesUntilTheMemberPressesConnect()
	{
		connect(alice);
		aliceSetsABountyOnCoal();
		party.run(MINUTE);

		// logged in and playing, with Alice's client sending, but a member only joins the party by pressing Connect
		assertEquals(GroupSync.Status.DISCONNECTED, bob.status());
		assertTrue(bob.bounties().list().isEmpty());

		bob.connect();
		party.run(CATCH_UP);
		assertEquals(1, bob.bounties().list().size());
	}

	@Test
	public void membersWhoAreNeverOnlineTogetherOnlyMeetThroughSomeoneElse()
	{
		connect(alice);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		alice.disconnect();

		// Bob plays later, with nobody there to hand it to him
		bob.connect();
		party.run(MINUTE);
		assertTrue(bob.bounties().list().isEmpty());
		bob.disconnect();

		// Carol overlaps with Alice, then with Bob
		alice.connect();
		carol.connect();
		party.run(CATCH_UP);
		alice.disconnect();
		bob.connect();
		party.run(CATCH_UP);

		assertEquals(Collections.singletonList(alice.id()), settersOfBountiesHeldBy(bob));
	}

	@Test
	public void leavingAfterHalfAnHourAtTheLoginScreenStopsThemHearingAnything()
	{
		connect(alice, bob);
		bob.logOut();
		party.run(5 * MINUTE);

		// still connected through a short logout, so it reaches them at the login screen
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		assertEquals(1, bob.bounties().list().size());

		party.run(30 * MINUTE);
		assertFalse("should have left the party", bob.groupSync().isGroupParty());
		bountyFrom(alice, alice.goals().list().get(0).getId(), LOBSTER, bob);
		party.run(10 * SECOND);
		assertEquals(1, bob.bounties().list().size());

		// logging back in isn't enough, Connect has to be pressed again
		bob.logIn();
		party.run(CATCH_UP);
		assertEquals(GroupSync.Status.DISCONNECTED, bob.status());
		assertEquals(1, bob.bounties().list().size());

		bob.connect();
		party.run(CATCH_UP);
		assertEquals(2, bob.bounties().list().size());
	}

	@Test
	public void anotherPartyPausesItUntilSwitchingBack()
	{
		connect(alice);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);

		bob.joinPartyDirectly("our-raids-party");
		party.run(CATCH_UP);
		assertEquals(GroupSync.Status.OTHER_PARTY, bob.status());
		assertTrue(bob.bounties().list().isEmpty());

		bob.connect();
		party.run(CATCH_UP);
		assertEquals(1, bob.bounties().list().size());
	}

	@Test
	public void aPassphraseOnOneClientPutsThemInASeparatePartyWithTheirOwnSavedData()
	{
		// the panel offers Create group before anyone has logged in, and a passphrase beats the group's name
		bob.setPassphrase("bpfgh-tvwxz-klmnr-cdfgj");
		bob.closeRuneLite();
		bob.openRuneLite();
		connect(alice, bob);

		// both look fine: neither has anyone to be suspicious of
		assertEquals(GroupSync.Status.CONNECTED, alice.status());
		assertEquals(GroupSync.Status.CONNECTED, bob.status());

		aliceSetsABountyOnCoal();
		bountyFrom(bob, goalFrom(bob), LOBSTER, alice);
		party.run(MINUTE);

		assertEquals("Bob only ever holds his own", Collections.singletonList(bob.id()), settersOfBountiesHeldBy(bob));
		assertEquals("and Alice only hers", Collections.singletonList(alice.id()), settersOfBountiesHeldBy(alice));
	}

	@Test
	public void aDifferentGroupSecretPutsThemInASeparatePartyWithTheirOwnSavedData()
	{
		bob.setSecret("only-bob-set-this");
		bob.closeRuneLite();
		bob.openRuneLite();
		connect(alice, bob);
		assertEquals(GroupSync.Status.CONNECTED, bob.status());

		aliceSetsABountyOnCoal();
		bountyFrom(bob, goalFrom(bob), LOBSTER, alice);
		party.run(MINUTE);

		assertEquals(Collections.singletonList(bob.id()), settersOfBountiesHeldBy(bob));
		assertEquals(Collections.singletonList(alice.id()), settersOfBountiesHeldBy(alice));
	}

	@Test
	@Ignore("A member connected at the login screen has no name for anyone who joins later, so the newcomer pauses and never asks for what it missed")
	public void aMemberAtTheLoginScreenDoesntStopANewcomerCatchingUp()
	{
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);

		// the plugin keeps a member connected through a logout, for up to half an hour
		bob.logOut();
		party.run(MINUTE);

		carol.connect();
		party.run(CATCH_UP);

		assertEquals(GroupSync.Status.CONNECTED, carol.status());
		assertEquals(1, carol.bounties().list().size());
	}

	@Test
	@Ignore("Reconnecting after a dropped connection forgets everyone's names, so a member at the login screen pauses whoever reconnected, the same as a newcomer")
	public void aDroppedConnectionDoesntPauseAMemberWhileSomeoneIsAtTheLoginScreen()
	{
		connect(alice, bob);
		bob.logOut();
		party.run(MINUTE);

		alice.loseConnection();
		party.run(MINUTE);

		assertEquals(GroupSync.Status.CONNECTED, alice.status());
	}

	@Test
	public void aNewcomerHearsNewBountiesFromMembersItKnowsWhileSomeoneIsAtTheLoginScreen()
	{
		connect(alice, bob);
		bob.logOut();
		party.run(MINUTE);
		carol.connect();
		party.run(CATCH_UP);

		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);

		// what's sent live still arrives, so members look up to date while nothing older does
		assertEquals(Collections.singletonList(alice.id()), settersOfBountiesHeldBy(carol));
	}

	@Test
	public void aNewcomerCatchesUpOnceTheMemberAtTheLoginScreenLogsBackIn()
	{
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		bob.logOut();
		party.run(MINUTE);
		carol.connect();
		party.run(CATCH_UP);

		bob.logIn();
		party.run(CATCH_UP);

		assertEquals(GroupSync.Status.CONNECTED, carol.status());
		assertEquals(1, carol.bounties().list().size());
		SimulatedParty.assertSameRecords(alice, bob, carol);
	}

	@Test
	public void aNewcomerCatchesUpOnceTheMemberAtTheLoginScreenHasLeft()
	{
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		bob.logOut();
		party.run(MINUTE);
		carol.connect();
		party.run(CATCH_UP);

		// half an hour at the login screen in all
		party.run(30 * MINUTE);

		assertFalse(bob.groupSync().isGroupParty());
		assertEquals(GroupSync.Status.CONNECTED, carol.status());
		assertEquals(1, carol.bounties().list().size());
	}

	@Test
	public void aNewcomerWaitsForTheServerToDropTheConnectionOfAMemberWhoLeftFromTheLoginScreen()
	{
		party.setStayListed(3 * MINUTE);
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		bob.logOut();
		party.run(MINUTE);
		carol.connect();
		party.run(CATCH_UP);

		// it left when its half hour was up, but Carol has heard from it so it can't be taken for a closed one
		party.run(30 * MINUTE);
		assertFalse(bob.groupSync().isGroupParty());
		party.run(4 * MINUTE);

		assertEquals(GroupSync.Status.CONNECTED, carol.status());
		assertEquals(1, carol.bounties().list().size());
	}

	@Test
	public void aPassphraseGroupIsntHeldUpByAMemberAtTheLoginScreen()
	{
		for (SimulatedClient member : Arrays.asList(alice, bob, carol))
		{
			member.setPassphrase("bpfgh-tvwxz-klmnr-cdfgj");
			member.closeRuneLite();
			member.openRuneLite();
		}
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		bob.logOut();
		party.run(MINUTE);

		carol.connect();
		party.run(CATCH_UP);

		// only a Group Ironman group checks who's in the party
		assertEquals(GroupSync.Status.CONNECTED, carol.status());
		assertEquals(1, carol.bounties().list().size());
	}

	@Test
	public void aConnectionThatHasClosedOnlyHoldsANewcomerUpForAFewSeconds()
	{
		party.setStayListed(3 * MINUTE);
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		bob.disconnect();
		alice.disconnect();

		// both old connections are still listed, and nobody can say whose they were
		alice.connect();
		party.run(2 * SECOND);
		assertFalse(alice.groupSync().isShareSafe());
		party.run(15 * SECOND);
		assertTrue(alice.groupSync().isShareSafe());

		carol.connect();
		party.run(CATCH_UP);
		assertEquals(1, carol.bounties().list().size());
	}

	@Test
	public void someoneOutsideTheGroupInThePartyStopsEveryoneUntilTheyLeave()
	{
		SimulatedClient mallory = party.outsider("Mallory");
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		bob.disconnect();

		// a group's party is named after the group, which anyone can look up
		mallory.joinPartyDirectly(HomebasePlugin.gimPassphrase(party.groupName(), null));
		party.run(5 * SECOND);
		assertEquals(GroupSync.Status.PAUSED, alice.status());

		carol.connect();
		party.run(CATCH_UP);
		assertEquals(GroupSync.Status.PAUSED, carol.status());
		assertTrue(carol.bounties().list().isEmpty());

		mallory.joinPartyDirectly(null);
		party.run(CATCH_UP);
		assertEquals(GroupSync.Status.CONNECTED, alice.status());
		assertEquals(GroupSync.Status.CONNECTED, carol.status());
		assertEquals(1, carol.bounties().list().size());
	}

	@Test
	public void anAltOnAConnectedClientStopsEveryoneUntilItsGone()
	{
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);

		// a logout doesn't leave the party, so whoever logs in next is in it
		bob.logInOnAnotherAccount("BobsAlt");
		party.run(5 * SECOND);
		assertEquals(GroupSync.Status.PAUSED, alice.status());
		assertEquals(Collections.singletonList("BobsAlt"), alice.groupSync().strangers());

		bountyFrom(alice, alice.goals().list().get(0).getId(), LOBSTER, bob);
		carol.connect();
		party.run(CATCH_UP);
		assertTrue(carol.bounties().list().isEmpty());

		bob.logInOnAnotherAccount("Bob");
		party.run(CATCH_UP);
		assertEquals(GroupSync.Status.CONNECTED, alice.status());
		assertEquals(2, carol.bounties().list().size());
	}

	@Test
	public void namesMatchTheGroupsMemberListHoweverTheGameWritesThem()
	{
		// the party has the name as the game wrote it, and the member list spells the space differently
		party.roster().add("Bonscott Kid");
		SimulatedClient kid = party.member("Bonscott Kid");
		party.roster().remove("Bonscott Kid");
		SimulatedClient dashed = party.member("Mun-dell");
		party.roster().remove("Mun-dell");
		party.roster().add("mun dell");

		connect(alice, kid, dashed);
		assertEquals(GroupSync.Status.CONNECTED, alice.status());

		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		assertEquals(1, kid.bounties().list().size());
		assertEquals(1, dashed.bounties().list().size());
	}

	@Test
	public void aClockMoreThanADayAheadIsRefusedWithoutAnyoneBeingTold()
	{
		alice.setClockAhead(2 * DAY);
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(MINUTE);

		assertEquals(GroupSync.Status.CONNECTED, alice.status());
		assertEquals(GroupSync.Status.CONNECTED, bob.status());
		assertEquals(1, alice.bounties().list().size());
		assertTrue(bob.bounties().list().isEmpty());

		// Bob's own bounties still reach Alice, so it looks one sided
		bountyFrom(bob, goalFrom(bob), LOBSTER, alice);
		party.run(10 * SECOND);
		assertEquals(2, alice.bounties().list().size());
		assertEquals(Collections.singletonList(bob.id()), settersOfBountiesHeldBy(bob));
	}

	@Test
	public void aClockMoreThanADayBehindRefusesEveryoneElsesRecords()
	{
		bob.setClockAhead(-2 * DAY);
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		bountyFrom(bob, goalFrom(bob), LOBSTER, alice);
		party.run(MINUTE);

		// everything Alice writes looks as if it's from the future, while what Bob writes just looks old
		assertEquals(Collections.singletonList(bob.id()), settersOfBountiesHeldBy(bob));
		assertEquals(2, alice.bounties().list().size());
		assertEquals("he doesn't hold her profile either", 1, bob.view().memberIds().size());
	}

	@Test
	public void aClockLessThanADayAheadIsAccepted()
	{
		alice.setClockAhead(DAY - MINUTE);
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		assertEquals(1, bob.bounties().list().size());
	}

	@Test
	public void anyoneCanDeleteABountyForEveryone()
	{
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);

		String id = bob.bounties().list().get(0).getId();
		bob.bounties().delete(id);
		party.run(5 * SECOND);

		assertTrue(alice.bounties().list().isEmpty());
		assertNull(alice.bounties().lockFor(COAL));
		// what's left says who deleted it, not who set it
		assertEquals(bob.id(), recordFor(alice, BountyService.TYPE, id).getAuthor());

		// someone who was away never sees it at all
		carol.connect();
		party.run(CATCH_UP);
		assertTrue(carol.bounties().list().isEmpty());
		assertTrue(recordFor(carol, BountyService.TYPE, id).isDeleted());
	}

	private static SyncRecord recordFor(SimulatedClient who, String type, String id)
	{
		String key = Keys.shared(type, id);
		for (SyncRecord record : who.store().all())
		{
			if (record.getKey().equals(key))
			{
				return record;
			}
		}
		return null;
	}

	@Test
	public void deletingAGoalLeavesItsBountiesHeldButOutOfSightOfTheGoalsTab()
	{
		connect(alice, bob);
		party.run(5 * SECOND);
		String goal = goalFrom(alice);
		bountyFrom(alice, goal, COAL, bob);
		party.run(5 * SECOND);
		assertTrue(bob.bounties().lockFor(COAL).isLocked());

		// anyone can do it, with one click
		bob.goals().delete(goal);
		party.run(5 * SECOND);

		// the Goals tab only draws a bounty inside the card of its goal, and there's no longer a card
		assertTrue(alice.goals().list().isEmpty());
		assertNull(alice.goals().get(goal));
		assertEquals("the bounty itself is still held", 1, alice.bounties().list().size());
		assertFalse("and no longer holds anything back", bob.bounties().isLocked(bob.bounties().list().get(0).getData(), bob.id()));
		assertFalse(bob.bounties().lockFor(COAL).isLocked());
	}

	@Test
	@Ignore("Deleting a goal leaves its bounties behind for good, with a padlock on the item and nowhere in the panel to remove them")
	public void deletingAGoalLeavesNoPadlockBehind()
	{
		connect(alice, bob);
		party.run(5 * SECOND);
		String goal = goalFrom(alice);
		bountyFrom(alice, goal, COAL, bob);
		party.run(5 * SECOND);

		bob.goals().delete(goal);
		party.run(5 * SECOND);

		assertNull(bob.bounties().lockFor(COAL));
	}

	@Test
	public void aBountyDoesNothingUntilItsGoalArrivesToo()
	{
		connect(alice);
		party.run(5 * SECOND);
		String goal = goalFrom(alice);
		bountyFrom(alice, goal, COAL, bob);
		party.run(5 * SECOND);

		SyncRecord goalRecord = recordFor(alice, GoalService.TYPE, goal);
		SyncRecord bountyRecord = alice.store().withPrefix(Keys.sharedPrefix(BountyService.TYPE)).get(0);
		SyncRecord profile = alice.store().get(Section.PROFILE.keyFor(alice.id()));

		// records of one catch up can come in any order, and in separate messages
		bob.engine().onRecords(Arrays.asList(profile, bountyRecord));
		assertEquals(1, bob.bounties().list().size());
		assertNull(bob.goals().get(goal));
		assertFalse("nothing holds the coal back for now", bob.bounties().lockFor(COAL).isLocked());

		bob.engine().onRecords(Collections.singletonList(goalRecord));
		assertTrue(bob.bounties().lockFor(COAL).isLocked());
	}

	@Test
	public void aGoalOfAKindThisVersionDoesntKnowHidesItsBounties()
	{
		connect(alice, bob);
		party.run(5 * SECOND);
		JsonObject goal = new JsonObject();
		goal.addProperty("type", "SOMETHING_NEW");
		goal.addProperty("amount", 5);
		alice.engine().publish(Keys.shared(GoalService.TYPE, "newer"), alice.id(), goal);
		bountyFrom(alice, "newer", COAL, bob);
		party.run(5 * SECOND);

		// passed on and kept, for whoever understands it
		assertNotNull(bob.store().get(Keys.shared(GoalService.TYPE, "newer")));
		assertTrue(bob.goals().list().isEmpty());
		assertEquals(1, bob.bounties().list().size());
		assertFalse(bob.bounties().lockFor(COAL).isLocked());
	}

	@Test
	public void aBountyFromSomeoneWithNoProfileIsListedButHoldsNothingBack()
	{
		connect(alice, bob);
		party.run(5 * SECOND);
		String goal = goalFrom(alice);
		party.run(5 * SECOND);

		BountyData data = new BountyData();
		data.setItemId(COAL);
		data.setItemName("Coal");
		data.setGoalId(goal);
		data.setLocked(Collections.singletonList(bob.id()));
		data.setCreatedBy("nobody-we-know");
		alice.engine().publish(Keys.shared(BountyService.TYPE, "theirs"), "nobody-we-know", alice.gson().toJsonTree(data));
		party.run(5 * SECOND);

		assertEquals(1, bob.bounties().list().size());
		assertNull("only bounties set by someone in the group hold anything back", bob.bounties().lockFor(COAL));
	}

	@Test
	public void aBountyFromAnAccountNoLongerInTheGroupHoldsNothingBack()
	{
		connect(alice, bob);
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);
		assertTrue(bob.bounties().lockFor(COAL).isLocked());

		// Alice's profile now says she's in another group
		JsonObject profile = new JsonObject();
		profile.addProperty("name", "Alice");
		profile.addProperty("world", 301);
		profile.addProperty("groupName", "SomewhereElse");
		alice.engine().publish(Section.PROFILE.keyFor(alice.id()), alice.id(), profile);
		party.run(5 * SECOND);

		assertEquals(1, bob.bounties().list().size());
		assertNull(bob.bounties().lockFor(COAL));
	}

	@Test
	public void unreadableBountiesAreIgnoredAndTheRestStillArrive()
	{
		connect(alice, bob);
		party.run(5 * SECOND);
		String goal = goalFrom(alice);
		alice.engine().publish(Keys.shared(BountyService.TYPE, "not-an-object"), alice.id(), new JsonPrimitive("nonsense"));
		JsonObject noItem = new JsonObject();
		noItem.addProperty("goalId", goal);
		alice.engine().publish(Keys.shared(BountyService.TYPE, "no-item"), alice.id(), noItem);
		bountyFrom(alice, goal, COAL, bob);
		party.run(5 * SECOND);

		assertEquals(1, bob.bounties().list().size());
		assertEquals(COAL, bob.bounties().list().get(0).getData().getItemId());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void oneRecordOfTheWrongShapeLosesTheWholeMessage()
	{
		connect(alice, bob);
		party.run(5 * SECOND);
		String good = "{\"k\":\"s:bounty:good\",\"v\":" + party.now() + ",\"a\":\"x\",\"d\":{\"itemId\":" + COAL + "}}";
		String bad = "{\"k\":\"s:bounty:bad\",\"v\":\"soon\",\"a\":\"x\",\"d\":{\"itemId\":" + COAL + "}}";
		alice.get(PartyService.class).send(new HomebaseSyncMessage(1, "R", WireCodec.pack("[" + good + "," + bad + "]")));
		party.run(5 * SECOND);

		// only a modified client could send this, and nothing else in the message survives it
		assertTrue(bob.bounties().list().isEmpty());
	}

	@Test
	public void aRecordRefusedByTheChecksDoesntLoseTheRestOfTheMessage()
	{
		connect(alice, bob);
		party.run(5 * SECOND);
		String good = "{\"k\":\"s:bounty:good\",\"v\":" + party.now() + ",\"a\":\"x\",\"d\":{\"itemId\":" + COAL + "}}";
		String noAuthor = "{\"k\":\"s:bounty:noauthor\",\"v\":" + party.now() + ",\"d\":{\"itemId\":" + COAL + "}}";
		String future = "{\"k\":\"s:bounty:future\",\"v\":" + (party.now() + 2 * DAY) + ",\"a\":\"x\",\"d\":{\"itemId\":" + COAL + "}}";
		alice.get(PartyService.class).send(new HomebaseSyncMessage(1, "R", WireCodec.pack("[" + noAuthor + "," + future + "," + good + "]")));
		party.run(5 * SECOND);

		assertEquals(1, bob.bounties().list().size());
		assertEquals("good", bob.bounties().list().get(0).getId());
	}

	@Test
	@Ignore("Closing RuneLite doesn't save, so anything from the last 30 seconds that nobody else received is gone for good")
	public void aBountySetJustBeforeClosingRuneLiteIsStillThere()
	{
		aliceSetsABountyOnCoal();
		party.run(5 * SECOND);

		alice.closeRuneLite();
		alice.openRuneLite();

		assertEquals(1, alice.bounties().list().size());
	}

	@Test
	public void aBountySavedBeforeClosingRuneLiteStillGoesOutLater()
	{
		aliceSetsABountyOnCoal();
		// long enough for the regular save
		party.run(MINUTE);
		alice.closeRuneLite();
		alice.openRuneLite();
		connect(alice, bob);

		assertEquals(1, alice.bounties().list().size());
		assertEquals(Collections.singletonList(alice.id()), settersOfBountiesHeldBy(bob));
	}

	@Test
	public void aDroppedConnectionCatchesUpOnceItReconnects()
	{
		connect(alice, bob);
		bob.loseConnection();
		party.run(SECOND);

		// sent into the gap, so Bob never gets this message
		aliceSetsABountyOnCoal();
		party.run(2 * SECOND);
		assertTrue(bob.bounties().list().isEmpty());

		// RuneLite only reconnects when there's something to send, which the next "still here" is
		party.run(MINUTE);
		assertEquals(1, bob.bounties().list().size());
		assertEquals(GroupSync.Status.CONNECTED, bob.status());
	}

	@Test
	public void aConnectionThatDroppedAtTheLoginScreenWaitsUntilTheyreBackToCatchUp()
	{
		connect(alice, bob);
		bob.logOut();
		party.run(5 * SECOND);
		bob.loseConnection();
		party.run(SECOND);

		aliceSetsABountyOnCoal();
		party.run(MINUTE);
		// nothing's due to be sent at the login screen, so nothing reconnects
		assertTrue(bob.bounties().list().isEmpty());

		bob.logIn();
		party.run(MINUTE);
		assertEquals(1, bob.bounties().list().size());
		SimulatedParty.assertSameRecords(alice, bob);
	}

	@Test
	public void slowConnectionsStillCatchUp()
	{
		party.setLatency(2 * SECOND);
		connect(alice);
		aliceSetsABountyOnCoal();
		party.run(10 * SECOND);

		for (SimulatedClient member : Arrays.asList(bob, carol))
		{
			member.connect();
			party.run(5 * SECOND);
		}
		party.run(MINUTE);

		assertEquals(1, bob.bounties().list().size());
		assertEquals(1, carol.bounties().list().size());
		SimulatedParty.assertSameRecords(alice, bob, carol);
	}
}
