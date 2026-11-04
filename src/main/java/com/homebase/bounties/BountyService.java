package com.homebase.bounties;

import com.google.gson.Gson;
import com.homebase.collect.LocalCollector;
import com.homebase.data.BountyData;
import com.homebase.data.Checkable;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.data.Ids;
import com.homebase.goals.GoalService;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Bank bounties: items in group storage that some members can't take until they reach a goal. The
 * lock is soft, since all the plugin can do is hide the Withdraw options for locked members.
 */
@Slf4j
@Singleton
public class BountyService
{
	public static final String TYPE = "bounty";

	/**
	 * A bounty or request amount meaning the whole stack.
	 */
	public static final int ALL = Integer.MAX_VALUE;

	private static final int MAX_ITEM_NAME = 60;
	private static final int MAX_NAME = 20;

	@Value
	public static class Bounty
	{
		String id;
		BountyData data;
	}

	/**
	 * How an item in group storage stands for the logged in player.
	 */
	@Value
	public static class Lock
	{
		/**
		 * The goal that lifts it, such as "70 Attack".
		 */
		String goal;

		/**
		 * True if the player can't withdraw the item yet.
		 */
		boolean locked;

		/**
		 * How many are held back from the player while locked; {@link BountyService#ALL} for every one.
		 */
		int quantity;
	}

	private final LocalCollector collector;
	private final GoalService goals;
	private final GroupIndex groupIndex;
	private final Gson gson;

	private volatile SyncEngine engine;
	private volatile boolean dirty = true;
	private volatile Map<Integer, Lock> locks = Collections.emptyMap();

	@Inject
	private BountyService(LocalCollector collector, GoalService goals, GroupIndex groupIndex, Gson gson)
	{
		this.collector = collector;
		this.goals = goals;
		this.groupIndex = groupIndex;
		this.gson = gson;
	}

	public void setEngine(SyncEngine engine)
	{
		this.engine = engine;
		dirty = true;
	}

	/**
	 * Group data changed, which may lock or unlock items.
	 */
	public void invalidate()
	{
		dirty = true;
	}

	public List<Bounty> list()
	{
		SyncEngine engine = this.engine;
		List<Bounty> out = new ArrayList<>();
		if (engine == null)
		{
			return out;
		}
		String prefix = Keys.sharedPrefix(TYPE);
		for (SyncRecord record : engine.getStore().withPrefix(prefix))
		{
			BountyData data = decode(record);
			if (data != null)
			{
				out.add(new Bounty(record.getKey().substring(prefix.length()), data));
			}
		}
		out.sort(Comparator.comparingLong((Bounty b) -> b.getData().getCreatedAt()));
		return out;
	}

	public void create(int itemId, String itemName, int quantity, String goalId, List<String> locked)
	{
		SyncEngine engine = this.engine;
		String me = collector.getMemberId();
		String myName = collector.getLocalName();
		if (engine == null || me == null || myName == null || itemId <= 0 || goalId == null || locked == null || locked.isEmpty())
		{
			return;
		}
		BountyData data = new BountyData();
		data.setItemId(itemId);
		data.setItemName(Clean.text(itemName, MAX_ITEM_NAME));
		data.setGoalId(goalId);
		data.setQuantity(Math.max(0, quantity));
		data.setLocked(new ArrayList<>(locked));
		data.setCreatedBy(me);
		data.setCreatedByName(myName);
		data.setCreatedAt(System.currentTimeMillis());
		String id = Ids.random();
		engine.publish(Keys.shared(TYPE, id), me, gson.toJsonTree(data));
	}

	public void delete(String id)
	{
		SyncEngine engine = this.engine;
		String me = collector.getMemberId();
		if (engine != null && me != null)
		{
			engine.delete(Keys.shared(TYPE, id), me);
		}
	}

	/**
	 * @return how the item stands for the logged in player, or null if there's no bounty on it.
	 * Cheap enough to call for every menu entry and every frame.
	 */
	public Lock lockFor(int itemId)
	{
		if (dirty)
		{
			dirty = false;
			locks = buildLocks();
		}
		return locks.get(itemId);
	}

	/**
	 * True if the bounty still holds for the member: they're on its list and haven't reached its goal.
	 */
	public boolean isLocked(BountyData bounty, String memberId)
	{
		if (bounty.getLocked() == null || !bounty.getLocked().contains(memberId))
		{
			return false;
		}
		GoalService.Goal goal = goals.get(bounty.getGoalId());
		// a deleted goal can never be reached, so its bounty no longer holds
		return goal != null && !goals.progress(goal.getData(), memberId).isDone();
	}

	private Map<Integer, Lock> buildLocks()
	{
		String me = collector.getMemberId();
		Map<Integer, Lock> out = new HashMap<>();
		for (Bounty bounty : list())
		{
			BountyData data = bounty.getData();
			if (!groupIndex.isMember(data.getCreatedBy()))
			{
				// only bounties set by someone in the group hold anything back
				continue;
			}
			GoalService.Goal goal = goals.get(data.getGoalId());
			String title = goal == null ? "a deleted goal" : GoalService.title(goal.getData());
			boolean locked = me != null && isLocked(data, me);
			int quantity = 0;
			if (locked)
			{
				quantity = data.getQuantity() > 0 ? data.getQuantity() : ALL;
			}
			Lock existing = out.get(data.getItemId());
			if (existing == null)
			{
				out.put(data.getItemId(), new Lock(title, locked, quantity));
			}
			else if (locked)
			{
				// several bounties on one item hold back their amounts together
				long total = (long) existing.getQuantity() + quantity;
				String goalTitle = existing.isLocked() ? existing.getGoal() : title;
				out.put(data.getItemId(), new Lock(goalTitle, true, (int) Math.min(ALL, total)));
			}
		}
		return out;
	}

	private BountyData decode(SyncRecord record)
	{
		if (record == null || record.getData() == null)
		{
			return null;
		}
		try
		{
			BountyData data = Checkable.checked(gson.fromJson(record.getData(), BountyData.class));
			if (data == null || data.getItemId() <= 0)
			{
				return null;
			}
			data.setItemName(Clean.text(data.getItemName(), MAX_ITEM_NAME));
			data.setCreatedByName(Clean.text(data.getCreatedByName(), MAX_NAME));
			return data;
		}
		catch (RuntimeException e)
		{
			log.debug("Unreadable bounty {}", record.getKey(), e);
			return null;
		}
	}
}
