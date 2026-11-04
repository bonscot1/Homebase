package com.homebase.ui;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Item names for tooltips and lists, shared by the whole panel. Names come from the game's cache,
 * which can only be read on the client thread, so they're looked up once and kept.
 */
final class ItemNames
{
	/**
	 * Most names looked up in one go, so searching everyone's banks can't stall a game frame.
	 */
	private static final int NAMES_AT_A_TIME = 200;

	private static final Map<Integer, String> NAMES = new ConcurrentHashMap<>();

	private ItemNames()
	{
	}

	/**
	 * @return the item's name, or "item 1234" if it hasn't been looked up yet
	 */
	static String get(int itemId)
	{
		return NAMES.getOrDefault(itemId, "item " + itemId);
	}

	/**
	 * @return the item's name, or null if it hasn't been looked up yet
	 */
	static String known(int itemId)
	{
		return NAMES.get(itemId);
	}

	/**
	 * Look up any names we don't have yet, then run {@code done} on the Swing thread. Call it from the
	 * Swing thread, since {@code done} runs straight away if every name is already known.
	 */
	static void resolve(Collection<Integer> itemIds, ItemManager itemManager, ClientThread clientThread, Runnable done)
	{
		Deque<Integer> missing = new ArrayDeque<>();
		for (Integer id : itemIds)
		{
			if (id != null && id > 0 && !NAMES.containsKey(id))
			{
				missing.add(id);
			}
		}
		if (missing.isEmpty())
		{
			done.run();
			return;
		}
		clientThread.invokeLater(() ->
		{
			for (int i = 0; i < NAMES_AT_A_TIME && !missing.isEmpty(); i++)
			{
				int id = missing.remove();
				try
				{
					NAMES.put(id, itemManager.getItemComposition(id).getName());
				}
				catch (RuntimeException e)
				{
					// an id from another member's client that the game doesn't know shouldn't stop the rest
					NAMES.put(id, "item " + id);
				}
			}
			if (!missing.isEmpty())
			{
				// not finished, so this runs again on the game's next cycle
				return false;
			}
			SwingUtilities.invokeLater(done);
			return true;
		});
	}
}
