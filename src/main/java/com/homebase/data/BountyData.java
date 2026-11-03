package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * An item in group storage that some members can't take until they reach a goal. It's a soft lock:
 * the plugin hides the Withdraw options for those members, so it only binds people running it.
 */
@Data
public class BountyData implements Checkable
{
	private static final int MAX_MEMBERS = 100;

	private int itemId;
	private String itemName;

	/**
	 * Id of the goal that lifts the lock.
	 */
	private String goalId;

	/**
	 * Member ids locked out until they reach the goal.
	 */
	private List<String> locked = new ArrayList<>();

	/**
	 * How many are held back; anything more in group storage is free for anyone. 0 means all of
	 * them, as bounties made before amounts existed did.
	 */
	private int quantity;

	private String createdBy;
	private String createdByName;
	private long createdAt;

	@Override
	public void check()
	{
		locked = Checkable.list(locked, MAX_MEMBERS);
		quantity = Math.max(0, quantity);
	}
}
