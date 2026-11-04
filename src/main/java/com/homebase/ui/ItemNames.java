package com.homebase.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * Item names for tooltips and lists, shared by the whole panel. Names come from the game's cache,
 * which can only be read on the client thread, so they're looked up once and kept.
 */
@Slf4j
final class ItemNames
{
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
	 * @return true if the item's name has been looked up
	 */
	static boolean known(int itemId)
	{
		return NAMES.containsKey(itemId);
	}

	/**
	 * Look up any names we don't have yet, then run {@code done} on the Swing thread. Call it from the
	 * Swing thread, since {@code done} runs straight away if every name is already known.
	 */
	static void resolve(Collection<Integer> itemIds, ItemManager itemManager, ClientThread clientThread, Runnable done)
	{
		List<Integer> missing = new ArrayList<>();
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
			for (int id : missing)
			{
				NAMES.put(id, lookUp(id, itemManager));
			}
			SwingUtilities.invokeLater(done);
		});
	}

	/**
	 * @return the item's name. Ids can come from other players, so one the game doesn't know just keeps
	 * the stand-in name, rather than stopping the names after it from being looked up.
	 */
	private static String lookUp(int itemId, ItemManager itemManager)
	{
		String name = null;
		try
		{
			name = itemManager.getItemComposition(itemId).getName();
		}
		catch (RuntimeException e)
		{
			log.debug("No name for item {}", itemId, e);
		}
		return name == null ? "item " + itemId : name;
	}
}
