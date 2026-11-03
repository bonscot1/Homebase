package com.homebase.bounties;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Hides the Withdraw options on group storage items that are locked for you by a bounty.
 */
@Singleton
public class BountyGuard
{
	private final Client client;
	private final ItemManager itemManager;
	private final BountyService bounties;

	@Inject
	private BountyGuard(Client client, ItemManager itemManager, BountyService bounties)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.bounties = bounties;
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
		int itemId = event.getItemId();
		if (itemId <= 0 && entry.getWidget() != null)
		{
			itemId = entry.getWidget().getItemId();
		}
		if (itemId <= 0)
		{
			return;
		}
		BountyService.Lock lock = bounties.lockFor(itemManager.canonicalize(itemId));
		if (lock == null)
		{
			lock = bounties.lockFor(itemId);
		}
		if (lock != null && lock.isLocked())
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}
}
