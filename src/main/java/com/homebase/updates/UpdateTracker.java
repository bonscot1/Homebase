package com.homebase.updates;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Clean;
import com.homebase.data.DiariesData;
import com.homebase.data.ProfileData;
import com.homebase.data.QuestsData;
import com.homebase.data.Section;
import com.homebase.data.SkillsData;
import com.homebase.data.UpdateEntry;
import com.homebase.sync.GroupStore;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Turns other members' new data into short summaries of what changed since the copy we had, such as
 * "bonscott1: Magic 15 → 32, completed The Restless Ghost, completed the Varrock Easy diary".
 * <p>
 * Changes for a member are gathered for a few seconds before summarising, so catching up on everything
 * that happened while you were away comes out as one line per member.
 */
@Slf4j
@Singleton
public class UpdateTracker
{
	static final long GATHER_MS = 3_000;
	static final int MAX_ENTRIES = 100;

	private final ScheduledExecutorService executor;
	private final LocalCollector collector;
	private final Gson gson;

	/**
	 * Newest first.
	 */
	private final List<UpdateEntry> entries = new CopyOnWriteArrayList<>();
	private final Map<String, Pending> pending = new HashMap<>();
	private final List<Consumer<UpdateEntry>> listeners = new CopyOnWriteArrayList<>();

	private volatile GroupStore store;

	/**
	 * A member's records as they were before the first change in the current batch, and the batch timer.
	 */
	private static final class Pending
	{
		final Map<String, SyncRecord> before = new HashMap<>();
		ScheduledFuture<?> timer;
	}

	@Inject
	private UpdateTracker(ScheduledExecutorService executor, LocalCollector collector, Gson gson)
	{
		this.executor = executor;
		this.collector = collector;
		this.gson = gson;
	}

	public void addListener(Consumer<UpdateEntry> listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Consumer<UpdateEntry> listener)
	{
		listeners.remove(listener);
	}

	/**
	 * Switch to a group's store, with the summaries saved for it.
	 */
	public synchronized void setGroup(GroupStore store, List<UpdateEntry> saved)
	{
		this.store = store;
		for (Pending p : pending.values())
		{
			if (p.timer != null)
			{
				p.timer.cancel(false);
			}
		}
		pending.clear();
		entries.clear();
		if (saved != null)
		{
			for (UpdateEntry entry : saved)
			{
				// summaries from earlier sessions were already seen
				entry.setAnnounced(true);
				entries.add(entry);
			}
		}
	}

	/**
	 * @return summaries, newest first
	 */
	public List<UpdateEntry> getEntries()
	{
		return Collections.unmodifiableList(entries);
	}

	/**
	 * @return summaries not yet shown in chat, oldest first, now marked as shown
	 */
	public List<UpdateEntry> takeUnannounced()
	{
		List<UpdateEntry> out = new ArrayList<>();
		for (UpdateEntry entry : entries)
		{
			if (!entry.isAnnounced())
			{
				entry.setAnnounced(true);
				out.add(0, entry);
			}
		}
		return out;
	}

	/**
	 * A record was stored. Called from the group store, on any thread.
	 */
	public void onRecordChanged(SyncRecord previous, SyncRecord current)
	{
		if (previous == null || previous.isDeleted() || current.isDeleted())
		{
			// the first copy we see of someone has nothing to compare against
			return;
		}
		String memberId = Keys.memberOf(current.getKey());
		String section = Keys.sectionOf(current.getKey());
		if (memberId == null || memberId.equals(collector.getMemberId()) || !isTracked(section))
		{
			return;
		}

		synchronized (this)
		{
			Pending p = pending.computeIfAbsent(memberId, k -> new Pending());
			p.before.putIfAbsent(section, previous);
			if (p.timer != null)
			{
				p.timer.cancel(false);
			}
			p.timer = executor.schedule(() -> summarise(memberId), GATHER_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void summarise(String memberId)
	{
		Map<String, SyncRecord> before;
		GroupStore store;
		synchronized (this)
		{
			Pending p = pending.remove(memberId);
			store = this.store;
			if (p == null || store == null)
			{
				return;
			}
			before = p.before;
		}

		List<String> changes = new ArrayList<>();
		SyncRecord skillsBefore = before.get(Section.SKILLS.getKey());
		if (skillsBefore != null)
		{
			changes.addAll(UpdateDiff.skills(
				levels(decode(skillsBefore, SkillsData.class)),
				levels(decode(store.get(Section.SKILLS.keyFor(memberId)), SkillsData.class))));
		}
		SyncRecord questsBefore = before.get(Section.QUESTS.getKey());
		if (questsBefore != null)
		{
			changes.addAll(UpdateDiff.quests(
				quests(decode(questsBefore, QuestsData.class)),
				quests(decode(store.get(Section.QUESTS.keyFor(memberId)), QuestsData.class))));
		}
		SyncRecord diariesBefore = before.get(Section.DIARIES.getKey());
		if (diariesBefore != null)
		{
			changes.addAll(UpdateDiff.diaries(
				diaries(decode(diariesBefore, DiariesData.class)),
				diaries(decode(store.get(Section.DIARIES.keyFor(memberId)), DiariesData.class)),
				DiariesData.REGIONS, DiariesData.TIERS));
		}
		if (changes.isEmpty())
		{
			return;
		}

		ProfileData profile = decode(store.get(Section.PROFILE.keyFor(memberId)), ProfileData.class);
		String name = profile == null ? null : Clean.text(profile.getName(), 20);
		UpdateEntry entry = new UpdateEntry(memberId, name == null ? "Someone" : name, System.currentTimeMillis(), changes, false);
		entries.add(0, entry);
		while (entries.size() > MAX_ENTRIES)
		{
			entries.remove(entries.size() - 1);
		}
		log.debug("Update for {}: {}", entry.getName(), changes);
		for (Consumer<UpdateEntry> listener : listeners)
		{
			listener.accept(entry);
		}
	}

	private static boolean isTracked(String section)
	{
		return Section.SKILLS.getKey().equals(section) || Section.QUESTS.getKey().equals(section)
			|| Section.DIARIES.getKey().equals(section);
	}

	private <T> T decode(SyncRecord record, Class<T> type)
	{
		if (record == null || record.getData() == null)
		{
			return null;
		}
		try
		{
			return gson.fromJson(record.getData(), type);
		}
		catch (JsonParseException e)
		{
			return null;
		}
	}

	private static Map<String, Integer> levels(SkillsData skills)
	{
		Map<String, Integer> out = new LinkedHashMap<>();
		if (skills != null && skills.getXp() != null)
		{
			for (Skill skill : Skill.values())
			{
				if (skills.getXp().containsKey(skill.name()))
				{
					out.put(skill.getName(), skills.getLevel(skill));
				}
			}
		}
		return out;
	}

	private static Map<String, String> quests(QuestsData quests)
	{
		Map<String, String> out = new HashMap<>();
		if (quests != null && quests.getQuests() != null)
		{
			for (Map.Entry<String, String> e : quests.getQuests().entrySet())
			{
				String name = Clean.text(e.getKey(), 60);
				if (name != null)
				{
					out.put(name, e.getValue());
				}
			}
		}
		return out;
	}

	private static Map<String, Integer> diaries(DiariesData diaries)
	{
		return diaries == null || diaries.getDone() == null ? Collections.emptyMap() : diaries.getDone();
	}
}
