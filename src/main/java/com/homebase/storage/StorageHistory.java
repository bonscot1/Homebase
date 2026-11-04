package com.homebase.storage;

import com.homebase.data.StorageLogData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.Value;
import net.runelite.api.Item;

/**
 * Works out what changed in group storage, and what the group's logs say happened and what's still out.
 */
public final class StorageHistory
{
	/**
	 * More kinds of item than an inventory and worn equipment hold. A save can't move this many in one
	 * direction, so a change this big means what we saw wasn't really what storage held.
	 */
	static final int MAX_KINDS_PER_SAVE = 40;

	private StorageHistory()
	{
	}

	/**
	 * Some of one item going in or out of group storage.
	 */
	@Value
	static class Change
	{
		int item;

		/**
		 * Above 0 when put in, below 0 when taken out.
		 */
		int amount;
	}

	/**
	 * One member's log, as read for the panel.
	 */
	@Value
	public static class MemberLog
	{
		String name;
		StorageLogData log;

		/**
		 * When the log was written. No entry in it can be newer than this.
		 */
		long updatedAt;
	}

	/**
	 * A line of the history: who put in or took out how many of what, and when.
	 */
	@Value
	public static class Line
	{
		String who;
		int item;
		int amount;
		long at;
	}

	/**
	 * Some of a marked item a member has taken out and not put back, and when they last took some.
	 */
	@Value
	public static class Owed
	{
		String who;
		int item;
		long amount;
		long at;
	}

	/**
	 * @return how many of each item the slots hold, wherever they are
	 */
	static Map<Integer, Integer> counts(Item[] slots)
	{
		Map<Integer, Integer> out = new HashMap<>();
		if (slots != null)
		{
			for (Item item : slots)
			{
				if (item != null && item.getId() > 0 && item.getQuantity() > 0)
				{
					out.merge(item.getId(), item.getQuantity(), (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
				}
			}
		}
		return out;
	}

	/**
	 * @return what changed from one look at group storage to a later one, by item. Items only count by
	 * how many there are, so moving them between slots is no change.
	 */
	static List<Change> changes(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		TreeSet<Integer> items = new TreeSet<>(before.keySet());
		items.addAll(after.keySet());
		List<Change> out = new ArrayList<>();
		for (int item : items)
		{
			int amount = after.getOrDefault(item, 0) - before.getOrDefault(item, 0);
			if (amount != 0)
			{
				out.add(new Change(item, amount));
			}
		}
		return out;
	}

	/**
	 * @return false if no one save could have made these changes
	 */
	static boolean possible(List<Change> changes)
	{
		int in = 0;
		int out = 0;
		for (Change change : changes)
		{
			if (change.getAmount() > 0)
			{
				in++;
			}
			else
			{
				out++;
			}
		}
		return in <= MAX_KINDS_PER_SAVE && out <= MAX_KINDS_PER_SAVE;
	}

	/**
	 * @return every member's entries together, newest first
	 */
	public static List<Line> lines(List<MemberLog> logs)
	{
		List<Line> out = new ArrayList<>();
		for (MemberLog member : logs)
		{
			for (StorageLogData.Entry entry : member.getLog().getEntries())
			{
				out.add(new Line(member.getName(), entry.getItem(), entry.getAmount(), timeOf(entry, member)));
			}
		}
		out.sort(Comparator.comparingLong(Line::getAt).reversed());
		return out;
	}

	/**
	 * Something is owed when it's marked as needing to be returned and a member has taken out more of
	 * it than they've put in, so taking back what you put in yourself doesn't count. Only their own
	 * entries count: someone else putting in the same item doesn't settle it. Neither do entries from
	 * before it was marked, since whoever took it then was free to keep it.
	 *
	 * @param marked each item marked as needing to be returned, and when it was marked
	 * @return what's still out, most recently taken first
	 */
	public static List<Owed> owed(List<MemberLog> logs, Map<Integer, Long> marked)
	{
		List<Owed> out = new ArrayList<>();
		for (MemberLog member : logs)
		{
			Map<Integer, Long> net = new HashMap<>();
			Map<Integer, Long> lastTaken = new HashMap<>();
			for (StorageLogData.Entry entry : member.getLog().getEntries())
			{
				Long markedAt = marked.get(entry.getItem());
				long at = timeOf(entry, member);
				if (markedAt == null || at < markedAt)
				{
					continue;
				}
				net.merge(entry.getItem(), (long) entry.getAmount(), Long::sum);
				if (entry.getAmount() < 0)
				{
					lastTaken.merge(entry.getItem(), at, Math::max);
				}
			}
			net.forEach((item, amount) ->
			{
				if (amount < 0)
				{
					out.add(new Owed(member.getName(), item, -amount, lastTaken.get(item)));
				}
			});
		}
		out.sort(Comparator.comparingLong(Owed::getAt).reversed().thenComparingInt(Owed::getItem));
		return out;
	}

	private static long timeOf(StorageLogData.Entry entry, MemberLog member)
	{
		return Math.min(entry.getAt(), member.getUpdatedAt());
	}
}
