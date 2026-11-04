package com.homebase.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.homebase.data.StorageLogData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Item;
import org.junit.Test;

public class StorageHistoryTest
{
	private static final int SHARK = 385;
	private static final int COAL = 453;
	private static final int WHIP = 4151;

	static Map<Integer, Integer> items(int... idThenAmount)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (int i = 0; i < idThenAmount.length; i += 2)
		{
			out.put(idThenAmount[i], idThenAmount[i + 1]);
		}
		return out;
	}

	private static StorageHistory.Change change(int item, int amount)
	{
		return new StorageHistory.Change(item, amount);
	}

	private static StorageHistory.MemberLog member(String name, long updatedAt, long... itemAmountAt)
	{
		StorageLogData log = new StorageLogData();
		for (int i = 0; i < itemAmountAt.length; i += 3)
		{
			log.add((int) itemAmountAt[i], (int) itemAmountAt[i + 1], itemAmountAt[i + 2]);
		}
		return new StorageHistory.MemberLog(name, log, updatedAt);
	}

	@Test
	public void noChangeWhenNothingMoved()
	{
		assertTrue(StorageHistory.changes(items(), items()).isEmpty());
		assertTrue(StorageHistory.changes(items(SHARK, 5, COAL, 10), items(COAL, 10, SHARK, 5)).isEmpty());
	}

	@Test
	public void changesAreWhatWentInAndCameOut()
	{
		assertEquals(Collections.singletonList(change(SHARK, 3)), StorageHistory.changes(items(SHARK, 5), items(SHARK, 8)));
		assertEquals(Collections.singletonList(change(SHARK, -3)), StorageHistory.changes(items(SHARK, 5), items(SHARK, 2)));
		assertEquals(Collections.singletonList(change(WHIP, 1)), StorageHistory.changes(items(), items(WHIP, 1)));
		assertEquals(Collections.singletonList(change(WHIP, -1)), StorageHistory.changes(items(WHIP, 1), items()));
		assertEquals(Arrays.asList(change(SHARK, -5), change(COAL, 20)),
			StorageHistory.changes(items(SHARK, 5, COAL, 10), items(COAL, 30)));
	}

	@Test
	public void slotsDontMatterOnlyHowManyThereAre()
	{
		Map<Integer, Integer> before = StorageHistory.counts(new Item[]{new Item(SHARK, 1), new Item(-1, 0), new Item(SHARK, 1), new Item(COAL, 9)});
		Map<Integer, Integer> after = StorageHistory.counts(new Item[]{new Item(COAL, 9), new Item(SHARK, 1), null, new Item(SHARK, 1)});
		assertEquals(items(SHARK, 2, COAL, 9), before);
		assertTrue(StorageHistory.changes(before, after).isEmpty());
	}

	@Test
	public void countingEmptySlotsAndHugeStacksIsSafe()
	{
		assertTrue(StorageHistory.counts(null).isEmpty());
		assertTrue(StorageHistory.counts(new Item[]{new Item(0, 5), new Item(SHARK, 0)}).isEmpty());
		assertEquals(items(SHARK, Integer.MAX_VALUE),
			StorageHistory.counts(new Item[]{new Item(SHARK, Integer.MAX_VALUE), new Item(SHARK, 5)}));
	}

	@Test
	public void noSaveCanMoveMoreKindsThanSomeoneCarries()
	{
		List<StorageHistory.Change> most = new ArrayList<>();
		for (int i = 1; i <= StorageHistory.MAX_KINDS_PER_SAVE; i++)
		{
			most.add(change(i, -1));
		}
		assertTrue(StorageHistory.possible(most));
		// the same again in the other direction is a swap, which can be done
		for (int i = 1; i <= StorageHistory.MAX_KINDS_PER_SAVE; i++)
		{
			most.add(change(1000 + i, 1));
		}
		assertTrue(StorageHistory.possible(most));
		most.add(change(5000, -1));
		assertFalse(StorageHistory.possible(most));
	}

	@Test
	public void linesFromEveryMemberAreNewestFirst()
	{
		List<StorageHistory.Line> lines = StorageHistory.lines(Arrays.asList(
			member("Mundell", 400, SHARK, -5, 100, COAL, 20, 300),
			member("Fred", 400, WHIP, 1, 200)));
		assertEquals(Arrays.asList(
			new StorageHistory.Line("Mundell", COAL, 20, 300),
			new StorageHistory.Line("Fred", WHIP, 1, 200),
			new StorageHistory.Line("Mundell", SHARK, -5, 100)), lines);
	}

	@Test
	public void anEntryCantClaimToBeNewerThanTheLogItCameIn()
	{
		List<StorageHistory.MemberLog> logs = Collections.singletonList(member("Mundell", 500, SHARK, -5, Long.MAX_VALUE));
		assertEquals(500, StorageHistory.lines(logs).get(0).getAt());
		assertEquals(500, owedAllMarked(logs).get(0).getAt());
	}

	/**
	 * What's owed if every test item has been marked since before the logs began.
	 */
	private static List<StorageHistory.Owed> owedAllMarked(List<StorageHistory.MemberLog> logs)
	{
		Map<Integer, Long> marked = new HashMap<>();
		marked.put(SHARK, 0L);
		marked.put(COAL, 0L);
		marked.put(WHIP, 0L);
		return StorageHistory.owed(logs, marked);
	}

	@Test
	public void takingSomethingUnmarkedOwesNothing()
	{
		assertTrue(StorageHistory.owed(Collections.singletonList(member("Mundell", 400, SHARK, -5, 100)),
			Collections.emptyMap()).isEmpty());
	}

	@Test
	public void onlyWhatsTakenAfterMarkingIsOwed()
	{
		Map<Integer, Long> marked = Collections.singletonMap(SHARK, 150L);
		assertEquals(Collections.singletonList(new StorageHistory.Owed("Mundell", SHARK, 2, 200)),
			StorageHistory.owed(Collections.singletonList(member("Mundell", 400, SHARK, -5, 100, SHARK, -2, 200)), marked));
	}

	@Test
	public void takenOutAndNotReturnedIsOwed()
	{
		assertEquals(Collections.singletonList(new StorageHistory.Owed("Mundell", SHARK, 5, 100)),
			owedAllMarked(Collections.singletonList(member("Mundell", 400, SHARK, -5, 100))));
	}

	@Test
	public void putBackPartlyLeavesTheRestOwed()
	{
		assertEquals(Collections.singletonList(new StorageHistory.Owed("Mundell", SHARK, 2, 100)),
			owedAllMarked(Collections.singletonList(member("Mundell", 400, SHARK, -5, 100, SHARK, 3, 200))));
	}

	@Test
	public void putBackInFullIsNoLongerOwed()
	{
		assertTrue(owedAllMarked(Collections.singletonList(member("Mundell", 400, SHARK, -5, 100, SHARK, 5, 200))).isEmpty());
		assertTrue(owedAllMarked(Collections.singletonList(member("Mundell", 400, SHARK, -5, 100, SHARK, 9, 200))).isEmpty());
	}

	@Test
	public void takingBackWhatYouPutInIsntOwing()
	{
		assertTrue(owedAllMarked(Collections.singletonList(member("Mundell", 400, WHIP, 1, 100, WHIP, -1, 200))).isEmpty());
		assertTrue(owedAllMarked(Collections.singletonList(member("Mundell", 400, SHARK, 100, 100, SHARK, -3, 200))).isEmpty());
	}

	@Test
	public void takingOutMoreThanYouPutInIsOwing()
	{
		assertEquals(Collections.singletonList(new StorageHistory.Owed("Mundell", SHARK, 3, 200)),
			owedAllMarked(Collections.singletonList(member("Mundell", 400, SHARK, 5, 100, SHARK, -8, 200))));
	}

	@Test
	public void onlyWhoeverTookItCanReturnIt()
	{
		assertEquals(Collections.singletonList(new StorageHistory.Owed("Mundell", SHARK, 5, 100)),
			owedAllMarked(Arrays.asList(
				member("Mundell", 400, SHARK, -5, 100),
				member("Fred", 400, SHARK, 5, 200))));
	}

	@Test
	public void owedSaysWhenTheyLastTookSomeHoweverTheLogIsOrdered()
	{
		assertEquals(Collections.singletonList(new StorageHistory.Owed("Mundell", SHARK, 5, 300)),
			owedAllMarked(Collections.singletonList(member("Mundell", 400, SHARK, -3, 300, SHARK, -2, 100))));
	}

	@Test
	public void mostRecentlyTakenIsFirstAndAmountsDontOverflow()
	{
		List<StorageHistory.Owed> owed = owedAllMarked(Arrays.asList(
			member("Mundell", 400, SHARK, -1, 100),
			member("Fred", 400, COAL, -Integer.MAX_VALUE, 200, COAL, -Integer.MAX_VALUE, 300)));
		assertEquals(Arrays.asList(
			new StorageHistory.Owed("Fred", COAL, 2L * Integer.MAX_VALUE, 300),
			new StorageHistory.Owed("Mundell", SHARK, 1, 100)), owed);
	}
}
