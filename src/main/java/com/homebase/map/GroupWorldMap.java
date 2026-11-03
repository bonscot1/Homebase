package com.homebase.map;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.party.GroupSync;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Online group members on the world map, moved as their positions arrive through the party.
 */
@Singleton
public class GroupWorldMap
{
	private static final int DOT_SIZE = 11;

	private final WorldMapPointManager worldMapPointManager;
	private final PartyService partyService;
	private final GroupSync groupSync;
	private final GroupIndex index;
	private final HomebaseConfig config;

	/**
	 * Points we added, by party member id.
	 */
	private final Map<Long, WorldMapPoint> points = new HashMap<>();
	private final Map<Integer, BufferedImage> dots = new HashMap<>();

	@Inject
	private GroupWorldMap(WorldMapPointManager worldMapPointManager, PartyService partyService, GroupSync groupSync,
		GroupIndex index, HomebaseConfig config)
	{
		this.worldMapPointManager = worldMapPointManager;
		this.partyService = partyService;
		this.groupSync = groupSync;
		this.index = index;
		this.config = config;
	}

	/**
	 * Move everyone's markers. Runs on the client thread, a couple of times a second.
	 */
	public void update()
	{
		if (!config.worldMap() || !groupSync.isGroupParty())
		{
			clear();
			return;
		}

		PartyMember local = partyService.getLocalMember();
		Set<Long> seen = new HashSet<>();
		for (PartyMember member : partyService.getMembers())
		{
			WorldPoint coord = member.getCoord();
			String name = Clean.text(member.getDisplayName(), 20);
			// the game already shows you on the map
			if (!member.isLoggedIn() || coord == null || name == null
				|| (local != null && local.getMemberId() == member.getMemberId()))
			{
				continue;
			}
			seen.add(member.getMemberId());

			BufferedImage dot = dot(index.color(name));
			String tooltip = name + " - " + AreaNames.describe(coord);
			WorldMapPoint point = points.get(member.getMemberId());
			if (point == null)
			{
				point = WorldMapPoint.builder()
					.worldPoint(coord)
					.image(dot)
					.name(name)
					.tooltip(tooltip)
					.snapToEdge(true)
					.jumpOnClick(true)
					.build();
				points.put(member.getMemberId(), point);
				worldMapPointManager.add(point);
			}
			else
			{
				point.setWorldPoint(coord);
				point.setImage(dot);
				point.setName(name);
				point.setTooltip(tooltip);
			}
		}

		for (Iterator<Map.Entry<Long, WorldMapPoint>> it = points.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Long, WorldMapPoint> entry = it.next();
			if (!seen.contains(entry.getKey()))
			{
				worldMapPointManager.remove(entry.getValue());
				it.remove();
			}
		}
	}

	public void clear()
	{
		for (WorldMapPoint point : points.values())
		{
			worldMapPointManager.remove(point);
		}
		points.clear();
	}

	/**
	 * @return how many members are on the map, for tests and the panel
	 */
	public int size()
	{
		return points.size();
	}

	private BufferedImage dot(Color color)
	{
		return dots.computeIfAbsent(color.getRGB(), rgb ->
		{
			BufferedImage image = new BufferedImage(DOT_SIZE, DOT_SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(color);
			g.fillOval(1, 1, DOT_SIZE - 2, DOT_SIZE - 2);
			g.setColor(Color.BLACK);
			g.setStroke(new BasicStroke(1.2f));
			g.drawOval(1, 1, DOT_SIZE - 2, DOT_SIZE - 2);
			g.dispose();
			return image;
		});
	}
}
