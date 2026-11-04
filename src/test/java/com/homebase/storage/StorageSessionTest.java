package com.homebase.storage;

import static com.homebase.storage.StorageHistoryTest.items;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class StorageSessionTest
{
	private static final int SHARK = 385;
	private static final int COAL = 453;

	private final StorageSession session = new StorageSession();

	private static List<StorageHistory.Change> just(int item, int amount)
	{
		return Collections.singletonList(new StorageHistory.Change(item, amount));
	}

	private List<StorageHistory.Change> saveAndLeave()
	{
		return session.tick(false, true);
	}

	private void open(int... idThenAmount)
	{
		session.built(items(idThenAmount), true);
		assertTrue(session.tick(true, false).isEmpty());
	}

	@Test
	public void openingAndClosingAddsNothing()
	{
		open(SHARK, 5);
		assertTrue(session.tick(false, false).isEmpty());
	}

	@Test
	public void savingWithoutChangingAnythingAddsNothing()
	{
		open(SHARK, 5);
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void movingItemsAroundAddsNothing()
	{
		open(SHARK, 5, COAL, 10);
		session.changed(items(COAL, 10, SHARK, 5), true);
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void putInAndTakenBackAddsNothing()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 8), true);
		session.changed(items(SHARK, 5), true);
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void nothingCountsUntilItsSaved()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		assertTrue(session.tick(true, false).isEmpty());
		assertEquals(just(SHARK, -3), session.tick(true, true));
	}

	@Test
	public void everyChangeInASaveIsCounted()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		session.changed(items(SHARK, 2, COAL, 30), true);
		assertEquals(Arrays.asList(new StorageHistory.Change(SHARK, -3), new StorageHistory.Change(COAL, 30)), saveAndLeave());
	}

	@Test
	public void theSaveMessageShowingForSeveralTicksCountsOnce()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		assertEquals(just(SHARK, -3), session.tick(true, true));
		assertTrue(session.tick(true, true).isEmpty());
		assertTrue(session.tick(false, true).isEmpty());
	}

	@Test
	public void aSecondSaveOnlyCountsWhatChangedSinceTheFirst()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		assertEquals(just(SHARK, -3), session.tick(true, true));
		session.changed(items(SHARK, 4), true);
		assertEquals(just(SHARK, 2), session.tick(true, true));
	}

	@Test
	public void closingWithoutSavingLosesTheChanges()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		for (int i = 0; i <= StorageSession.CLOSE_GRACE_TICKS; i++)
		{
			assertTrue(session.tick(false, false).isEmpty());
		}
		// someone else then takes the rest, so this is where the next look starts from
		open(COAL, 1);
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void theSaveCanShowUpJustAfterTheInterfaceCloses()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		for (int i = 0; i < StorageSession.CLOSE_GRACE_TICKS; i++)
		{
			assertTrue(session.tick(false, false).isEmpty());
		}
		assertEquals(just(SHARK, -3), saveAndLeave());
	}

	@Test
	public void aSaveLongAfterClosingIsSomethingElse()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		for (int i = 0; i <= StorageSession.CLOSE_GRACE_TICKS; i++)
		{
			session.tick(false, false);
		}
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void aChangeOnTheSameTickAsLeavingStillCounts()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 9), false);
		assertEquals(just(SHARK, 4), saveAndLeave());
	}

	@Test
	public void anEmptyContainerOnItsOwnIsntBelieved()
	{
		open(SHARK, 5);
		session.changed(items(), true);
		assertTrue(saveAndLeave().isEmpty());

		open(SHARK, 5);
		session.changed(items(), false);
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void takingOutTheLastItemCountsOnceTheInterfaceShowsIt()
	{
		open(SHARK, 5);
		session.changed(items(), true);
		session.built(items(), true);
		assertEquals(just(SHARK, -5), saveAndLeave());
	}

	@Test
	public void theInterfaceBeingRedrawnWhileItClosesIsIgnored()
	{
		open(SHARK, 5);
		session.built(items(), false);
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void puttingTheFirstThingsInEmptyStorageCounts()
	{
		open();
		session.changed(items(COAL, 30), true);
		assertEquals(just(COAL, 30), saveAndLeave());
	}

	@Test
	public void theStartingPointIsFoundWhicheverWayTheGameReportsIt()
	{
		// the container's report first, with no drawing of the interface yet
		session.changed(items(SHARK, 5), true);
		assertTrue(session.tick(true, false).isEmpty());
		session.changed(items(SHARK, 2), true);
		assertEquals(just(SHARK, -3), session.tick(true, true));

		// drawn before it counts as open
		StorageSession other = new StorageSession();
		other.built(items(COAL, 7), false);
		assertTrue(other.tick(true, false).isEmpty());
		other.changed(items(COAL, 9), true);
		assertEquals(just(COAL, 2), other.tick(true, true));
	}

	@Test
	public void aFreshCopyArrivingAsItOpensIsNotOurChange()
	{
		StorageSession session = new StorageSession();
		// drawn from the copy left from last time, then the game's fresh one with another member's save
		session.built(items(SHARK, 5), true);
		session.changed(items(SHARK, 2), true);
		assertTrue(session.tick(true, false).isEmpty());

		// what we then do ourselves still counts
		session.changed(items(SHARK, 1), true);
		assertEquals(just(SHARK, -1), session.tick(true, true));
	}

	@Test
	public void changesWhileItWasNeverOpenAreIgnored()
	{
		session.changed(items(SHARK, 5), false);
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void aSaveWithNothingFollowedAddsNothing()
	{
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void anImpossibleJumpIsDroppedAndTheNextSaveStartsFromIt()
	{
		int[] everything = new int[2 * (StorageHistory.MAX_KINDS_PER_SAVE + 1)];
		for (int i = 0; i < StorageHistory.MAX_KINDS_PER_SAVE + 1; i++)
		{
			everything[2 * i] = i + 1;
			everything[2 * i + 1] = 1;
		}
		open();
		session.changed(items(everything), true);
		assertTrue(session.tick(true, true).isEmpty());

		everything[1] = 2;
		session.changed(items(everything), true);
		assertEquals(just(1, 1), session.tick(true, true));
	}

	@Test
	public void resetForgetsEverything()
	{
		open(SHARK, 5);
		session.changed(items(SHARK, 2), true);
		session.reset();
		assertTrue(saveAndLeave().isEmpty());
	}

	@Test
	public void theSavingMessageIsToldFromTheLoadingOne()
	{
		assertTrue(StorageWatcher.isSavingText("Saving..."));
		assertTrue(StorageWatcher.isSavingText("<col=ffffff>saving...</col>"));
		assertFalse(StorageWatcher.isSavingText("Loading..."));
		assertFalse(StorageWatcher.isSavingText(null));
	}
}
