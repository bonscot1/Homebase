package com.homebase.collect;

import com.homebase.data.FeedData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FeedCollectorTest
{
	@Test
	public void recognisesTheMessagesForAPet()
	{
		assertTrue(FeedCollector.isPetMessage("You have a funny feeling like you're being followed."));
		assertTrue(FeedCollector.isPetMessage("You feel something weird sneaking into your backpack."));
		// this one means you'd have got a pet if you didn't already have it
		assertFalse(FeedCollector.isPetMessage("You have a funny feeling like you would have been followed..."));
		assertFalse(FeedCollector.isPetMessage("You pet the cat."));
	}

	@Test
	public void theFeedKeepsOnlyTheNewestEntries()
	{
		FeedData feed = new FeedData();
		for (int i = 1; i <= FeedData.MAX_ENTRIES + 5; i++)
		{
			feed.add(new FeedData.Entry(i, FeedData.DROP, "Item", 1, 1, null));
		}
		assertEquals(FeedData.MAX_ENTRIES, feed.getEntries().size());
		assertEquals(6, feed.getEntries().get(0).getAt());
		assertEquals(FeedData.MAX_ENTRIES + 5, feed.getEntries().get(FeedData.MAX_ENTRIES - 1).getAt());
	}
}
