package com.homebase.data;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

	private static final class Snapshot
	{
		final Map<String, Integer> combatLevels = new HashMap<>();
		final Map<String, Color> colors = new HashMap<>();
		final Map<String, String> idsByName = new HashMap<>();
		final List<MarkedTile> tiles = new ArrayList<>();
		final List<Highlight> highlights = new ArrayList<>();
	}

	private volatile GroupView view;
	private volatile Predicate<ProfileData> belongs = p -> true;
	private volatile boolean dirty = true;
	private volatile Snapshot snapshot = new Snapshot();

	public void setView(GroupView view, Predicate<ProfileData> belongs)
	{
		this.view = view;
		this.belongs = belongs;
		dirty = true;
	}

	/**
	 * Group data changed. Cheap, so it's fine to call for every record.
	 */
	public void invalidate()
	{
		dirty = true;
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

	private Snapshot current()
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
			out.colors.put(key, color);
		}
		return out;
	}

	private static String key(String name)
	{
		// the game sometimes writes the space in a name as a non-breaking one
		return name.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
