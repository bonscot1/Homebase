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

	@Value
	public static class MarkedTile
	{
		WorldPoint point;
		Color color;
		String name;
	}

	private static final class Snapshot
	{
		final Map<String, Integer> combatLevels = new HashMap<>();
		final Map<String, Color> colors = new HashMap<>();
		final Map<String, String> idsByName = new HashMap<>();
		final List<MarkedTile> tiles = new ArrayList<>();
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
	 * @return every tile marked by anyone in the group
	 */
	public List<MarkedTile> tiles()
	{
		return Collections.unmodifiableList(current().tiles);
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
		Snapshot s = new Snapshot();
		if (view == null)
		{
			return s;
		}
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			if (profile == null || profile.getName() == null || !belongs.test(profile))
			{
				continue;
			}
			String name = Clean.text(profile.getName(), 20);
			if (name == null)
			{
				continue;
			}
			String key = key(name);
			s.idsByName.put(key, id);

			SkillsData skills = view.get(id, Section.SKILLS, SkillsData.class);
			if (skills != null && skills.getXp() != null && !skills.getXp().isEmpty())
			{
				s.combatLevels.put(key, Experience.getCombatLevel(
					skills.getLevel(Skill.ATTACK), skills.getLevel(Skill.STRENGTH), skills.getLevel(Skill.DEFENCE),
					skills.getLevel(Skill.HITPOINTS), skills.getLevel(Skill.MAGIC), skills.getLevel(Skill.RANGED),
					skills.getLevel(Skill.PRAYER)));
			}

			TilesData tiles = view.get(id, Section.TILES, TilesData.class);
			Color color = DEFAULT_COLOR;
			if (tiles != null)
			{
				color = new Color(tiles.getColor() & 0xFFFFFF);
				if (tiles.getTiles() != null)
				{
					int n = 0;
					for (TilesData.Tile tile : tiles.getTiles())
					{
						if (tile != null && n++ < TilesData.MAX_TILES)
						{
							s.tiles.add(new MarkedTile(new WorldPoint(tile.getX(), tile.getY(), tile.getPlane()), color, name));
						}
					}
				}
			}
			s.colors.put(key, color);
		}
		return s;
	}

	private static String key(String name)
	{
		return name.replace(' ', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
