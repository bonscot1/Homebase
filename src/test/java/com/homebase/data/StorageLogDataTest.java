package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.Test;

public class StorageLogDataTest
{
	private final Gson gson = new Gson();

	private StorageLogData read(String json)
	{
		return Checkable.checked(gson.fromJson(json, StorageLogData.class));
	}

	@Test
	public void theOldestEntriesDropOffWhenItsFull()
	{
		StorageLogData log = new StorageLogData();
		for (int i = 1; i <= StorageLogData.MAX_ENTRIES + 3; i++)
		{
			log.add(i, 1, i);
		}
		assertEquals(StorageLogData.MAX_ENTRIES, log.getEntries().size());
		assertEquals(4, log.getEntries().get(0).getItem());
		assertEquals(StorageLogData.MAX_ENTRIES + 3, log.getEntries().get(StorageLogData.MAX_ENTRIES - 1).getItem());
	}

	@Test
	public void missingListsAndNullsAreDropped()
	{
		assertTrue(read("{}").getEntries().isEmpty());
		assertTrue(read("{\"entries\":null}").getEntries().isEmpty());
		assertTrue(read("{\"entries\":[null]}").getEntries().isEmpty());
	}

	@Test
	public void entriesThatMeanNothingAreDropped()
	{
		StorageLogData log = read("{\"entries\":["
			+ "{\"item\":0,\"amount\":1,\"at\":5},"
			+ "{\"item\":-4,\"amount\":1,\"at\":5},"
			+ "{\"item\":385,\"amount\":0,\"at\":5},"
			+ "{\"item\":385,\"amount\":-2147483648,\"at\":5},"
			+ "{\"item\":385,\"amount\":1,\"at\":0},"
			+ "{\"item\":385,\"amount\":1},"
			+ "{\"item\":385,\"amount\":-3,\"at\":5}]}");
		assertEquals(1, log.getEntries().size());
		assertEquals(new StorageLogData.Entry(385, -3, 5), log.getEntries().get(0));
	}

	@Test
	public void aRunawayLogIsCapped()
	{
		StringBuilder entries = new StringBuilder();
		for (int i = 0; i < StorageLogData.MAX_ENTRIES + 10; i++)
		{
			entries.append(i == 0 ? "" : ",").append("{\"item\":385,\"amount\":1,\"at\":5}");
		}
		assertEquals(StorageLogData.MAX_ENTRIES, read("{\"entries\":[" + entries + "]}").getEntries().size());
	}

	@Test
	public void theSavedFormatStaysTheSame()
	{
		StorageLogData log = new StorageLogData();
		log.add(385, -5, 1000);
		assertEquals("{\"entries\":[{\"item\":385,\"amount\":-5,\"at\":1000}]}", gson.toJson(log));
	}

	@Test
	public void aFullLogIsSmallOnTheWire()
	{
		StorageLogData log = new StorageLogData();
		for (int i = 0; i < StorageLogData.MAX_ENTRIES; i++)
		{
			log.add(30_000 + i, -2_000_000_000, 1_790_000_000_000L);
		}
		assertTrue(gson.toJson(log).length() < 6_000);
	}

	@Test
	public void itsRecordIsTheMembersOwnAndItsKeyStaysTheSame()
	{
		assertEquals("m:abc:storagelog", Section.STORAGE_LOG.keyFor("abc"));
	}
}
