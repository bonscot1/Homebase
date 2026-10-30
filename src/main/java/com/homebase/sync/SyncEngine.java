package com.homebase.sync;

import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * Decides what to send and what to accept. Has no networking of its own: the party transport feeds it
 * peers' messages and broadcasts whatever {@link #takeDue} hands back.
 * <p>
 * The protocol is gossip. Members tell each other which version of every key they hold, and anyone
 * holding a newer copy sends it. Because whoever receives data also holds it, information hops between
 * members who are never online at the same time.
 */
public class SyncEngine
{
	static final long RELAY_DELAY_MIN_MS = 300;
	static final long RELAY_DELAY_MAX_MS = 2500;

	/**
	 * Records stamped further in the future than this are rejected, so a broken clock can't publish a
	 * version nobody can ever replace.
	 */
	static final long MAX_CLOCK_AHEAD_MS = TimeUnit.DAYS.toMillis(1);

	private final GroupStore store;
	private final OutboundQueue queue = new OutboundQueue();
	private final LongSupplier clock;
	private final Random random;

	public SyncEngine(GroupStore store, LongSupplier clock, Random random)
	{
		this.store = store;
		this.clock = clock;
		this.random = random;
	}

	public GroupStore getStore()
	{
		return store;
	}

	/**
	 * @return key to version for everything we hold
	 */
	public Map<String, Long> summary()
	{
		return store.versions();
	}

	/**
	 * A peer told us what it holds. Queue everything we have that is newer.
	 *
	 * @return true if the peer holds something newer than us, meaning we should send our own summary
	 */
	public boolean onSummary(Map<String, Long> peer)
	{
		long now = clock.getAsLong();
		for (SyncRecord record : store.newerThan(peer))
		{
			queue.offer(record, now + relayDelay());
		}
		return store.isBehind(peer);
	}

	/**
	 * A peer broadcast records.
	 *
	 * @return how many were newer than ours and stored
	 */
	public int onRecords(Collection<SyncRecord> records)
	{
		long now = clock.getAsLong();
		int accepted = 0;
		for (SyncRecord record : records)
		{
			if (!isAcceptable(record, now))
			{
				continue;
			}
			queue.seen(record);
			if (store.offer(record))
			{
				accepted++;
			}
		}
		return accepted;
	}

	/**
	 * Load records from the save file. Nothing is queued, since peers will ask for what they lack.
	 */
	public void load(Collection<SyncRecord> records)
	{
		long now = clock.getAsLong();
		for (SyncRecord record : records)
		{
			if (isAcceptable(record, now))
			{
				store.offer(record);
			}
		}
	}

	/**
	 * Write local data and queue it to send straight away. Does nothing if the data is unchanged.
	 *
	 * @return the record written, or null if nothing changed
	 */
	public SyncRecord publish(String key, String author, JsonElement data)
	{
		return write(key, author, data, false);
	}

	public SyncRecord delete(String key, String author)
	{
		return write(key, author, null, true);
	}

	public List<SyncRecord> takeDue(int budget, ToIntFunction<SyncRecord> sizer)
	{
		return queue.takeDue(clock.getAsLong(), budget, sizer);
	}

	public int pendingCount()
	{
		return queue.size();
	}

	private SyncRecord write(String key, String author, JsonElement data, boolean deleted)
	{
		long now = clock.getAsLong();
		SyncRecord record = store.write(key, author, data, deleted, now);
		if (record != null)
		{
			queue.offer(record, now);
		}
		return record;
	}

	static boolean isAcceptable(SyncRecord record, long now)
	{
		if (record == null || !Keys.isValid(record.getKey()) || record.getAuthor() == null || record.getAuthor().isEmpty())
		{
			return false;
		}
		if (record.getVersion() <= 0 || record.getVersion() > now + MAX_CLOCK_AHEAD_MS)
		{
			return false;
		}
		if (!record.isDeleted() && record.getData() == null)
		{
			return false;
		}
		// member data can only be written by the member it belongs to
		String owner = Keys.memberOf(record.getKey());
		return owner == null || owner.equals(record.getAuthor());
	}

	private long relayDelay()
	{
		return RELAY_DELAY_MIN_MS + (long) (random.nextDouble() * (RELAY_DELAY_MAX_MS - RELAY_DELAY_MIN_MS));
	}
}
