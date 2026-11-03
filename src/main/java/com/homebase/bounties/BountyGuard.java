package com.homebase.bounties;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Hides the Withdraw options on group storage items that would take any of what's held back from you:
 * bounties you haven't earned yet, and items kept for other members' requests. Options that only take
 * from what's left over stay, so anything more than the amount held is free for anyone.
 */
@Singleton
public class BountyGuard
{
	private final Client client;
	private final StorageHolds holds;

	@Inject
	private BountyGuard(Client client, StorageHolds holds)
	{
		this.client = client;
		this.holds = holds;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (event.getActionParam1() != InterfaceID.SharedBank.ITEMS || event.getOption() == null
			|| !event.getOption().startsWith("Withdraw"))
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		Widget widget = entry.getWidget();
		int itemId = event.getItemId();
		if (itemId <= 0 && widget != null)
		{
			itemId = widget.getItemId();
		}
		if (itemId <= 0)
		{
			return;
		}
		StorageHolds.Hold hold = holds.holdFor(itemId);
		if (hold.getHeld() <= 0)
		{
			return;
		}
		int count = widget != null && widget.getItemQuantity() > 0 ? widget.getItemQuantity() : countInStorage(itemId);
		if (!StorageHolds.allowed(event.getOption(), count, hold.getHeld()))
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	private int countInStorage(int itemId)
	{
		ItemContainer storage = client.getItemContainer(InventoryID.INV_GROUP_TEMP);
		return storage == null ? 0 : storage.count(itemId);
	}
}
