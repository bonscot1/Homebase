package com.homebase.storage;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ReturnTagsTest
{
	@Test
	public void onlyRealItemIdsCount()
	{
		assertEquals(4151, ReturnTags.itemOf("4151"));
		assertEquals(0, ReturnTags.itemOf("-5"));
		assertEquals(0, ReturnTags.itemOf("shark"));
		assertEquals(0, ReturnTags.itemOf(""));
	}
}
