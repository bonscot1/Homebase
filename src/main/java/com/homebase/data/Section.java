package com.homebase.data;

import com.homebase.sync.Keys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The kinds of data each member shares. Each section is versioned on its own, so a member's bank from
 * this morning and their skills from a minute ago can both be the newest copy.
 */
@Getter
@RequiredArgsConstructor
public enum Section
{
	PROFILE("profile", false, 0),
	SKILLS("skills", false, 5_000),
	INVENTORY("inv", false, 3_000),
	EQUIPMENT("worn", false, 3_000),
	BANK("bank", false, 3_000),
	SEED_VAULT("seedvault", false, 3_000),
	LOOTING_BAG("lootingbag", false, 3_000),
	QUESTS("quests", false, 5_000),
	DIARIES("diaries", false, 2_000),
	GROUP_STORAGE("storage", true, 2_000);

	private final String key;

	/**
	 * True for data about the whole group, such as the shared group storage, rather than one member.
	 */
	private final boolean groupWide;

	/**
	 * Minimum gap between publishes, so a burst of changes goes out as one update.
	 */
	private final long minIntervalMs;

	public String keyFor(String memberId)
	{
		return groupWide ? Keys.group(key) : Keys.member(memberId, key);
	}
}
