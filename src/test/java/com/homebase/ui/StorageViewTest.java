package com.homebase.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class StorageViewTest
{
	@Test
	public void oneItemIsNotItems()
	{
		assertEquals("1 item", ItemGrid.itemCount(1));
		assertEquals("0 items", ItemGrid.itemCount(0));
		assertEquals("67 items", ItemGrid.itemCount(67));
	}

	@Test
	public void summaryOnlySaysWhatsGoingOn()
	{
		assertNull(StorageView.summary(0, 0, 0));
		assertEquals("1 bounty", StorageView.summary(0, 1, 0));
		assertEquals("2 owed back  ·  3 bounties  ·  1 held for requests", StorageView.summary(2, 3, 1));
	}
}
