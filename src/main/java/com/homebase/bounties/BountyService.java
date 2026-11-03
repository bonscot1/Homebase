package com.homebase.bounties;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.homebase.collect.LocalCollector;
import com.homebase.data.BountyData;
import com.homebase.data.Clean;
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
import java.util.UUID;
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
	}

	private final LocalCollector collector;
	private final GoalService goals;
	private final Gson gson;

	private volatile SyncEngine engine;
	private volatile boolean dirty = true;
	private volatile Map<Integer, Lock> locks = Collections.emptyMap();

	@Inject
	private BountyService(LocalCollector collector, GoalService goals, Gson gson)
	{
		this.collector = collector;
		this.goals = goals;
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

	public boolean canEdit()
	{
		return goals.canEdit();
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

	public void create(int itemId, String itemName, String goalId, List<String> locked)
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
		data.setLocked(new ArrayList<>(locked));
		data.setCreatedBy(me);
		data.setCreatedByName(myName);
		data.setCreatedAt(System.currentTimeMillis());
		String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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
			GoalService.Goal goal = goals.get(bounty.getData().getGoalId());
			String title = goal == null ? "a deleted goal" : GoalService.title(goal.getData());
			boolean locked = me != null && isLocked(bounty.getData(), me);
			Lock existing = out.get(bounty.getData().getItemId());
			// several bounties on one item: any one still holding locks it
			if (existing == null || (locked && !existing.isLocked()))
			{
				out.put(bounty.getData().getItemId(), new Lock(title, locked));
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
			BountyData data = gson.fromJson(record.getData(), BountyData.class);
			if (data == null || data.getItemId() <= 0)
			{
				return null;
			}
			data.setItemName(Clean.text(data.getItemName(), MAX_ITEM_NAME));
			data.setCreatedByName(Clean.text(data.getCreatedByName(), MAX_NAME));
			return data;
		}
		catch (JsonParseException e)
		{
			log.debug("Unreadable bounty {}", record.getKey(), e);
			return null;
		}
	}
}
