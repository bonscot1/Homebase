package com.homebase.map;

import com.google.gson.Gson;
import com.homebase.HomebaseConfig;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Checkable;
import com.homebase.data.GroupIndex;
import com.homebase.data.PinData;
import com.homebase.data.Section;
import com.homebase.party.GroupSync;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncRecord;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;

/**
 * Right-click the world map and choose Pin for group, and everyone in the group sees a pin there in
 * your colour. Each member has one pin at a time, so placing another moves it.
 */
@Singleton
public class MapPins
{
	private static final String PIN = "Pin for group";
	private static final String UNPIN = "Remove your pin";
	private static final String MENU_TARGET = ColorUtil.wrapWithColorTag("Map", JagexColors.MENU_TARGET);

	private final Client client;
	private final HomebaseConfig config;
	private final LocalCollector collector;
	private final GroupSync groupSync;
	private final GroupIndex index;
	private final Gson gson;

	@Inject
	private MapPins(Client client, HomebaseConfig config, LocalCollector collector, GroupSync groupSync, GroupIndex index, Gson gson)
	{
		this.client = client;
		this.config = config;
		this.collector = collector;
		this.groupSync = groupSync;
		this.index = index;
		this.gson = gson;
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.worldMap() || !config.mapPins() || collector.getMemberId() == null || !groupSync.isGroupParty())
		{
			return;
		}
		Widget mapWidget = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		Point mouse = client.getMouseCanvasPosition();
		WorldMap map = client.getWorldMap();
		if (mapWidget == null || mapWidget.isHidden() || mouse == null || map == null
			|| !mapWidget.getBounds().contains(mouse.getX(), mouse.getY()))
		{
			return;
		}
		Point centre = map.getWorldMapPosition();
		if (centre == null)
		{
			return;
		}
		WorldPoint target = pointAt(mouse.getX(), mouse.getY(), mapWidget.getBounds(), map.getWorldMapZoom(), centre.getX(), centre.getY());

		if (hasPin())
		{
			client.getMenu().createMenuEntry(-1)
				.setOption(UNPIN)
				.setTarget(MENU_TARGET)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> remove());
		}
		client.getMenu().createMenuEntry(-1)
			.setOption(PIN)
			.setTarget(MENU_TARGET)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> place(target));
	}

	/**
	 * The reverse of how RuneLite places a marker on the map.
	 *
	 * @param map where the map is on screen
	 * @param zoom how many pixels wide a tile is
	 * @param centreX the tile in the middle of the map
	 * @param centreY the tile in the middle of the map
	 * @return the tile under a point on the map
	 */
	static WorldPoint pointAt(int mouseX, int mouseY, Rectangle map, float zoom, int centreX, int centreY)
	{
		int widthInTiles = (int) Math.ceil(map.getWidth() / zoom);
		int heightInTiles = (int) Math.ceil(map.getHeight() / zoom);
		int tilesAcross = (int) ((mouseX - map.getX()) / zoom);
		int tilesUp = (int) ((map.getY() + map.getHeight() - mouseY) / zoom);
		return new WorldPoint(centreX - widthInTiles / 2 + tilesAcross, centreY - heightInTiles / 2 - 1 + tilesUp, 0);
	}

	/**
	 * Share the player's new colour on their pin. Runs on the client thread.
	 */
	public void colorChanged()
	{
		PinData pin = mine();
		if (pin != null && pin.isPlaced())
		{
			pin.setColor(config.tileColor().getRGB() & 0xFFFFFF);
			collector.share(Section.PIN, pin);
		}
	}

	/**
	 * @return a line for the chat box when someone places a pin, or null if the record isn't one
	 */
	public String describeChange(SyncRecord record)
	{
		if (!Section.PIN.getKey().equals(Keys.sectionOf(record.getKey())) || record.isDeleted() || record.getData() == null)
		{
			return null;
		}
		String name = index.nameOf(Keys.memberOf(record.getKey()));
		PinData pin;
		try
		{
			pin = Checkable.checked(gson.fromJson(record.getData(), PinData.class));
		}
		catch (RuntimeException e)
		{
			return null;
		}
		if (name == null || pin == null || !pin.isPlaced())
		{
			return null;
		}
		String area = AreaNames.describe(pin.getX(), pin.getY());
		return name + " pinned a spot on the world map" + (AreaNames.UNKNOWN.equals(area) ? "." : ": " + area + ".");
	}

	private boolean hasPin()
	{
		PinData pin = mine();
		return pin != null && pin.isPlaced();
	}

	private void place(WorldPoint point)
	{
		collector.share(Section.PIN, new PinData(true, point.getX(), point.getY(), point.getPlane(),
			config.tileColor().getRGB() & 0xFFFFFF, System.currentTimeMillis()));
	}

	private void remove()
	{
		PinData pin = mine();
		if (pin != null)
		{
			pin.setPlaced(false);
			collector.share(Section.PIN, pin);
		}
	}

	private PinData mine()
	{
		return collector.shared(Section.PIN, PinData.class);
	}
}
