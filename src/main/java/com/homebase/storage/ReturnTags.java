package com.homebase.storage;

import com.google.gson.JsonObject;
import com.homebase.collect.LocalCollector;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Items in group storage marked as needing to be returned. Storage is mostly for giving each other
 * things, so taking something out only leaves you owing it if it's been marked as belonging there.
 */
@Singleton
public class ReturnTags
{
	private static final String TYPE = "return";

	private final LocalCollector collector;
	private volatile SyncEngine engine;

	@Inject
	private ReturnTags(LocalCollector collector)
	{
		this.collector = collector;
	}

	public void setEngine(SyncEngine engine)
	{
		this.engine = engine;
	}

	public boolean canEdit()
	{
		return engine != null && collector.getMemberId() != null;
	}

	/**
	 * @return each marked item, and when it was marked
	 */
	public Map<Integer, Long> marked()
	{
		SyncEngine engine = this.engine;
		Map<Integer, Long> out = new HashMap<>();
		if (engine == null)
		{
			return out;
		}
		String prefix = Keys.sharedPrefix(TYPE);
		for (SyncRecord record : engine.getStore().withPrefix(prefix))
		{
			int item = itemOf(record.getKey().substring(prefix.length()));
			if (item > 0)
			{
				out.put(item, record.getVersion());
			}
		}
		return out;
	}

	public void mark(int item)
	{
		SyncEngine engine = this.engine;
		String me = collector.getMemberId();
		if (engine != null && me != null && item > 0)
		{
			JsonObject data = new JsonObject();
			data.addProperty("item", item);
			engine.publish(Keys.shared(TYPE, String.valueOf(item)), me, data);
		}
	}

	public void unmark(int item)
	{
		SyncEngine engine = this.engine;
		String me = collector.getMemberId();
		if (engine != null && me != null && item > 0)
		{
			engine.delete(Keys.shared(TYPE, String.valueOf(item)), me);
		}
	}

	/**
	 * @return the item a key's id names, or 0 if another member's client sent something that isn't one
	 */
	static int itemOf(String id)
	{
		try
		{
			return Math.max(0, Integer.parseInt(id));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
