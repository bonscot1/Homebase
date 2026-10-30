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
}
