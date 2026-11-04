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
import java.util.concurrent.TimeUnit;
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
 * Online group members and everyone's pins on the world map, moved as new positions arrive.
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

	/**
	 * Stands in for an angle in a picture's name, since a pin doesn't turn.
	 */
	private static final int NO_ANGLE = -1;

	private final Client client;
	private final WorldMapPointManager worldMapPointManager;
	private final PartyService partyService;
	private final GroupSync groupSync;
	private final GroupIndex index;
	private final HomebaseConfig config;
	private final Presence presence;

	/**
	 * Markers we added for members, by party member id.
	 */
	private final Map<Long, Marker> memberMarkers = new HashMap<>();

	/**
	 * Markers we added for pins, by the id of the member who placed them.
	 */
	private final Map<String, Marker> pinMarkers = new HashMap<>();

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
		updateMembers();
		updatePins();
	}

	public void clear()
	{
		memberMarkers.values().forEach(worldMapPointManager::remove);
		memberMarkers.clear();
		pinMarkers.values().forEach(worldMapPointManager::remove);
		pinMarkers.clear();
		lastOnMap.clear();
	}

	private void updateMembers()
	{
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
			Marker marker = memberMarkers.get(member.getMemberId());
			boolean isNew = marker == null;
			if (isNew)
			{
				marker = new Marker(shown, false);
				memberMarkers.put(member.getMemberId(), marker);
			}
			marker.show(shown, name, name, index.color(name), name + " - " + AreaNames.describe(coord));
			if (isNew)
			{
				worldMapPointManager.add(marker);
			}
		}

		for (Iterator<Map.Entry<Long, Marker>> it = memberMarkers.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Long, Marker> entry = it.next();
			if (!seen.contains(entry.getKey()))
			{
				worldMapPointManager.remove(entry.getValue());
				lastOnMap.remove(entry.getKey());
				it.remove();
			}
		}
	}

	private void updatePins()
	{
		Set<String> seen = new HashSet<>();
		long oldest = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(config.mapPinHours());
		if (config.mapPins())
		{
			for (GroupIndex.Pin pin : index.pins())
			{
				if (pin.getAt() < oldest)
				{
					continue;
				}
				seen.add(pin.getMemberId());
				Marker marker = pinMarkers.get(pin.getMemberId());
				boolean isNew = marker == null;
				if (isNew)
				{
					marker = new Marker(pin.getPoint(), true);
					pinMarkers.put(pin.getMemberId(), marker);
				}
				String title = pin.getName() + "'s pin";
				marker.show(pin.getPoint(), title, pin.getName(), pin.getColor(), title + " - " + AreaNames.describe(pin.getPoint()));
				if (isNew)
				{
					worldMapPointManager.add(marker);
				}
			}
		}

		for (Iterator<Map.Entry<String, Marker>> it = pinMarkers.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<String, Marker> entry = it.next();
			if (!seen.contains(entry.getKey()))
			{
				worldMapPointManager.remove(entry.getValue());
				it.remove();
			}
		}
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

	private BufferedImage image(boolean pin, String label, Color color, int angle, double scale)
	{
		if (images.size() >= MAX_KEPT_IMAGES)
		{
			images.clear();
		}
		String key = label + '|' + color.getRGB() + '|' + angle + '|' + scale + '|' + pin;
		return images.computeIfAbsent(key, k -> pin ? MapMarker.pin(label, color, scale) : MapMarker.arrow(label, color, angle, scale));
	}

	/**
	 * One marker. On the part of the map being looked at, its tip is on the spot it marks. When that's
	 * off the edge it waits at the edge instead, and a member's arrow points the way to go.
	 */
	private final class Marker extends WorldMapPoint
	{
		private final boolean pin;
		private String label;
		private Color color;

		private Marker(WorldPoint point, boolean pin)
		{
			super(point, null);
			this.pin = pin;
			setJumpOnClick(true);
		}

		/**
		 * @param title what RuneLite's Focus on menu option calls it
		 * @param name the name to show on it, if names are turned on
		 */
		private void show(WorldPoint point, String title, String name, Color color, String tooltip)
		{
			setWorldPoint(point);
			setName(title);
			setTooltip(tooltip);
			boolean keepAtEdge = config.mapEdge();
			setSnapToEdge(keepAtEdge);
			if (!keepAtEdge)
			{
				setCurrentlyEdgeSnapped(false);
			}
			label = config.mapNames() ? name : null;
			this.color = color;
			redraw();
		}

		@Override
		public void onEdgeSnap()
		{
			redraw();
		}

		@Override
		public void onEdgeUnsnap()
		{
			redraw();
		}

		private void redraw()
		{
			double scale = config.mapMarkerSize().getScale();
			if (isCurrentlyEdgeSnapped())
			{
				setImage(image(pin, label, color, pin ? NO_ANGLE : angleFromMapCentre(), scale));
				// RuneLite keeps a snapped picture on screen around its middle
				setImagePoint(null);
			}
			else
			{
				BufferedImage picture = image(pin, label, color, pin ? NO_ANGLE : MapMarker.DOWN, scale);
				setImage(picture);
				setImagePoint(MapMarker.tip(picture, scale));
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
