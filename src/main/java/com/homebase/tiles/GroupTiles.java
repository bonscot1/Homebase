package com.homebase.tiles;

import com.homebase.HomebaseConfig;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Section;
import com.homebase.data.TilesData;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;

/**
 * Marks tiles for the whole group. Shift + right-click a tile and choose Group mark; everyone in the
 * group sees it in your colour. Tiles in instances are stored at their template location, so a tile
 * marked in one raid shows up in the same spot in everyone's raid.
 */
@Singleton
public class GroupTiles
{
	private static final String MARK = "Group mark";
	private static final String UNMARK = "Group unmark";
	private static final String WALK_HERE = "Walk here";

	private final Client client;
	private final HomebaseConfig config;
	private final LocalCollector collector;

	@Inject
	private GroupTiles(Client client, HomebaseConfig config, LocalCollector collector)
	{
		this.client = client;
		this.config = config;
		this.collector = collector;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.groupTiles() || event.getType() != MenuAction.WALK.getId() || !WALK_HERE.equals(event.getOption())
			|| !client.isKeyPressed(KeyCode.KC_SHIFT) || collector.getMemberId() == null)
		{
			return;
		}
		WorldView view = client.getTopLevelWorldView();
		Tile tile = view == null ? null : view.getSelectedSceneTile();
		if (tile == null)
		{
			return;
		}
		WorldPoint point = templatePoint(tile.getLocalLocation());
		if (point == null)
		{
			return;
		}
		boolean marked = mine().contains(point.getX(), point.getY(), point.getPlane());
		client.getMenu().createMenuEntry(-1)
			.setOption(marked ? UNMARK : MARK)
			.setTarget("Tile")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggle(point));
	}

	/**
	 * Share our tiles again with the new colour. Runs on the client thread.
	 */
	public void colorChanged()
	{
		if (collector.getMemberId() == null)
		{
			return;
		}
		TilesData data = collector.shared(Section.TILES, TilesData.class);
		if (data != null)
		{
			data.setColor(config.tileColor().getRGB() & 0xFFFFFF);
			collector.share(Section.TILES, data);
		}
	}

	private void toggle(WorldPoint point)
	{
		TilesData data = mine();
		data.setColor(config.tileColor().getRGB() & 0xFFFFFF);
		data.toggle(point.getX(), point.getY(), point.getPlane());
		collector.share(Section.TILES, data);
	}

	private TilesData mine()
	{
		TilesData data = collector.shared(Section.TILES, TilesData.class);
		if (data == null)
		{
			data = new TilesData();
		}
		if (data.getTiles() == null)
		{
			data.setTiles(new ArrayList<>());
		}
		return data;
	}

	/**
	 * @return where the tile is in the game's template map, which is where it is in the world unless
	 * we're in an instance
	 */
	private WorldPoint templatePoint(LocalPoint local)
	{
		return local == null ? null : WorldPoint.fromLocalInstance(client, local);
	}
}
