package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What one member has put in and taken out of group storage, oldest first. Who did it isn't written
 * here: it's the member whose record this is, and only they can write it. Each member keeps their own
 * log, so two members saving while they can't reach each other can't overwrite one another's entries.
 */
@Data
public class StorageLogData implements Checkable
{
	/**
	 * Most entries one member keeps, with the oldest dropped first. An entry is 55 bytes at most, so a
	 * full log is under 6 KB before it's compressed.
	 */
	public static final int MAX_ENTRIES = 100;

	private List<Entry> entries = new ArrayList<>();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Entry
	{
		private int item;

		/**
		 * How many: above 0 when put in, below 0 when taken out.
		 */
		private int amount;

		/**
		 * When the change was saved.
		 */
		private long at;
	}

	public void add(int item, int amount, long at)
	{
		entries.add(new Entry(item, amount, at));
		while (entries.size() > MAX_ENTRIES)
		{
			entries.remove(0);
		}
	}

	@Override
	public void check()
	{
		entries = Checkable.list(entries, MAX_ENTRIES);
		// Integer.MIN_VALUE has no positive amount to show
		entries.removeIf(e -> e.item <= 0 || e.amount == 0 || e.amount == Integer.MIN_VALUE || e.at <= 0);
	}
}
