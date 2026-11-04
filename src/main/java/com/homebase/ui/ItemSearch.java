package com.homebase.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import lombok.Getter;
import lombok.Value;

/**
 * Finds who has an item, across every container the group shares.
 */
final class ItemSearch
{
	private static final int EXACT = 0;
	private static final int STARTS_WITH = 1;
	private static final int WORD_STARTS_WITH = 2;
	private static final int CONTAINS = 3;

	private ItemSearch()
	{
	}

	/**
	 * Some of an item in one place.
	 */
	@Value
	static class Holding
	{
		/**
		 * The member who has it, or the name of a shared place such as the group storage.
		 */
		String owner;

		/**
		 * Where they have it, such as "Bank".
		 */
		String place;
		int itemId;
		int quantity;
	}

	/**
	 * One item found, and everyone who has it. Noted items and the like share a name, so they're
	 * counted as the same thing.
	 */
	@Getter
	static final class Result
	{
		private final String name;
		private final int itemId;
		private long total;

		/**
		 * Owner to place to how many they have there, in the order they were found.
		 */
		private final Map<String, Map<String, Long>> owners = new LinkedHashMap<>();

		private Result(String name, int itemId)
		{
			this.name = name;
			this.itemId = itemId;
		}

		private void add(Holding holding)
		{
			total += holding.getQuantity();
			owners.computeIfAbsent(holding.getOwner(), o -> new LinkedHashMap<>())
				.merge(holding.getPlace(), (long) holding.getQuantity(), Long::sum);
		}
	}

	/**
	 * @param nameOf an item's name, or null if it isn't known
	 * @return the items whose names contain the query, best matches first, at most {@code limit} of them
	 */
	static List<Result> find(String query, List<Holding> holdings, IntFunction<String> nameOf, int limit)
	{
		String wanted = query.trim().toLowerCase(Locale.ROOT);
		List<Result> results = new ArrayList<>();
		if (wanted.isEmpty())
		{
			return results;
		}
		Map<String, Result> byName = new HashMap<>();
		for (Holding holding : holdings)
		{
			String name = nameOf.apply(holding.getItemId());
			String lower = name == null ? null : name.toLowerCase(Locale.ROOT);
			if (lower == null || !lower.contains(wanted))
			{
				continue;
			}
			Result result = byName.get(lower);
			if (result == null)
			{
				result = new Result(name, holding.getItemId());
				byName.put(lower, result);
				results.add(result);
			}
			result.add(holding);
		}
		results.sort(Comparator.<Result>comparingInt(r -> rank(r.getName().toLowerCase(Locale.ROOT), wanted))
			.thenComparing(Result::getName, String.CASE_INSENSITIVE_ORDER));
		return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : results;
	}

	/**
	 * @return how well a name matches, lower being better
	 */
	private static int rank(String name, String wanted)
	{
		if (name.equals(wanted))
		{
			return EXACT;
		}
		if (name.startsWith(wanted))
		{
			return STARTS_WITH;
		}
		return name.contains(" " + wanted) ? WORD_STARTS_WITH : CONTAINS;
	}
}
