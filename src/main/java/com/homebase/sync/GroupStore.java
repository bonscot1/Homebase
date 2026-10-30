package com.homebase.sync;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Every client's local copy of the group's data. Thread safe.
 */
public class GroupStore
{
	private final Map<String, SyncRecord> records = new HashMap<>();
	private final List<Consumer<SyncRecord>> listeners = new CopyOnWriteArrayList<>();

	public void addListener(Consumer<SyncRecord> listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Consumer<SyncRecord> listener)
	{
		listeners.remove(listener);
	}

	/**
	 * Store a record if it is newer than the copy we have.
	 *
	 * @return true if the record was stored
	 */
	public boolean offer(SyncRecord record)
	{
		synchronized (this)
		{
			if (!record.supersedes(records.get(record.getKey())))
			{
				return false;
			}
			records.put(record.getKey(), record);
		}
		notifyListeners(record);
		return true;
	}

	/**
	 * Write a new version of a key, guaranteed to be newer than any copy we have seen.
	 *
	 * @return the record written, or null if the data is unchanged
	 */
	public SyncRecord write(String key, String author, JsonElement data, boolean deleted, long now)
	{
		SyncRecord record;
		synchronized (this)
		{
			SyncRecord current = records.get(key);
			if (current != null && current.isDeleted() == deleted && current.getAuthor().equals(author)
				&& (deleted || current.getData().equals(data)))
			{
				return null;
			}
			long version = current == null ? now : Math.max(now, current.getVersion() + 1);
			record = new SyncRecord(key, version, author, deleted, deleted ? null : data);
			records.put(key, record);
		}
		notifyListeners(record);
		return record;
	}

	/**
	 * @return the record for a key, or null if missing or deleted
	 */
	public synchronized SyncRecord get(String key)
	{
		SyncRecord record = records.get(key);
		return record == null || record.isDeleted() ? null : record;
	}

	/**
	 * @return live records whose key starts with the prefix
	 */
	public synchronized List<SyncRecord> withPrefix(String prefix)
	{
		List<SyncRecord> out = new ArrayList<>();
		for (SyncRecord record : records.values())
		{
			if (!record.isDeleted() && record.getKey().startsWith(prefix))
			{
				out.add(record);
			}
		}
		return out;
	}

	/**
	 * @return every record including tombstones, for saving to disk
	 */
	public synchronized List<SyncRecord> all()
	{
		return new ArrayList<>(records.values());
	}

	/**
	 * @return key to version for everything we hold, which is what we tell peers we have
	 */
	public synchronized Map<String, Long> versions()
	{
		Map<String, Long> out = new HashMap<>(records.size());
		for (SyncRecord record : records.values())
		{
			out.put(record.getKey(), record.getVersion());
		}
		return out;
	}

	/**
	 * @return records we hold that the peer is missing or has an older copy of
	 */
	public synchronized List<SyncRecord> newerThan(Map<String, Long> peer)
	{
		List<SyncRecord> out = new ArrayList<>();
		for (SyncRecord record : records.values())
		{
			Long theirs = peer.get(record.getKey());
			if (theirs == null || record.getVersion() > theirs)
			{
				out.add(record);
			}
		}
		return out;
	}

	/**
	 * @return true if the peer has any key we are missing or hold an older copy of
	 */
	public synchronized boolean isBehind(Map<String, Long> peer)
	{
		for (Map.Entry<String, Long> entry : peer.entrySet())
		{
			SyncRecord ours = records.get(entry.getKey());
			if (entry.getValue() != null && (ours == null || entry.getValue() > ours.getVersion()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Forget tombstones older than the cutoff.
	 *
	 * @return number of tombstones removed
	 */
	public synchronized int purgeDeleted(long cutoff)
	{
		int before = records.size();
		records.values().removeIf(r -> r.isDeleted() && r.getVersion() < cutoff);
		return before - records.size();
	}

	public synchronized int size()
	{
		return records.size();
	}

	private void notifyListeners(SyncRecord record)
	{
		for (Consumer<SyncRecord> listener : listeners)
		{
			listener.accept(record);
		}
	}
}
