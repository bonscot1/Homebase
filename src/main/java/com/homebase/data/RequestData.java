package com.homebase.data;

import lombok.Data;

/**
 * A request for an item from the group, such as asking the group's best smith for a rune scimitar.
 */
@Data
public class RequestData
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
	 * @return true while the item waits in group storage for the requester
	 */
	public boolean isReserved()
	{
		return reservedItemId > 0 && !done;
	}
}
