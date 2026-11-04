package com.homebase.ui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ItemSearchTest
{
	private static final Map<Integer, String> NAMES = new HashMap<>();

	static
	{
		NAMES.put(1, "Rune scimitar");
		NAMES.put(2, "Rune scimitar");
		NAMES.put(3, "Scimitar of rune");
		NAMES.put(4, "Dragon scimitar");
		NAMES.put(5, "Rune");
		NAMES.put(6, "Prune");
	}

	private static List<ItemSearch.Result> find(String query, ItemSearch.Holding... holdings)
	{
		return ItemSearch.find(query, Arrays.asList(holdings), NAMES::get, 10);
	}

	private static ItemSearch.Holding held(String owner, String place, int itemId, int quantity)
	{
		return new ItemSearch.Holding(owner, place, itemId, quantity);
	}

	@Test
	public void addsUpTheSameItemAcrossPeopleAndPlaces()
	{
		// item 2 is the noted version of item 1
		List<ItemSearch.Result> results = find("rune sci",
			held("Mundell", "Bank", 1, 2),
			held("Mundell", "Worn", 1, 1),
			held("Bonscott", "Bank", 2, 10),
			held("Mundell", "Bank", 2, 5));

		assertEquals(1, results.size());
		ItemSearch.Result result = results.get(0);
		assertEquals("Rune scimitar", result.getName());
		assertEquals(18, result.getTotal());
		assertEquals(Long.valueOf(7), result.getOwners().get("Mundell").get("Bank"));
		assertEquals(Long.valueOf(1), result.getOwners().get("Mundell").get("Worn"));
		assertEquals(Long.valueOf(10), result.getOwners().get("Bonscott").get("Bank"));
	}

	@Test
	public void bestMatchesComeFirst()
	{
		List<ItemSearch.Result> results = find("rune",
			held("A", "Bank", 6, 1),
			held("A", "Bank", 3, 1),
			held("A", "Bank", 1, 1),
			held("A", "Bank", 5, 1));

		assertEquals("Rune", results.get(0).getName());
		assertEquals("Rune scimitar", results.get(1).getName());
		assertEquals("Scimitar of rune", results.get(2).getName());
		assertEquals("Prune", results.get(3).getName());
	}

	@Test
	public void ignoresCaseAndSpacesAndUnnamedItems()
	{
		assertEquals(1, find("  DRAGON ", held("A", "Bank", 4, 1)).size());
		assertTrue(find("   ", held("A", "Bank", 4, 1)).isEmpty());
		// a name that hasn't been looked up yet can't match
		assertTrue(find("item", held("A", "Bank", 99, 1)).isEmpty());
	}

	@Test
	public void stopsAtTheLimit()
	{
		List<ItemSearch.Result> results = ItemSearch.find("rune",
			Arrays.asList(held("A", "Bank", 1, 1), held("A", "Bank", 3, 1), held("A", "Bank", 5, 1)), NAMES::get, 2);
		assertEquals(2, results.size());
		assertEquals("Rune", results.get(0).getName());
	}

	@Test
	public void describesWhereSomeoneHasIt()
	{
		Map<String, Long> places = new LinkedHashMap<>();
		places.put("Bank", 1200L);
		places.put("Worn", 1L);
		assertEquals("Mundell: Bank 1,200, Worn 1", ItemSearchView.describe("Mundell", places));

		Map<String, Long> storage = new LinkedHashMap<>();
		storage.put("Group storage", 5L);
		assertEquals("Group storage: 5", ItemSearchView.describe("Group storage", storage));
	}
}
