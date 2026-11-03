package com.homebase.data;

import lombok.Data;

/**
 * A request for an item from the group, such as asking the group's best smith for a rune scimitar.
 */
@Data
public class RequestData implements Checkable
{
	private String item;
	private int quantity;
	private String note;

	/**
	 * Member id and name of whoever asked.
	 */
	private String requestedBy;
	private String requestedByName;

	/**
	 * Member id and name of whoever has taken it on, or null while nobody has.
	 */
	private String assignedTo;
	private String assignedToName;

	private boolean done;
	private long createdAt;

	/**
	 * The item the member who took the request on put into group storage, kept for the requester
	 * until they take it out; 0 while nothing has been put in.
	 */
	private int reservedItemId;

	/**
	 * How many of that item wait in group storage for the requester.
	 */
	private int reservedQuantity;

	/**
	 * How many the requester has received so far, out of group storage or by trade.
	 */
	private int delivered;

	/**
	 * @return how many were asked for, at least one
	 */
	public int asked()
	{
		return Math.max(1, quantity);
	}

	/**
	 * @return true while some of the item waits in group storage for the requester
	 */
	public boolean isReserved()
	{
		return reservedItemId > 0 && reservedQuantity > 0 && !done;
	}

	/**
	 * Some of the item went into group storage for the requester: keep it for them, up to what they
	 * still need. Anything more is free for anyone.
	 */
	public void keep(int itemId, int amount)
	{
		reservedItemId = itemId;
		reservedQuantity = Math.max(0, Math.min(asked() - delivered, reservedQuantity + amount));
	}

	/**
	 * The member who put the item in group storage took some of it back out, so that much is no longer
	 * waiting there.
	 */
	public void takeBack(int amount)
	{
		reservedQuantity = Math.max(0, reservedQuantity - amount);
	}

	/**
	 * The requester got some of the item, out of group storage or by trade. Once they have all they
	 * asked for, the request is done.
	 */
	public void receive(int amount, boolean fromStorage)
	{
		int got = fromStorage ? Math.min(amount, reservedQuantity) : amount;
		if (fromStorage)
		{
			reservedQuantity -= got;
		}
		delivered += Math.max(0, got);
		if (delivered >= asked())
		{
			done = true;
			reservedQuantity = 0;
		}
	}

	@Override
	public void check()
	{
		reservedQuantity = Math.max(0, reservedQuantity);
		delivered = Math.max(0, delivered);
	}
}
