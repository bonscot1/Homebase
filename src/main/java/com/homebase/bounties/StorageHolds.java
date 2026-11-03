package com.homebase.bounties;

import com.homebase.requests.RequestService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/**
 * How much of an item in group storage is held back from the logged in player: by bounties on it
 * that they haven't earned yet, and by other members' requests waiting to be collected. Only the
 * amount asked for is held; anything more in storage is free for anyone. Runs on the client thread.
 */
@Singleton
public class StorageHolds
{
	public static final int ALL = BountyService.ALL;

	@Value
	public static class Hold
	{
		/**
		 * How many are held back from the player: 0 for none, {@link #ALL} for every one.
		 */
		int held;

		/**
		 * One line per bounty or request on the item, for its padlock's tooltip.
		 */
		List<String> reasons;

		public boolean any()
		{
			return !reasons.isEmpty();
		}
	}

	private final ItemManager itemManager;
	private final BountyService bounties;
	private final RequestService requests;

	@Inject
	private StorageHolds(ItemManager itemManager, BountyService bounties, RequestService requests)
	{
		this.itemManager = itemManager;
		this.bounties = bounties;
		this.requests = requests;
	}

	public Hold holdFor(int itemId)
	{
		int canonical = itemManager.canonicalize(itemId);
		long held = 0;
		List<String> reasons = new ArrayList<>();

		BountyService.Lock lock = bounties.lockFor(canonical);
		if (lock == null)
		{
			lock = bounties.lockFor(itemId);
		}
		if (lock != null)
		{
			if (lock.isLocked())
			{
				held += lock.getQuantity();
				reasons.add("Bounty: " + (lock.getQuantity() == ALL ? "" : lock.getQuantity() + " ")
					+ "locked until you reach " + lock.getGoal());
			}
			else
			{
				reasons.add("Bounty: " + lock.getGoal() + " (you can take this)");
			}
		}

		List<RequestService.Reservation> reservations = requests.reservationsFor(canonical);
		if (reservations.isEmpty())
		{
			reservations = requests.reservationsFor(itemId);
		}
		for (RequestService.Reservation reservation : reservations)
		{
			if (requests.isMe(reservation.getRequestedBy()))
			{
				reasons.add(reservation.getQuantity() + " kept for you: your request");
			}
			else
			{
				held += reservation.getQuantity();
				reasons.add(reservation.getQuantity() + " kept for "
					+ (reservation.getRequestedByName() == null ? "someone" : reservation.getRequestedByName()) + "'s request");
			}
		}
		return new Hold((int) Math.min(ALL, held), reasons);
	}

	/**
	 * @return how many of a stack anyone may take while {@code held} of it are held back
	 */
	public static int free(int count, int held)
	{
		return held >= ALL ? 0 : Math.max(0, count - held);
	}

	/**
	 * @return how many of a stack of {@code count} this Withdraw option takes, or -1 for Withdraw-X,
	 * whose amount isn't known until it's typed
	 */
	public static int amount(String option, int count)
	{
		String o = Text.removeTags(option).trim().toLowerCase(Locale.ROOT);
		if (o.endsWith("-all-but-1"))
		{
			return Math.max(0, count - 1);
		}
		if (o.endsWith("-all"))
		{
			return count;
		}
		int dash = o.lastIndexOf('-');
		try
		{
			return Math.min(count, Integer.parseInt(o.substring(dash + 1)));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}

	/**
	 * @return true if the Withdraw option may stay: it can't take any of what's held back
	 */
	public static boolean allowed(String option, int count, int held)
	{
		if (held <= 0)
		{
			return true;
		}
		int amount = amount(option, count);
		return amount >= 0 && amount <= free(count, held);
	}
}
