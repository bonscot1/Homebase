package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The newest things to have happened to a member that can't be worked out from the rest of their data,
 * because nothing is left to compare: valuable drops, pets and deaths.
 */
@Data
public class FeedData implements Checkable
{
	public static final String DROP = "drop";
	public static final String PET = "pet";
	public static final String DEATH = "death";

	/**
	 * Most entries kept, oldest dropped first.
	 */
	public static final int MAX_ENTRIES = 40;

	/**
	 * Oldest first.
	 */
	private List<Entry> entries = new ArrayList<>();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Entry
	{
		/**
		 * When it happened, which is also how a newer copy's entries are told from an older copy's.
		 */
		private long at;

		/**
		 * {@link #DROP}, {@link #PET} or {@link #DEATH}.
		 */
		private String type;

		/**
		 * The item dropped, or where a death happened.
		 */
		private String name;

		private int quantity;

		/**
		 * What the whole stack is worth in coins.
		 */
		private long value;

		/**
		 * What dropped it, such as "Vorkath".
		 */
		private String source;

		/**
		 * The dropped item's id, for its picture, or 0 for anything else. Older versions of the plugin
		 * don't send it.
		 */
		private int item;

		public Entry(long at, String type, String name, int quantity, long value, String source)
		{
			this(at, type, name, quantity, value, source, 0);
		}
	}

	public void add(Entry entry)
	{
		entries.add(entry);
		while (entries.size() > MAX_ENTRIES)
		{
			entries.remove(0);
		}
	}

	@Override
	public void check()
	{
		entries = Checkable.list(entries, MAX_ENTRIES);
		entries.forEach(entry -> entry.setItem(Math.max(0, entry.getItem())));
	}
}
