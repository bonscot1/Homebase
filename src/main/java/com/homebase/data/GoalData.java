package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * A target set for the group, such as "50 Firemaking" before Wintertodt. Goals tick off on their own
 * as each member's shared data shows they've reached it.
 */
@Data
public class GoalData implements Checkable
{
	private static final int MAX_MEMBERS = 100;

	public enum Type
	{
		SKILL,
		TOTAL_LEVEL,
		QUEST,
		DIARY,
		KILL_COUNT,
		COMBAT_POINTS,
		COLLECTION_LOG
	}

	/**
	 * A {@link Type} name, kept as text so a goal type added in a later version is ignored rather than
	 * breaking older clients.
	 */
	private String type;

	/**
	 * What the goal is about: a skill, quest, diary ("Varrock Easy") or boss name. Unused for totals.
	 */
	private String target;

	/**
	 * The level, kill count, points or slots needed. Unused for quests and diaries.
	 */
	private int amount;

	/**
	 * Member ids the goal applies to. Empty means everyone.
	 */
	private List<String> members = new ArrayList<>();

	private String createdBy;
	private String createdByName;
	private long createdAt;

	public Type typeOrNull()
	{
		try
		{
			return type == null ? null : Type.valueOf(type);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/**
	 * @return true if the goal applies to the member
	 */
	public boolean appliesTo(String memberId)
	{
		return members == null || members.isEmpty() || members.contains(memberId);
	}

	@Override
	public void check()
	{
		members = Checkable.list(members, MAX_MEMBERS);
	}
}
