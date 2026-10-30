package com.homebase.sync;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Records waiting to be broadcast to the party.
 * <p>
 * When several online members all hold data a newcomer is missing, each of them queues it with a small
 * random delay. Whoever's delay runs out first sends it, and everyone else sees that broadcast and drops
 * their own copy instead of sending a duplicate.
 */
public class OutboundQueue
{
	private static final class Pending
	{
		final SyncRecord record;
		final long dueAt;

		Pending(SyncRecord record, long dueAt)
		{
			this.record = record;
			this.dueAt = dueAt;
		}
	}

	private final Map<String, Pending> pending = new LinkedHashMap<>();

	public synchronized void offer(SyncRecord record, long dueAt)
	{
		Pending existing = pending.get(record.getKey());
		if (existing == null)
		{
			pending.put(record.getKey(), new Pending(record, dueAt));
			return;
		}
		SyncRecord newest = record.supersedes(existing.record) ? record : existing.record;
		pending.put(record.getKey(), new Pending(newest, Math.min(dueAt, existing.dueAt)));
	}

	/**
	 * Someone broadcast a record, so we no longer need to send our copy if it is the same or older.
	 */
	public synchronized void seen(SyncRecord record)
	{
		Pending existing = pending.get(record.getKey());
		if (existing != null && !existing.record.supersedes(record))
		{
			pending.remove(record.getKey());
		}
	}

	/**
	 * Remove and return records that are due, up to roughly {@code budget} in size. At least one due
	 * record is always returned so that a single oversized record can't block the queue.
	 */
	public synchronized List<SyncRecord> takeDue(long now, int budget, ToIntFunction<SyncRecord> sizer)
	{
		List<SyncRecord> out = new ArrayList<>();
		int used = 0;
		for (Iterator<Pending> it = pending.values().iterator(); it.hasNext(); )
		{
			Pending p = it.next();
			if (p.dueAt > now)
			{
				continue;
			}
			int size = sizer.applyAsInt(p.record);
			if (!out.isEmpty() && used + size > budget)
			{
				continue;
			}
			out.add(p.record);
			used += size;
			it.remove();
		}
		return out;
	}

	public synchronized int size()
	{
		return pending.size();
	}

	public synchronized void clear()
	{
		pending.clear();
	}
}
