package com.homebase.bounties;

import com.homebase.HomebaseConfig;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Stops withdrawals from group storage that would take any of what's held back from you: bounties you
 * haven't earned yet, and items kept for other members' requests. Withdraw options that only take from
 * what's left over stay, so anything more than the amount held is free for anyone.
 * <p>
 * Clicks can come faster than the game reports what's left, so withdrawals we've let through are
 * counted until the game catches up, and a click that would dip into what's held is stopped.
 */
@Singleton
public class BountyGuard
{
	/**
	 * Ticks a withdrawal we let through stays counted while the game hasn't reported it.
	 */
	private static final int PENDING_TICKS = 3;

	private final Client client;
	private final StorageHolds holds;
	private final HomebaseConfig config;

	/**
	 * Per item: how many we've let through that the game hasn't taken out of storage yet.
	 */
	private final Map<Integer, Integer> pending = new HashMap<>();

	/**
	 * Per item: how many group storage held at the game's last report.
	 */
	private Map<Integer, Integer> reported = new HashMap<>();

	/**
	 * When we last let a withdrawal through. One the game refuses, such as with a full inventory, never
	 * shows up, so withdrawals still counted a few ticks later are forgotten.
	 */
	private int lastWithdrawTick;

	@Inject
	private BountyGuard(Client client, StorageHolds holds, HomebaseConfig config)
	{
		this.client = client;
		this.holds = holds;
		this.config = config;
	}

	/**
	 * Forget withdrawals still being counted, when the plugin stops.
	 */
	public void reset()
	{
		pending.clear();
		reported = new HashMap<>();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.holdItems() || !isWithdraw(event.getActionParam1(), event.getOption()))
		{
			return;
		}
		MenuEntry entry = event.getMenuEntry();
		int itemId = itemId(event.getItemId(), entry.getWidget());
		if (itemId <= 0)
		{
			return;
		}
		StorageHolds.Hold hold = holds.holdFor(itemId);
		if (hold.getHeld() > 0 && !StorageHolds.allowed(event.getOption(), available(itemId), hold.getHeld()))
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		if (!config.holdItems() || !isWithdraw(entry.getParam1(), entry.getOption()))
		{
			return;
		}
		int itemId = itemId(entry.getItemId(), entry.getWidget());
		if (itemId <= 0)
		{
			return;
		}
		StorageHolds.Hold hold = holds.holdFor(itemId);
		if (hold.getHeld() <= 0)
		{
			return;
		}
		int count = available(itemId);
		if (!StorageHolds.allowed(entry.getOption(), count, hold.getHeld()))
		{
			// the menu was built before an earlier withdrawal showed up
			event.consume();
			return;
		}
		pending.merge(itemId, StorageHolds.amount(entry.getOption(), count), Integer::sum);
		lastWithdrawTick = client.getTickCount();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!pending.isEmpty() && client.getTickCount() - lastWithdrawTick > PENDING_TICKS)
		{
			pending.clear();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV_GROUP_TEMP)
		{
			return;
		}
		Map<Integer, Integer> now = counts(event.getItemContainer());
		// withdrawals the game has now reported no longer need counting
		pending.replaceAll((itemId, amount) ->
		{
			int taken = Math.max(0, reported.getOrDefault(itemId, 0) - now.getOrDefault(itemId, 0));
			return Math.max(0, amount - taken);
		});
		pending.values().removeIf(amount -> amount == 0);
		reported = now;
	}

	/**
	 * @return how many of an item are in group storage once withdrawals still on their way are taken off
	 */
	private int available(int itemId)
	{
		ItemContainer storage = client.getItemContainer(InventoryID.INV_GROUP_TEMP);
		int count = storage == null ? 0 : storage.count(itemId);
		return Math.max(0, count - pending.getOrDefault(itemId, 0));
	}

	private static boolean isWithdraw(int param1, String option)
	{
		return param1 == InterfaceID.SharedBank.ITEMS && option != null && option.startsWith("Withdraw");
	}

	private static int itemId(int itemId, Widget widget)
	{
		return itemId > 0 || widget == null ? itemId : widget.getItemId();
	}

	private static Map<Integer, Integer> counts(ItemContainer container)
	{
		Map<Integer, Integer> out = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0)
				{
					out.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return out;
	}
}
