package com.homebase.data;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.homebase.sync.GroupStore;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncRecord;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Typed read access to the group store.
 */
@Slf4j
public class GroupView
{
	private final GroupStore store;
	private final Gson gson;

	public GroupView(GroupStore store, Gson gson)
	{
		this.store = store;
		this.gson = gson;
	}

	/**
	 * @return ids of every member we have a profile for
	 */
	public List<String> memberIds()
	{
		List<String> out = new ArrayList<>();
		for (SyncRecord record : store.withPrefix("m:"))
		{
			if (Section.PROFILE.getKey().equals(Keys.sectionOf(record.getKey())))
			{
				out.add(Keys.memberOf(record.getKey()));
			}
		}
		return out;
	}

	public <T> T get(String memberId, Section section, Class<T> type)
	{
		return decode(store.get(section.keyFor(memberId)), type);
	}

	/**
	 * @return when the data was captured, or 0 if we have none
	 */
	public long updatedAt(String memberId, Section section)
	{
		SyncRecord record = store.get(section.keyFor(memberId));
		return record == null ? 0 : record.getVersion();
	}

	/**
	 * @return who captured group-wide data, such as the last member to open the group storage
	 */
	public String authorOf(String memberId, Section section)
	{
		SyncRecord record = store.get(section.keyFor(memberId));
		return record == null ? null : record.getAuthor();
	}

	private <T> T decode(SyncRecord record, Class<T> type)
	{
		if (record == null)
		{
			return null;
		}
		try
		{
			return gson.fromJson(record.getData(), type);
		}
		catch (JsonParseException e)
		{
			log.debug("Unreadable record {}", record.getKey(), e);
			return null;
		}
	}
}
