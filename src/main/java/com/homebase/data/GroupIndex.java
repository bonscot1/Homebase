package com.homebase.data;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * Lookups the overlays need every frame, such as a member's combat level or everyone's marked tiles.
 * Worked out again only after the group's data changes, so drawing a frame never decodes records.
 */
@Singleton
public class GroupIndex
{
	/**
	 * Tile colour for members who haven't shared one.
	 */
	public static final Color DEFAULT_COLOR = new Color(0xFF9800);

	private static final int MAX_NAME = 20;

	@Value
	public static class MarkedTile
	{
		WorldPoint point;
		Color color;
		String name;
	}

	@Value
	public static class Highlight
	{
		int world;
		int index;
		int id;
		Color color;
		String name;
	}

	@Value
	public static class Pin
	{
		String memberId;
		String name;
		Color color;
		WorldPoint point;
		long at;
	}

	@Value
	public static class LogOwner
	{
		String memberId;
		String name;
		Color color;
	}

	private static final class Snapshot
	{
		final Map<String, Integer> combatLevels = new HashMap<>();
		final Map<String, Integer> worlds = new HashMap<>();
		final Map<String, Color> colors = new HashMap<>();
		final Map<String, String> idsByName = new HashMap<>();
		final Map<String, String> namesById = new HashMap<>();
		final List<MarkedTile> tiles = new ArrayList<>();
		final List<Highlight> highlights = new ArrayList<>();
		final List<Pin> pins = new ArrayList<>();
	}

	private volatile GroupView view;
	private volatile Predicate<ProfileData> belongs = p -> true;
	private volatile boolean dirty = true;
	private volatile Snapshot snapshot = new Snapshot();

	/**
	 * Who has each collection log item, by item id. Kept apart from the rest and only worked out while
	 * someone is looking at their collection log, because it means reading everyone's whole log.
	 */
	private volatile boolean logOwnersDirty = true;
	private volatile Map<Integer, List<LogOwner>> logOwners = Collections.emptyMap();

	public void setView(GroupView view, Predicate<ProfileData> belongs)
	{
		this.view = view;
		this.belongs = belongs;
		dirty = true;
		logOwnersDirty = true;
	}

	/**
	 * Group data changed. Cheap, so it's fine to call for every record.
	 */
	public void invalidate()
	{
		dirty = true;
		logOwnersDirty = true;
	}

	/**
	 * @return the member's combat level worked out from their shared skills, or 0 if we don't know it
	 */
	public int combatLevel(String name)
	{
		Integer level = name == null ? null : current().combatLevels.get(key(name));
		return level == null ? 0 : level;
	}

	/**
	 * @return the colour the member chose for their tiles
	 */
	public Color color(String name)
	{
		Color color = name == null ? null : current().colors.get(key(name));
		return color == null ? DEFAULT_COLOR : color;
	}

	/**
	 * @return the member id for a player name, or null if they aren't in the group
	 */
	public String memberId(String name)
	{
		return name == null ? null : current().idsByName.get(key(name));
	}

	/**
	 * @return the member's name as they share it, or null if they aren't in the group
	 */
	public String nameOf(String memberId)
	{
		return memberId == null ? null : current().namesById.get(memberId);
	}

	/**
	 * @return true if there's anyone in the group besides this member
	 */
	public boolean hasOtherMembers(String memberId)
	{
		Map<String, String> names = current().namesById;
		return names.size() > (memberId != null && names.containsKey(memberId) ? 1 : 0);
	}

	/**
	 * @return the world the member was last on, or 0 if they've logged out or we don't know
	 */
	public int world(String name)
	{
		Integer world = name == null ? null : current().worlds.get(key(name));
		return world == null ? 0 : world;
	}

	/**
	 * @return true if the member id belongs to someone in the group, so what they set up counts
	 */
	public boolean isMember(String memberId)
	{
		return memberId != null && current().idsByName.containsValue(memberId);
	}

	/**
	 * @return every tile marked by anyone in the group
	 */
	public List<MarkedTile> tiles()
	{
		return Collections.unmodifiableList(current().tiles);
	}

	/**
	 * @return every monster highlighted by anyone in the group
	 */
	public List<Highlight> highlights()
	{
		return Collections.unmodifiableList(current().highlights);
	}

