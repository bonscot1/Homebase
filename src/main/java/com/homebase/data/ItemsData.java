package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

@Data
public class ItemsData
{
	/**
	 * Item id and quantity pairs in slot order, with -1 for empty slots, the same layout RuneLite's own
	 * party sync uses.
	 */
	private int[] items = new int[0];

	public static ItemsData of(ItemContainer container)
	{
		ItemsData data = new ItemsData();
		if (container == null)
		{
			return data;
		}
		Item[] slots = container.getItems();
		int[] packed = new int[slots.length * 2];
		for (int i = 0; i < slots.length; i++)
		{
			Item item = slots[i];
			boolean empty = item == null || item.getId() < 0 || item.getQuantity() <= 0;
			packed[i * 2] = empty ? -1 : item.getId();
			packed[i * 2 + 1] = empty ? 0 : item.getQuantity();
		}
		data.items = packed;
		return data;
	}

	/**
	 * @return the slots, with null for empty ones
	 */
	public List<Item> toItems()
	{
		List<Item> out = new ArrayList<>(items.length / 2);
		for (int i = 0; i + 1 < items.length; i += 2)
		{
			out.add(items[i] < 0 || items[i + 1] <= 0 ? null : new Item(items[i], items[i + 1]));
		}
		return out;
	}

	public boolean isEmpty()
	{
		for (int i = 0; i < items.length; i += 2)
		{
			if (items[i] >= 0)
			{
				return false;
			}
		}
		return true;
	}
}
