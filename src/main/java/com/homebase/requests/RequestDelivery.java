package com.homebase.requests;

import com.homebase.data.RequestData;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
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
 * <li>trading it to the member who asked, which finishes the request when the trade is accepted.</li>
 * </ul>
 */
@Singleton
public class RequestDelivery
{
	private static final String TRADE_ACCEPTED = "Accepted trade.";

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

	@Inject
	private RequestDelivery(Client client, ItemManager itemManager, RequestService requests)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.requests = requests;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.TRADEOFFER)
		{
			offered = counts(event.getItemContainer());
			return;
		}
		if (event.getContainerId() != InventoryID.INV_GROUP_TEMP)
		{
			return;
		}
		Map<Integer, Integer> counts = counts(event.getItemContainer());
		Map<Integer, Integer> before = lastCounts;
		lastCounts = counts;
		// the first look after opening is only where we start from
		if (before == null || !requests.canEdit())
		{
			return;
		}
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			if (e.getValue() > before.getOrDefault(e.getKey(), 0))
			{
				deposited(e.getKey());
			}
		}
		for (Map.Entry<Integer, Integer> e : before.entrySet())
		{
			if (counts.getOrDefault(e.getKey(), 0) < e.getValue())
			{
				withdrawn(e.getKey());
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
		else
		{
			tradeOpen = false;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (tradePartner == null || !TRADE_ACCEPTED.equals(Text.removeTags(event.getMessage()).trim()))
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

	private void deposited(int itemId)
	{
		String name = nameOf(itemId);
		for (RequestService.Request request : requests.list())
		{
			RequestData data = request.getData();
			if (!data.isDone() && data.getReservedItemId() == 0 && takenByMe(data) && sameItem(data.getItem(), name))
			{
				requests.reserve(request.getId(), itemId);
				return;
			}
		}
	}

	private void withdrawn(int itemId)
	{
		for (RequestService.Reservation reservation : requests.reservationsFor(itemId))
		{
			if (requests.isMe(reservation.getRequestedBy()))
			{
				requests.complete(reservation.getRequestId());
				return;
			}
		}
	}

	/**
	 * We handed items to someone in a trade: if it's what they asked us for, in full, it's done.
	 */
	private void traded(String partner, int itemId, int quantity)
	{
		String name = nameOf(itemId);
		for (RequestService.Request request : requests.list())
		{
			RequestData data = request.getData();
			if (!data.isDone() && takenByMe(data) && sameName(partner, data.getRequestedByName())
				&& sameItem(data.getItem(), name) && quantity >= Math.max(1, data.getQuantity()))
			{
				requests.complete(request.getId());
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