	/**
	 * @return the spot each member has pinned on the world map
	 */
	public List<Pin> pins()
	{
		return Collections.unmodifiableList(current().pins);
	}

	/**
	 * @return everyone who has this collection log item, as far as we've seen their log
	 */
	public List<LogOwner> logOwners(int itemId)
	{
		if (logOwnersDirty)
		{
			logOwnersDirty = false;
			logOwners = buildLogOwners(view);
		}
		return logOwners.getOrDefault(itemId, Collections.emptyList());
	}

	private Map<Integer, List<LogOwner>> buildLogOwners(GroupView view)
	{
		Map<Integer, List<LogOwner>> out = new HashMap<>();
		if (view == null)
		{
			return out;
		}
		Snapshot members = current();
		for (Map.Entry<String, String> member : members.namesById.entrySet())
		{
			CollectionLogData log = view.get(member.getKey(), Section.COLLECTION_LOG, CollectionLogData.class);
			if (log == null)
			{
				continue;
			}
			LogOwner owner = new LogOwner(member.getKey(), member.getValue(), color(member.getValue()));
			// an item can be on more than one page
			Set<Integer> items = new HashSet<>();
			for (CollectionLogData.Page page : log.getPages().values())
			{
				items.addAll(page.getItems());
			}
			for (int item : items)
			{
				out.computeIfAbsent(item, id -> new ArrayList<>()).add(owner);
			}
		}
		return out;
	}

	private synchronized Snapshot current()
	{
		if (dirty)
		{
			dirty = false;
			snapshot = build(view, belongs);
		}
		return snapshot;
	}

	private static Snapshot build(GroupView view, Predicate<ProfileData> belongs)
	{
		Snapshot out = new Snapshot();
		if (view == null)
		{
			return out;
		}
		// the view hands out checked data, so lists are never null and already capped
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			String name = profile == null ? null : Clean.text(profile.getName(), MAX_NAME);
			if (name == null || !belongs.test(profile))
			{
				continue;
			}
			String key = key(name);
			out.idsByName.put(key, id);
			out.namesById.put(id, name);
			out.worlds.put(key, profile.getWorld());

			SkillsData skills = view.get(id, Section.SKILLS, SkillsData.class);
			if (skills != null && !skills.getXp().isEmpty())
			{
				out.combatLevels.put(key, Experience.getCombatLevel(
					skills.getLevel(Skill.ATTACK), skills.getLevel(Skill.STRENGTH), skills.getLevel(Skill.DEFENCE),
					skills.getLevel(Skill.HITPOINTS), skills.getLevel(Skill.MAGIC), skills.getLevel(Skill.RANGED),
					skills.getLevel(Skill.PRAYER)));
			}

			Color color = DEFAULT_COLOR;
			HighlightsData highlights = view.get(id, Section.HIGHLIGHTS, HighlightsData.class);
			if (highlights != null)
			{
				color = new Color(highlights.getColor() & 0xFFFFFF);
				for (HighlightsData.Npc npc : highlights.getNpcs())
				{
					out.highlights.add(new Highlight(highlights.getWorld(), npc.getIndex(), npc.getId(), color, name));
				}
			}
			TilesData tiles = view.get(id, Section.TILES, TilesData.class);
			if (tiles != null)
			{
				color = new Color(tiles.getColor() & 0xFFFFFF);
				for (TilesData.Tile tile : tiles.getTiles())
				{
					out.tiles.add(new MarkedTile(new WorldPoint(tile.getX(), tile.getY(), tile.getPlane()), color, name));
				}
			}
			// the colour they chose, once they've said; before that, what their tiles or highlights were marked in
			Color chosen = profile.getColor() == null ? color : new Color(profile.getColor() & 0xFFFFFF);
			out.colors.put(key, chosen);

			PinData pin = view.get(id, Section.PIN, PinData.class);
			if (pin != null && pin.isPlaced())
			{
				out.pins.add(new Pin(id, name, new Color(pin.getColor() & 0xFFFFFF),
					new WorldPoint(pin.getX(), pin.getY(), pin.getPlane()), pin.getAt()));
			}
		}
		return out;
	}

	private static String key(String name)
	{
		// the game sometimes writes the space in a name as a non-breaking one
		return name.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
