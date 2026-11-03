package com.homebase.goals;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Checkable;
import com.homebase.data.Clean;
import com.homebase.data.CollectionLogData;
import com.homebase.data.CombatAchievementsData;
import com.homebase.data.DiariesData;
import com.homebase.data.GoalData;
import com.homebase.data.GroupView;
import com.homebase.data.KillCountsData;
import com.homebase.data.ProfileData;
import com.homebase.data.QuestsData;
import com.homebase.data.Section;
import com.homebase.data.SkillsData;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * Group goals, such as "50 Firemaking" for everyone before Wintertodt. Goals are shared records any
 * member can create or delete, and each member's progress is worked out from the data they share, so
 * goals tick off on their own.
 */
@Slf4j
@Singleton
public class GoalService
{
	public static final String TYPE = "goal";

	private static final int MAX_TARGET = 60;
	private static final int MAX_NAME = 20;

	@Value
	public static class Goal
	{
		String id;
		GoalData data;
		long updatedAt;
	}

	@Value
	@AllArgsConstructor
	public static class Progress
	{
		boolean done;

		/**
		 * Such as "45/50", or "?" when the member hasn't shared the data yet.
		 */
		String text;

		/**
		 * How far along, for goals counted in numbers such as levels; 0 of 0 for the rest.
		 */
		int have;
		int need;

		Progress(boolean done, String text)
		{
			this(done, text, 0, 0);
		}
	}

	@Value
	public static class Member
	{
		String id;
		String name;
	}

	private final LocalCollector collector;
	private final Gson gson;

	private volatile SyncEngine engine;
	private volatile GroupView view;
	private volatile Predicate<ProfileData> belongs = p -> true;

	/**
	 * "goalId|memberId" for every goal we've seen each member complete, or null before the first check.
	 */
	private Set<String> completed;
	private Set<String> knownGoals = new HashSet<>();

	@Inject
	private GoalService(LocalCollector collector, Gson gson)
	{
		this.collector = collector;
		this.gson = gson;
	}

	public synchronized void setGroup(SyncEngine engine, GroupView view, Predicate<ProfileData> belongs)
	{
		this.engine = engine;
		this.view = view;
		this.belongs = belongs == null ? p -> true : belongs;
		completed = null;
		knownGoals = new HashSet<>();
	}

	public boolean canEdit()
	{
		return engine != null && collector.getMemberId() != null && collector.getLocalName() != null;
	}

	/**
	 * @return goals, oldest first
	 */
	public List<Goal> list()
	{
		SyncEngine engine = this.engine;
		List<Goal> out = new ArrayList<>();
		if (engine == null)
		{
			return out;
		}
		String prefix = Keys.sharedPrefix(TYPE);
		for (SyncRecord record : engine.getStore().withPrefix(prefix))
		{
			GoalData data = decode(record);
			if (data != null)
			{
				out.add(new Goal(record.getKey().substring(prefix.length()), data, record.getVersion()));
			}
		}
		out.sort(Comparator.comparingLong((Goal g) -> g.getData().getCreatedAt()));
		return out;
	}

	public Goal get(String id)
	{
		SyncEngine engine = this.engine;
		if (engine == null || id == null)
		{
			return null;
		}
		SyncRecord record = engine.getStore().get(Keys.shared(TYPE, id));
		GoalData data = decode(record);
		return data == null ? null : new Goal(id, data, record.getVersion());
	}

