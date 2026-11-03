package com.homebase.requests;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Checkable;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.data.RequestData;
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
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and updates item requests. Requests are shared records, so any member can change them and
 * the newest change wins.
 */
@Slf4j
@Singleton
public class RequestService
{
	public static final String TYPE = "request";

	private static final int MAX_ITEM = 60;
	private static final int MAX_NOTE = 140;
	private static final int MAX_NAME = 20;

	@Value
	public static class Request
	{
		String id;
		RequestData data;

		/**
		 * When the request was last changed.
		 */
		long updatedAt;
	}

	/**
	 * An item waiting in group storage for the member who asked for it.
	 */
	@Value
	public static class Reservation
	{
		String requestId;
		String item;
		String requestedBy;
		String requestedByName;

		/**
		 * How many were asked for; anything more in group storage is free for anyone.
		 */
		int quantity;
	}

	private final LocalCollector collector;
	private final GroupIndex groupIndex;
	private final Gson gson;

	private volatile SyncEngine engine;
	private volatile boolean dirty = true;
	private volatile Map<Integer, List<Reservation>> reservations = Collections.emptyMap();

	@Inject
	private RequestService(LocalCollector collector, GroupIndex groupIndex, Gson gson)
	{
		this.collector = collector;
		this.groupIndex = groupIndex;
		this.gson = gson;
	}

	public void setEngine(SyncEngine engine)
	{
		this.engine = engine;
		dirty = true;
	}

	/**
	 * Group data changed, which may reserve or release items.
	 */
	public void invalidate()
	{
		dirty = true;
	}

	/**
	 * @return the requests an item in group storage is kept for, if any. Cheap enough to call for
	 * every menu entry and every frame.
	 */
	public List<Reservation> reservationsFor(int itemId)
	{
		if (dirty)
		{
			dirty = false;
			reservations = buildReservations();
		}
		return reservations.getOrDefault(itemId, Collections.emptyList());
	}

	private Map<Integer, List<Reservation>> buildReservations()
	{
		Map<Integer, List<Reservation>> out = new HashMap<>();
		for (Request request : list())
		{
			RequestData data = request.getData();
			// only kept for, and by, people in the group
			if (data.isReserved() && groupIndex.isMember(data.getRequestedBy())
				&& (data.getAssignedTo() == null || groupIndex.isMember(data.getAssignedTo())))
			{
				out.computeIfAbsent(data.getReservedItemId(), id -> new ArrayList<>()).add(new Reservation(request.getId(),
					data.getItem(), data.getRequestedBy(), data.getRequestedByName(), data.getReservedQuantity()));
			}
		}
		return out;
	}

	/**
	 * The member who took the request on put the item in group storage: keep it for the requester.
	 */
	public void keep(String id, int itemId, int amount)
	{
		update(id, data -> data.keep(itemId, amount));
	}

	/**
	 * The requester got some of what they asked for, out of group storage or by trade.
	 */
	public void receive(String id, int amount, boolean fromStorage)
	{
		update(id, data -> data.receive(amount, fromStorage));
	}

	/**
	 * Requests can only be changed while logged in, since every change is signed with who made it.
	 */
	public boolean canEdit()
	{
		return engine != null && collector.getMemberId() != null && collector.getLocalName() != null;
	}

	/**
	 * @return true if the member id is the logged in player
	 */
	public boolean isMe(String memberId)
	{
		return memberId != null && memberId.equals(collector.getMemberId());
	}

	/**
	 * @return open requests newest first, then finished ones newest first
	 */
	public List<Request> list()
	{
		SyncEngine engine = this.engine;
		List<Request> out = new ArrayList<>();
		if (engine == null)
		{
			return out;
		}
		String prefix = Keys.sharedPrefix(TYPE);
		for (SyncRecord record : engine.getStore().withPrefix(prefix))
		{
			RequestData data = decode(record);
			if (data != null && !Strings.isNullOrEmpty(data.getItem()))
			{
				out.add(new Request(record.getKey().substring(prefix.length()), data, record.getVersion()));
			}
		}
		out.sort(Comparator
			.comparing((Request r) -> r.getData().isDone())
			.thenComparing(Comparator.comparingLong((Request r) -> r.getData().getCreatedAt()).reversed()));
		return out;
	}

