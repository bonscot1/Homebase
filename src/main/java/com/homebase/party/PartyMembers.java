package com.homebase.party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

/**
 * The party's members, one per player. When a player logs out and back in, RuneLite's party server
 * can keep their old connection for a while before telling everyone it left, so the same player shows
 * up twice; only the newest connection, which is the live one, is kept.
 */
public final class PartyMembers
{
	private PartyMembers()
	{
	}

	public static List<PartyMember> current(PartyService party)
	{
		return newestPerName(party.getMembers(), PartyMember::getDisplayName);
	}

	/**
	 * @return the player's live party member, or null if they aren't in the party
	 */
	public static PartyMember byName(PartyService party, String name)
	{
		if (name == null)
		{
			return null;
		}
		String key = key(name);
		PartyMember found = null;
		for (PartyMember member : party.getMembers())
		{
			if (member.getDisplayName() != null && key(member.getDisplayName()).equals(key))
			{
				// members are listed in the order they joined, so the last match is the newest
				found = member;
			}
		}
		return found;
	}

	/**
	 * @param members in the order they joined
	 * @param name each member's display name, or null if it isn't known yet
	 */
	static <T> List<T> newestPerName(List<T> members, Function<T, String> name)
	{
		Map<String, T> byName = new LinkedHashMap<>();
		List<T> unnamed = new ArrayList<>();
		for (T member : members)
		{
			String n = name.apply(member);
			if (n == null)
			{
				unnamed.add(member);
				continue;
			}
			String key = key(n);
			byName.remove(key);
			byName.put(key, member);
		}
		List<T> out = new ArrayList<>(byName.values());
		out.addAll(unnamed);
		return out;
	}

	private static String key(String name)
	{
		return Text.toJagexName(name).toLowerCase(Locale.ROOT);
	}
}
