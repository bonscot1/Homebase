package com.homebase.storage;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Follows one member's time with group storage open and says what each save changed.
 * <p>
 * While it's open the game only shows a temporary copy of storage. Changes count once they're saved, and
 * can be lost until then, so only a save produces anything. Everything is compared with what storage
 * held when it was opened or last saved, so opening and closing it, putting something in and taking it
 * back, or moving items around all come to nothing.
 */
final class StorageSession
{
	/**
	 * Ticks after the interface closes to wait for its save to show up.
	 */
	static final int CLOSE_GRACE_TICKS = 5;

	/**
	 * What storage held when opened or last saved, or null while nothing is being followed.
	 */
	private Map<Integer, Integer> saved;

	/**
	 * What it holds now, as far as we've seen.
	 */
	private Map<Integer, Integer> current;
	private int closedTicks;

	/**
	 * The interface was drawn from what the game sent. It's the one time an empty storage can be
	 * believed: an empty container on its own can just mean it hasn't been filled in yet. Only while
	 * it's closing is it ignored, since by then the temporary copy may already be gone.
	 */
	void built(Map<Integer, Integer> now, boolean open)
	{
		if (open || saved == null)
		{
			look(now);
		}
	}

	/**
	 * The game reported a change in the container. It can arrive just after the interface closes, if the
	 * last change and leaving happened on the same tick, but it never starts anything then.
	 */
	void changed(Map<Integer, Integer> now, boolean open)
	{
		if (!now.isEmpty() && (open || saved != null))
		{
			look(now);
		}
	}

	private void look(Map<Integer, Integer> now)
	{
		if (saved == null)
		{
			saved = now;
		}
		current = now;
	}

	/**
	 * Call once a game tick, after that tick's changes.
	 *
	 * @param open true if the interface is open
	 * @param saving true if the game has shown that it's saving
	 * @return what was saved, if a save just happened
	 */
	List<StorageHistory.Change> tick(boolean open, boolean saving)
	{
		List<StorageHistory.Change> out = Collections.emptyList();
		if (saving && saved != null)
		{
			List<StorageHistory.Change> changes = StorageHistory.changes(saved, current);
			if (StorageHistory.possible(changes))
			{
				out = changes;
			}
			saved = current;
		}
		if (open || saving)
		{
			closedTicks = 0;
		}
		else if (saved != null && ++closedTicks > CLOSE_GRACE_TICKS)
		{
			reset();
		}
		return out;
	}

	void reset()
	{
		saved = null;
		current = null;
		closedTicks = 0;
	}
}
