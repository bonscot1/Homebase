package com.homebase.map;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.party.GroupSync;
import com.homebase.party.PartyMembers;
import com.homebase.party.Presence;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.api.worldmap.WorldMapData;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * Online group members on the world map, moved as their positions arrive through the party.
 * <p>
 * RuneLite only draws a marker that's on the map being looked at, which on its own would make anyone in
 * a dungeon or an instance vanish. So someone in a dungeon is shown above it, and someone somewhere the
 * map doesn't cover, such as a player-owned house, stays where they were last on it. Only a viewer who
 * has a dungeon's own map open misses members on the surface, because that map has nowhere to put them.
 */
@Singleton
public class GroupWorldMap
{
	private static final int MAX_NAME = 20;

	/**
	 * Far more pictures than a group needs. This only stops them piling up as people change colour.
	 */
	private static final int MAX_KEPT_IMAGES = 256;

	private final Client client;
	private final WorldMapPointManager worldMapPointManager;
	private final PartyService partyService;
	private final GroupSync groupSync;
	private final GroupIndex index;
	private final HomebaseConfig config;
	private final Presence presence;

	/**
	 * Markers we added, by party member id.
	 */
	private final Map<Long, MemberPoint> points = new HashMap<>();

	/**
	 * The last place each member could be shown, by party member id.
	 */
	private final Map<Long, WorldPoint> lastOnMap = new HashMap<>();
	private final Map<String, BufferedImage> images = new HashMap<>();

	@Inject
	private GroupWorldMap(Client client, WorldMapPointManager worldMapPointManager, PartyService partyService, GroupSync groupSync,
		GroupIndex index, HomebaseConfig config, Presence presence)
	{
		this.client = client;
		this.worldMapPointManager = worldMapPointManager;
		this.partyService = partyService;
		this.groupSync = groupSync;
		this.index = index;
		this.config = config;
		this.presence = presence;
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
		for (PartyMember member : PartyMembers.current(partyService))
		{
			WorldPoint coord = presence.positionOf(member);
			String name = Clean.text(member.getDisplayName(), MAX_NAME);
			// the game already shows you on the map
			if (!presence.isOnline(member) || coord == null || name == null
				|| (local != null && local.getMemberId() == member.getMemberId()))
			{
				continue;
			}
			WorldPoint shown = pointOnMap(member.getMemberId(), coord);
			if (shown == null)
			{
				continue;
			}
			seen.add(member.getMemberId());

			String tooltip = name + " - " + AreaNames.describe(coord);
			MemberPoint point = points.get(member.getMemberId());
			if (point == null)
			{
				point = new MemberPoint(shown);
				point.show(shown, name, index.color(name), tooltip);
				points.put(member.getMemberId(), point);
				worldMapPointManager.add(point);
			}
			else
			{
				point.show(shown, name, index.color(name), tooltip);
			}
		}

		for (Iterator<Map.Entry<Long, MemberPoint>> it = points.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Long, MemberPoint> entry = it.next();
			if (!seen.contains(entry.getKey()))
			{
				worldMapPointManager.remove(entry.getValue());
				lastOnMap.remove(entry.getKey());
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
		lastOnMap.clear();
	}

	/**
	 * @return where to put a member's marker on the map being looked at, or null if there's nowhere yet
	 */
	private WorldPoint pointOnMap(long memberId, WorldPoint coord)
	{
		WorldPoint above = AreaNames.aboveGround(coord);
		WorldMapData openMap = openMap();
		WorldPoint point;
		if (openMap == null)
		{
			// nothing's drawn while the map is closed, so this only needs to remember roughly where they were
			point = AreaNames.isOnSurface(above) ? above : null;
		}
		else if (openMap.surfaceContainsPosition(coord.getX(), coord.getY()))
		{
			point = coord;
		}
		else
		{
			point = openMap.surfaceContainsPosition(above.getX(), above.getY()) ? above : null;
		}

		if (point == null)
		{
			return lastOnMap.get(memberId);
		}
		lastOnMap.put(memberId, point);
		return point;
	}

	/**
	 * @return the map being looked at, or null if the world map is closed
	 */
	private WorldMapData openMap()
	{
		if (client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER) == null)
		{
			return null;
		}
		WorldMap map = client.getWorldMap();
		return map == null ? null : map.getWorldMapData();
	}

	private BufferedImage image(String name, Color color, int angle)
	{
		if (images.size() >= MAX_KEPT_IMAGES)
		{
			images.clear();
		}
		return images.computeIfAbsent(name + '|' + color.getRGB() + '|' + angle, key -> MapMarker.draw(name, color, angle));
	}

	/**
	 * One member's marker. On the part of the map being looked at, its arrow points down at them. When
	 * they're off the edge it waits at the edge instead, and points the way to go.
	 */
	private final class MemberPoint extends WorldMapPoint
	{
		private Color color;

		private MemberPoint(WorldPoint point)
		{
			super(point, null);
			setSnapToEdge(true);
			setJumpOnClick(true);
		}

		private void show(WorldPoint point, String name, Color color, String tooltip)
		{
			setWorldPoint(point);
			setName(name);
			setTooltip(tooltip);
			this.color = color;
			drawArrow();
		}

		@Override
		public void onEdgeSnap()
		{
			drawArrow();
		}

		@Override
		public void onEdgeUnsnap()
		{
			drawArrow();
		}

		private void drawArrow()
		{
			if (isCurrentlyEdgeSnapped())
			{
				setImage(image(getName(), color, angleFromMapCentre()));
				// RuneLite keeps a snapped picture on screen around its middle
				setImagePoint(null);
			}
			else
			{
				BufferedImage picture = image(getName(), color, MapMarker.DOWN);
				setImage(picture);
				setImagePoint(MapMarker.tip(picture));
			}
		}

		private int angleFromMapCentre()
		{
			WorldMap map = client.getWorldMap();
			Point centre = map == null ? null : map.getWorldMapPosition();
			WorldPoint point = getWorldPoint();
			return centre == null ? MapMarker.DOWN
				: MapMarker.angleBetween(centre.getX(), centre.getY(), point.getX(), point.getY());
		}
	}
}
