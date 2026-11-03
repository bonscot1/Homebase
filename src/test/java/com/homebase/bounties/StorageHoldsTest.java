package com.homebase.bounties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class StorageHoldsTest
{
	@Test
	public void withdrawAmounts()
	{
		assertEquals(1, StorageHolds.amount("Withdraw-1", 8));
		assertEquals(5, StorageHolds.amount("Withdraw-5", 8));
		assertEquals(8, StorageHolds.amount("Withdraw-10", 8));
		assertEquals(3, StorageHolds.amount("Withdraw-3", 8));
		assertEquals(8, StorageHolds.amount("Withdraw-All", 8));
		assertEquals(7, StorageHolds.amount("Withdraw-All-but-1", 8));
		assertEquals(-1, StorageHolds.amount("Withdraw-X", 8));
	}

	@Test
	public void onlyWhatIsLeftOverCanBeTaken()
	{
		// 8 in storage, 5 held for someone: 3 are free for anyone
		assertEquals(3, StorageHolds.free(8, 5));
		assertTrue(StorageHolds.allowed("Withdraw-1", 8, 5));
		assertTrue(StorageHolds.allowed("Withdraw-3", 8, 5));
		assertFalse(StorageHolds.allowed("Withdraw-5", 8, 5));
		assertFalse(StorageHolds.allowed("Withdraw-10", 8, 5));
		assertFalse(StorageHolds.allowed("Withdraw-All", 8, 5));
		assertFalse(StorageHolds.allowed("Withdraw-All-but-1", 8, 5));
		// the amount typed after Withdraw-X can't be checked, so it goes while anything is held
		assertFalse(StorageHolds.allowed("Withdraw-X", 8, 5));
	}

	@Test
	public void fewerThanAskedForAreAllHeld()
	{
		assertEquals(0, StorageHolds.free(3, 5));
		assertFalse(StorageHolds.allowed("Withdraw-1", 3, 5));
	}

	@Test
	public void wholeStackHoldsEverything()
	{
		assertEquals(0, StorageHolds.free(40_000, StorageHolds.ALL));
		assertFalse(StorageHolds.allowed("Withdraw-1", 40_000, StorageHolds.ALL));
	}

	@Test
	public void nothingHeldLeavesEverything()
	{
		assertTrue(StorageHolds.allowed("Withdraw-All", 8, 0));
		assertTrue(StorageHolds.allowed("Withdraw-X", 8, 0));
	}
}
