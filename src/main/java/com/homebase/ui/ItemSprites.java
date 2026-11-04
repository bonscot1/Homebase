package com.homebase.ui;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Item pictures and names for the panel's grids. Used on the Swing thread only.
 * <p>
 * RuneLite remembers only its last 128 item pictures, and draws each new one on the game's own thread.
 * A bank holds far more than that, so without keeping our own the game would stutter while a bank's
 * pictures were redrawn, every time the panel refreshed with it open.
 */
class ItemSprites
{
	/**
	 * At about 4.5 KB a picture, this is under 7 MB at most.
	 */
	private static final int MAX_KEPT = 1_500;

	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Map<Long, AsyncBufferedImage> kept = new LinkedHashMap<Long, AsyncBufferedImage>(256, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, AsyncBufferedImage> eldest)
		{
			return size() > MAX_KEPT;
		}
	};

	ItemSprites(ItemManager itemManager, ClientThread clientThread)
	{
		this.itemManager = itemManager;
		this.clientThread = clientThread;
	}

	/**
	 * @return the item's picture, which may still be blank while it waits for the game's thread
	 */
	AsyncBufferedImage picture(Item item)
	{
		long key = (long) item.getId() << 32 | item.getQuantity() & 0xFFFFFFFFL;
		AsyncBufferedImage picture = kept.get(key);
		if (picture == null)
		{
			picture = load(item);
			kept.put(key, picture);
		}
		return picture;
	}

	AsyncBufferedImage load(Item item)
	{
		return itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1);
	}

	/**
	 * Have these items' names ready for {@link ItemNames#get}.
	 */
	void lookUpNames(Collection<Integer> itemIds)
	{
		ItemNames.resolve(itemIds, itemManager, clientThread, () ->
		{
		});
	}
}
