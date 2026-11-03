package com.homebase.requests;

import com.homebase.data.RequestData;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * Finishes requests from what actually happens in game, so nobody can just say they're done. The
 * member who took a request on delivers it one of two ways:
 * <ul>
 * <li>putting the item in group storage, where it's kept for the member who asked, and the request is
 * done once they take it out. Only one member can have group storage open at a time, so the changes
 * seen there are our own.</li>
 * <li>trading it to the member who asked, which counts when the trade is accepted.</li>
 * </ul>
 * Amounts add up, so a request for 500 can be delivered as 200 and then 300.
 */
@Singleton
public class RequestDelivery
{
	private static final String TRADE_ACCEPTED = "Accepted trade.";

	/**
	 * Ticks after the trade screens close to wait for the game's "Accepted trade." message.
	 */
	private static final int TRADE_GRACE_TICKS = 2;

	private final Client client;
	private final ItemManager itemManager;
	private final RequestService requests;

	/**
	 * What group storage held at the last change, or null while it's closed.
	 */
	private Map<Integer, Integer> lastCounts;

	/**
	 * Who we're trading with and what we're offering, kept until the trade is accepted.
	 */
	private String tradePartner;
	private Map<Integer, Integer> offered = new HashMap<>();
	private boolean tradeOpen;
	private int tradeClosedTick;

	@Inject
	private RequestDelivery(Client client, ItemManager itemManager, RequestService requests)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.requests = requests;
	}

	/**
	 * Forget any storage or trade in progress, when the plugin stops.
	 */
	public void reset()
	{
		lastCounts = null;
		tradePartner = null;
		offered = new HashMap<>();
		tradeOpen = false;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.TRADEOFFER)
		{
			// the offer empties as the trade goes through, so only changes made on the first screen count
			Map<Integer, Integer> offer = counts(event.getItemContainer());
			if (!offer.isEmpty() || visible(InterfaceID.Trademain.TITLE))
			{
				offered = offer;
			}
			return;
		}
		if (event.getContainerId() != InventoryID.INV_GROUP_TEMP)
		{
			return;
		}
		Map<Integer, Integer> now = counts(event.getItemContainer());
		Map<Integer, Integer> before = lastCounts;
		lastCounts = now;
		// the first look after opening is only where we start from
		if (before == null || !requests.canEdit())
		{
			return;
		}
		for (Map.Entry<Integer, Integer> e : now.entrySet())
		{
			int added = e.getValue() - before.getOrDefault(e.getKey(), 0);
			if (added > 0)
			{
				deposited(e.getKey(), added);
			}
		}
		for (Map.Entry<Integer, Integer> e : before.entrySet())
		{
			int taken = e.getValue() - now.getOrDefault(e.getKey(), 0);
			if (taken > 0)
			{
				withdrawn(e.getKey(), taken);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (lastCounts != null && !visible(InterfaceID.SharedBank.ITEMS))
		{
			lastCounts = null;
		}

		String partner = partnerFrom(InterfaceID.Trademain.TITLE);
		if (partner == null)
		{
			partner = partnerFrom(InterfaceID.Tradeconfirm.TRADEOPPONENT);
		}
		if (partner != null)
		{
			if (!tradeOpen)
			{
				// a new trade: forget the last one's offer
				offered = new HashMap<>();
			}
			tradeOpen = true;
			tradePartner = partner;
		}
		else if (tradeOpen)
		{
			tradeOpen = false;
			tradeClosedTick = client.getTickCount();
		}
		else if (tradePartner != null && client.getTickCount() - tradeClosedTick > TRADE_GRACE_TICKS)
		{
			// declined, or closed without trading
			tradePartner = null;
			offered = new HashMap<>();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		// only the game's own message counts, not a player typing the same words
		boolean fromGame = event.getType() == ChatMessageType.TRADE || event.getType() == ChatMessageType.GAMEMESSAGE;
		if (tradePartner == null || !fromGame || !TRADE_ACCEPTED.equals(Text.removeTags(event.getMessage()).trim()))
		{
			return;
		}
		String partner = tradePartner;
		Map<Integer, Integer> gave = offered;
		tradePartner = null;
		offered = new HashMap<>();
		if (!requests.canEdit())
		{
			return;
		}
		for (Map.Entry<Integer, Integer> e : gave.entrySet())
		{
			traded(partner, e.getKey(), e.getValue());
		}
	}

	/**
	 * We put some of an item in group storage: if it's for a request we took on, keep it for whoever
	 * asked.
	 */
	private void deposited(int itemId, int amount)
	{
		String name = nameOf(itemId);
		for (RequestService.Request request : requests.list())
		{
			RequestData data = request.getData();
			boolean sameReservation = data.getReservedItemId() == 0 || data.getReservedItemId() == itemId;
			boolean stillNeeded = data.getDelivered() + data.getReservedQuantity() < data.asked();
			if (!data.isDone() && takenByMe(data) && sameReservation && stillNeeded && sameItem(data.getItem(), name))
			{
				requests.keep(request.getId(), itemId, amount);
				return;
			}
		}
	}

	/**
	 * We took some of an item out of group storage: if it was kept for us, we've received it.
	 */
	private void withdrawn(int itemId, int amount)
	{
		for (RequestService.Reservation reservation : requests.reservationsFor(itemId))
		{
			if (requests.isMe(reservation.getRequestedBy()))
			{
				requests.receive(reservation.getRequestId(), amount, true);
				return;
			}
		}
	}

	/**
	 * We handed items to someone in a trade: if it's what they asked us for, they've received it.
	 */
	private void traded(String partner, int itemId, int quantity)
	{
		String name = nameOf(itemId);
		for (RequestService.Request request : requests.list())
		{
			RequestData data = request.getData();
			if (!data.isDone() && takenByMe(data) && sameName(partner, data.getRequestedByName()) && sameItem(data.getItem(), name))
			{
				requests.receive(request.getId(), quantity, false);
				return;
			}
		}
	}

	/**
	 * @return true if we took the request on, and it isn't our own
	 */
	private boolean takenByMe(RequestData data)
	{
		return requests.isMe(data.getAssignedTo()) && !requests.isMe(data.getRequestedBy());
	}

	private String nameOf(int itemId)
	{
		return Text.removeTags(itemManager.getItemComposition(itemManager.canonicalize(itemId)).getName());
	}

	/**
	 * @return the name of the player we're trading with, from a trade screen's title, or null if that
	 * screen isn't open
	 */
	private String partnerFrom(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		if (widget == null || widget.isHidden() || widget.getText() == null)
		{
			return null;
		}
		return partnerName(widget.getText());
	}

	/**
	 * @return the player's name from a trade screen title such as "Trading With: Bonscott Kid"
	 */
	static String partnerName(String title)
	{
		String text = Text.removeTags(title.replace("<br>", " "));
		int colon = text.indexOf(':');
		String name = (colon >= 0 ? text.substring(colon + 1) : text).trim();
		return name.isEmpty() ? null : name;
	}

	private boolean visible(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget != null && !widget.isHidden();
	}

	/**
	 * @return true if what was asked for, as typed, is this item
	 */
	static boolean sameItem(String asked, String itemName)
	{
		return asked != null && itemName != null && normalize(asked).equals(normalize(itemName));
	}

	/**
	 * @return true if two player names are the same player, however their spaces are written
	 */
	static boolean sameName(String a, String b)
	{
		return a != null && b != null && Text.toJagexName(a).equalsIgnoreCase(Text.toJagexName(b));
	}

	private static String normalize(String text)
	{
		return text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