	/**
	 * @return the new goal's id, or null if it couldn't be created
	 */
	public String create(GoalData.Type type, String target, int amount, List<String> members)
	{
		SyncEngine engine = this.engine;
		String me = collector.getMemberId();
		String myName = collector.getLocalName();
		if (engine == null || me == null || myName == null || type == null)
		{
			return null;
		}
		GoalData data = new GoalData();
		data.setType(type.name());
		data.setTarget(Clean.text(target, MAX_TARGET));
		data.setAmount(Math.max(0, amount));
		data.setMembers(members == null ? new ArrayList<>() : new ArrayList<>(members));
		data.setCreatedBy(me);
		data.setCreatedByName(myName);
		data.setCreatedAt(System.currentTimeMillis());
		if (!isValid(data))
		{
			return null;
		}
		String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		engine.publish(Keys.shared(TYPE, id), me, gson.toJsonTree(data));
		return id;
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
	 * @return the group's members, by name
	 */
	public List<Member> members()
	{
		GroupView view = this.view;
		List<Member> out = new ArrayList<>();
		if (view == null)
		{
			return out;
		}
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			String name = profile == null ? null : Clean.text(profile.getName(), MAX_NAME);
			if (name != null && belongs.test(profile))
			{
				out.add(new Member(id, name));
			}
		}
		out.sort(Comparator.comparing(Member::getName, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	/**
	 * How far a member is towards a goal, worked out from what they've shared.
	 */
	public Progress progress(GoalData goal, String memberId)
	{
		GroupView view = this.view;
		GoalData.Type type = goal.typeOrNull();
		if (view == null || type == null)
		{
			return new Progress(false, "?");
		}
		switch (type)
		{
			case SKILL:
			{
				Skill skill = skill(goal.getTarget());
				SkillsData skills = view.get(memberId, Section.SKILLS, SkillsData.class);
				if (skill == null || skills == null || skills.getXp() == null || skills.getXp().isEmpty())
				{
					return new Progress(false, "?");
				}
				return amount(skills.getLevel(skill), goal.getAmount());
			}
			case TOTAL_LEVEL:
			{
				SkillsData skills = view.get(memberId, Section.SKILLS, SkillsData.class);
				if (skills == null || skills.getXp() == null || skills.getXp().isEmpty())
				{
					return new Progress(false, "?");
				}
				return amount(skills.getTotalLevel(), goal.getAmount());
			}
			case QUEST:
			{
				QuestsData quests = view.get(memberId, Section.QUESTS, QuestsData.class);
				if (quests == null || quests.getQuests() == null)
				{
					return new Progress(false, "?");
				}
				String state = lookup(quests.getQuests(), goal.getTarget());
				if (QuestsData.FINISHED.equals(state))
				{
					return new Progress(true, "done");
				}
				return new Progress(false, QuestsData.STARTED.equals(state) ? "started" : "not started");
			}
			case DIARY:
			{
				DiariesData diaries = view.get(memberId, Section.DIARIES, DiariesData.class);
				int[] diary = diary(goal.getTarget());
				if (diaries == null || diaries.getDone() == null || diary == null)
				{
					return new Progress(false, "?");
				}
				boolean done = diaries.isDone(DiariesData.REGIONS.get(diary[0]), diary[1]);
				return new Progress(done, done ? "done" : "not done");
			}
			case KILL_COUNT:
			{
				KillCountsData kills = view.get(memberId, Section.KILL_COUNTS, KillCountsData.class);
				if (kills == null || kills.getCounts() == null)
				{
					return new Progress(false, "?");
				}
				Integer count = kills.countFor(goal.getTarget());
				return amount(count == null ? 0 : count, goal.getAmount());
			}
			case COMBAT_POINTS:
			{
				CombatAchievementsData ca = view.get(memberId, Section.COMBAT_ACHIEVEMENTS, CombatAchievementsData.class);
				return ca == null ? new Progress(false, "?") : amount(ca.getPoints(), goal.getAmount());
			}
			case COLLECTION_LOG:
			{
				CollectionLogData clog = view.get(memberId, Section.COLLECTION_LOG, CollectionLogData.class);
				if (clog == null || clog.getObtained() <= 0)
				{
					return new Progress(false, "?");
				}
				return amount(clog.getObtained(), goal.getAmount());
			}
			default:
				return new Progress(false, "?");
		}
	}

	/**
	 * Check for members who have newly reached a goal.
	 *
	 * @return chat lines such as "bonscott1 reached the goal: 50 Firemaking."
	 */
	public synchronized List<String> checkCompletions()
	{
		List<String> out = new ArrayList<>();
		if (view == null)
		{
			return out;
		}
		List<Goal> goals = list();
		List<Member> members = members();
		Set<String> now = new HashSet<>();
		Set<String> goalIds = new HashSet<>();
		for (Goal goal : goals)
		{
			goalIds.add(goal.getId());
			for (Member member : members)
			{
				if (goal.getData().appliesTo(member.getId()) && progress(goal.getData(), member.getId()).isDone())
				{
					String key = goal.getId() + "|" + member.getId();
					now.add(key);
					// the first look, and goals we've only just heard about, count as already known
					if (completed != null && knownGoals.contains(goal.getId()) && !completed.contains(key))
					{
						out.add(member.getName() + " reached the goal: " + title(goal.getData()) + ".");
					}
				}
			}
		}
		completed = now;
		knownGoals = goalIds;
		return out;
	}

	/**
	 * A short description, such as "50 Firemaking", "Finish Dragon Slayer I" or "100 Vorkath kills".
	 */
	public static String title(GoalData goal)
	{
		GoalData.Type type = goal.typeOrNull();
		String target = Strings.nullToEmpty(Clean.text(goal.getTarget(), MAX_TARGET));
		if (type == null)
		{
			return "Unknown goal";
		}
		switch (type)
		{
			case SKILL:
				return goal.getAmount() + " " + target;
			case TOTAL_LEVEL:
				return goal.getAmount() + " total level";
			case QUEST:
				return "Finish " + target;
			case DIARY:
				return target + " diary";
			case KILL_COUNT:
				return goal.getAmount() + " " + target + " kills";
			case COMBAT_POINTS:
				return goal.getAmount() + " combat achievement points";
			case COLLECTION_LOG:
				return goal.getAmount() + " collection log slots";
			default:
				return "Unknown goal";
		}
	}

	static boolean isValid(GoalData goal)
	{
		GoalData.Type type = goal.typeOrNull();
		if (type == null)
		{
			return false;
		}
		switch (type)
		{
			case SKILL:
				return skill(goal.getTarget()) != null && goal.getAmount() >= 1 && goal.getAmount() <= 99;
			case QUEST:
				return !Strings.isNullOrEmpty(goal.getTarget());
			case DIARY:
				return diary(goal.getTarget()) != null;
			case KILL_COUNT:
				return !Strings.isNullOrEmpty(goal.getTarget()) && goal.getAmount() >= 1;
			default:
				return goal.getAmount() >= 1;
		}
	}

	static Skill skill(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(name) || skill.name().equalsIgnoreCase(name))
			{
				return skill;
			}
		}
		return null;
	}

	/**
	 * @return the region and tier index of a diary such as "Varrock Easy", or null if it isn't one
	 */
	static int[] diary(String target)
	{
		if (target == null)
		{
			return null;
		}
		for (int region = 0; region < DiariesData.REGIONS.size(); region++)
		{
			String prefix = DiariesData.REGIONS.get(region) + " ";
			if (target.regionMatches(true, 0, prefix, 0, prefix.length()))
			{
				String tier = target.substring(prefix.length()).trim();
				for (int t = 0; t < DiariesData.TIERS.size(); t++)
				{
					if (DiariesData.TIERS.get(t).equalsIgnoreCase(tier))
					{
						return new int[]{region, t};
					}
				}
			}
		}
		return null;
	}

	private static Progress amount(int have, int need)
	{
		return new Progress(have >= need, Math.min(have, need) + "/" + need, Math.min(have, need), need);
	}

	private static <V> V lookup(Map<String, V> map, String key)
	{
		if (key == null)
		{
			return null;
		}
		V exact = map.get(key);
		if (exact != null)
		{
			return exact;
		}
		String lower = key.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, V> e : map.entrySet())
		{
			if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lower))
			{
				return e.getValue();
			}
		}
		return null;
	}

	private GoalData decode(SyncRecord record)
	{
		if (record == null || record.getData() == null)
		{
			return null;
		}
		try
		{
			GoalData data = Checkable.checked(gson.fromJson(record.getData(), GoalData.class));
			if (data == null || data.typeOrNull() == null)
			{
				return null;
			}
			// written by other players' clients, so never trust it for display
			data.setTarget(Clean.text(data.getTarget(), MAX_TARGET));
			data.setCreatedByName(Clean.text(data.getCreatedByName(), MAX_NAME));
			return data;
		}
		catch (RuntimeException e)
		{
			log.debug("Unreadable goal {}", record.getKey(), e);
			return null;
		}
	}
}
