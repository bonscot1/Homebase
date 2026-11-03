package com.homebase.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CollectionLogSearchTest
{
	@Test
	public void itemsAreSortedIntoTheirPages()
	{
		List<CollectionLogSearch.PageLayout> pages = Arrays.asList(
			new CollectionLogSearch.PageLayout("Vorkath", new int[]{1, 2, 3}),
			new CollectionLogSearch.PageLayout("Zulrah", new int[]{4, 5}),
			new CollectionLogSearch.PageLayout("Barrows Chests", new int[]{6}));

		// item 6 is listed under another id in the cache, and found under the one the game reports
		Map<String, CollectionLogSearch.PageResult> result = CollectionLogSearch.pagesWith(pages,
			new HashSet<>(Arrays.asList(1, 3, 60)), Collections.singletonMap(6, 60));

		assertEquals(Arrays.asList(1, 3), result.get("Vorkath").getObtained());
		assertEquals(3, result.get("Vorkath").getSize());
		assertEquals(Collections.singletonList(6), result.get("Barrows Chests").getObtained());
		// nothing from Zulrah yet, so the page is left out
		assertFalse(result.containsKey("Zulrah"));
	}
}
