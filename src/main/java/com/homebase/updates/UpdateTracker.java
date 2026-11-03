package com.homebase.updates;

import com.google.gson.Gson;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Checkable;
import com.homebase.data.Clean;
import com.homebase.data.CollectionLogData;
import com.homebase.data.CombatAchievementsData;
import com.homebase.data.DiariesData;
import com.homebase.data.KillCountsData;
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
	private static final long GATHER_MS = 3_000;

	/**
	 * Most summaries kept.
	 */
	private static final int MAX_ENTRIES = 100;

	private static final int MAX_PLAYER_NAME = 20;
	private static final int MAX_QUEST_NAME = 60;
	private static final int MAX_ITEM_NAME = 60;
	private static final int MAX_BOSS_NAME = 40;

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

		List<String> changes = describeChanges(memberId, before, store);
		if (changes.isEmpty())
		{
			return;
		}
		ProfileData profile = decodeCurrent(store, memberId, Section.PROFILE, ProfileData.class);
		String name = profile == null ? null : Clean.text(profile.getName(), MAX_PLAYER_NAME);
		UpdateEntry entry = new UpdateEntry(memberId, name == null ? "Someone" : name, System.currentTimeMillis(), changes, false);
		synchronized (this)
		{
			if (store != this.store)
			{
				// switched group while this was being worked out
				return;
			}
			entries.add(0, entry);
			while (entries.size() > MAX_ENTRIES)
			{
				entries.remove(entries.size() - 1);
			}
		}
		log.debug("Update for {}: {}", entry.getName(), changes);
		for (Consumer<UpdateEntry> listener : listeners)
		{
			listener.accept(entry);
		}
	}

	/**
	 * @return what changed in each section, from the copy we had before the batch to the one in the
	 * store now
	 */
	private List<String> describeChanges(String memberId, Map<String, SyncRecord> before, GroupStore store)
	{
		List<String> changes = new ArrayList<>();
		SyncRecord skillsBefore = before.get(Section.SKILLS.getKey());
		if (skillsBefore != null)
		{
			changes.addAll(UpdateDiff.skills(
				levels(decode(skillsBefore, SkillsData.class)),
				levels(decodeCurrent(store, memberId, Section.SKILLS, SkillsData.class))));
		}
		SyncRecord questsBefore = before.get(Section.QUESTS.getKey());
		if (questsBefore != null)
		{
			changes.addAll(UpdateDiff.quests(
				quests(decode(questsBefore, QuestsData.class)),
				quests(decodeCurrent(store, memberId, Section.QUESTS, QuestsData.class))));
		}
		SyncRecord diariesBefore = before.get(Section.DIARIES.getKey());
		if (diariesBefore != null)
		{
			changes.addAll(UpdateDiff.diaries(
				diaries(decode(diariesBefore, DiariesData.class)),
				diaries(decodeCurrent(store, memberId, Section.DIARIES, DiariesData.class)),
				DiariesData.REGIONS, DiariesData.TIERS));
		}
		SyncRecord caBefore = before.get(Section.COMBAT_ACHIEVEMENTS.getKey());
		if (caBefore != null)
		{
			CombatAchievementsData was = decode(caBefore, CombatAchievementsData.class);
			CombatAchievementsData now = decodeCurrent(store, memberId, Section.COMBAT_ACHIEVEMENTS, CombatAchievementsData.class);
			if (was != null && now != null)
			{
				changes.addAll(UpdateDiff.combatAchievements(was.getTasks(), now.getTasks(), was.getTier(), now.getTier(),
					CombatAchievementsData.TIERS));
			}
		}
		SyncRecord logBefore = before.get(Section.COLLECTION_LOG.getKey());
		if (logBefore != null)
		{
			CollectionLogData was = decode(logBefore, CollectionLogData.class);
			CollectionLogData now = decodeCurrent(store, memberId, Section.COLLECTION_LOG, CollectionLogData.class);
			if (was != null && now != null)
			{
				changes.addAll(UpdateDiff.collectionLog(was.getObtained(), now.getObtained(), itemNames(was.getRecent()),
					itemNames(now.getRecent())));
			}
		}
		SyncRecord killsBefore = before.get(Section.KILL_COUNTS.getKey());
		if (killsBefore != null)
		{
			changes.addAll(UpdateDiff.killCounts(
				kills(decode(killsBefore, KillCountsData.class)),
				kills(decodeCurrent(store, memberId, Section.KILL_COUNTS, KillCountsData.class))));
		}
		return changes;
	}

	private static boolean isTracked(String section)
	{
		return Section.SKILLS.getKey().equals(section) || Section.QUESTS.getKey().equals(section)
			|| Section.DIARIES.getKey().equals(section) || Section.COMBAT_ACHIEVEMENTS.getKey().equals(section)
			|| Section.COLLECTION_LOG.getKey().equals(section) || Section.KILL_COUNTS.getKey().equals(section);
	}

	private <T> T decodeCurrent(GroupStore store, String memberId, Section section, Class<T> type)
	{
		return decode(store.get(section.keyFor(memberId)), type);
	}

	private <T> T decode(SyncRecord record, Class<T> type)
	{
		if (record == null || record.getData() == null)
		{
			return null;
		}
		try
		{
			return Checkable.checked(gson.fromJson(record.getData(), type));
		}
		catch (RuntimeException e)
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
				String name = Clean.text(e.getKey(), MAX_QUEST_NAME);
				if (name != null)
				{
					out.put(name, e.getValue());
				}
			}
		}
		return out;
	}

	/**
	 * Item names written by other players' clients, cleaned before they're shown.
	 */
	private static List<String> itemNames(List<String> raw)
	{
		List<String> out = new ArrayList<>();
		if (raw != null)
		{
			for (String name : raw)
			{
				String clean = Clean.text(name, MAX_ITEM_NAME);
				if (clean != null)
				{
					out.add(clean);
				}
			}
		}
		return out;
	}

	private static Map<String, Integer> kills(KillCountsData kills)
	{
		Map<String, Integer> out = new HashMap<>();
		if (kills != null && kills.getCounts() != null)
		{
			for (Map.Entry<String, Integer> e : kills.getCounts().entrySet())
			{
				String name = Clean.text(e.getKey(), MAX_BOSS_NAME);
				if (name != null && e.getValue() != null)
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
