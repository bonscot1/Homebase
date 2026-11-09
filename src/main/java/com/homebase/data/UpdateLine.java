package com.homebase.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line of a member's update, such as "Magic 15 → 32", and what it's about, so it can be shown
 * with a picture of the skill, boss or item. Kept on this computer only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLine
{
	public enum Kind
	{
		LEVEL,
		QUEST,
		DIARY,
		COMBAT_ACHIEVEMENT,
		COLLECTION_LOG,
		KILL_COUNT,
		BEST_TIME,
		DROP,
		PET,
		DEATH,
	}

	private Kind kind;

	/**
	 * The skill or boss it's about. Null for other kinds of line, and for a line about several.
	 */
	private String subject;

	/**
	 * The item it's about, or 0 for none.
	 */
	private int item;

	private String text;

	public static UpdateLine of(Kind kind, String text)
	{
		return new UpdateLine(kind, null, 0, text);
	}

	public static UpdateLine about(Kind kind, String subject, String text)
	{
		return new UpdateLine(kind, subject, 0, text);
	}
}