	public void create(String item, int quantity, String note)
	{
		SyncEngine engine = this.engine;
		String me = collector.getMemberId();
		String myName = collector.getLocalName();
		if (engine == null || me == null || myName == null || Strings.isNullOrEmpty(item))
		{
			return;
		}
		String cleanItem = Clean.text(item, MAX_ITEM);
		if (cleanItem == null)
		{
			return;
		}
		RequestData data = new RequestData();
		data.setItem(cleanItem);
		data.setQuantity(Math.max(1, quantity));
		data.setNote(Clean.text(note, MAX_NOTE));
		data.setRequestedBy(me);
		data.setRequestedByName(myName);
		data.setCreatedAt(System.currentTimeMillis());
		String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		engine.publish(Keys.shared(TYPE, id), me, gson.toJsonTree(data));
	}

	/**
	 * Take a request on.
	 */
	public void claim(String id)
	{
		update(id, data ->
		{
			data.setAssignedTo(collector.getMemberId());
			data.setAssignedToName(collector.getLocalName());
		});
	}

	/**
	 * Give a request back so someone else can take it.
	 */
	public void unclaim(String id)
	{
		update(id, data ->
		{
			data.setAssignedTo(null);
			data.setAssignedToName(null);
			data.setReservedItemId(0);
			data.setReservedQuantity(0);
		});
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
	 * Describe a change someone else made, for the chat box, or null if it isn't worth a message.
	 */
	public String describeChange(SyncRecord record)
	{
		if (record.isDeleted() || !record.getKey().startsWith(Keys.sharedPrefix(TYPE)))
		{
			return null;
		}
		RequestData data = decode(record);
		if (data == null || Strings.isNullOrEmpty(data.getItem()))
		{
			return null;
		}
		String what = data.getQuantity() > 1 ? data.getQuantity() + " x " + data.getItem() : data.getItem();
		if (data.isReserved())
		{
			String kept = data.getReservedQuantity() > 1 ? data.getReservedQuantity() + " x " + data.getItem() : data.getItem();
			return name(data.getAssignedToName()) + " put " + kept + " in group storage for " + name(data.getRequestedByName()) + ".";
		}
		if (data.isDone())
		{
			return name(data.getAssignedToName()) + " finished " + what + " for " + name(data.getRequestedByName()) + ".";
		}
		if (data.getAssignedTo() != null)
		{
			return name(data.getAssignedToName()) + " is getting " + what + " for " + name(data.getRequestedByName()) + ".";
		}
		return name(data.getRequestedByName()) + " requested " + what + ".";
	}

	private void update(String id, Consumer<RequestData> change)
	{
		SyncEngine engine = this.engine;
		String me = collector.getMemberId();
		if (engine == null || me == null)
		{
			return;
		}
		String key = Keys.shared(TYPE, id);
		RequestData data = decode(engine.getStore().get(key));
		if (data == null)
		{
			return;
		}
		change.accept(data);
		engine.publish(key, me, gson.toJsonTree(data));
	}

	private RequestData decode(SyncRecord record)
	{
		if (record == null || record.getData() == null)
		{
			return null;
		}
		try
		{
			RequestData data = Checkable.checked(gson.fromJson(record.getData(), RequestData.class));
			if (data != null)
			{
				// written by other players' clients, so never trust it for display
				data.setItem(Clean.text(data.getItem(), MAX_ITEM));
				data.setNote(Clean.text(data.getNote(), MAX_NOTE));
				data.setRequestedByName(Clean.text(data.getRequestedByName(), MAX_NAME));
				data.setAssignedToName(Clean.text(data.getAssignedToName(), MAX_NAME));
			}
			return data;
		}
		catch (RuntimeException e)
		{
			log.debug("Unreadable request {}", record.getKey(), e);
			return null;
		}
	}

	private static String name(String name)
	{
		return Strings.isNullOrEmpty(name) ? "Someone" : name;
	}
}
