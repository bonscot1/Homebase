package com.homebase.overlay;

import com.homebase.HomebaseConfig;
import com.homebase.data.GroupIndex;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Draws every tile the group has marked, each in the colour of whoever marked it.
 */
@Singleton
public class GroupTileOverlay extends Overlay
{
	private static final Stroke STROKE = new BasicStroke(2f);
	private static final int FILL_ALPHA = 40;

	private final Client client;
	private final HomebaseConfig config;
	private final GroupIndex index;

	@Inject
	private GroupTileOverlay(Client client, HomebaseConfig config, GroupIndex index)
	{
		this.client = client;
		this.config = config;
		this.index = index;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.groupTiles() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		WorldView view = client.getTopLevelWorldView();
		if (view == null)
		{
			return null;
		}
		int plane = view.getPlane();
		boolean names = config.tileNames();
		for (GroupIndex.MarkedTile tile : index.tiles())
		{
			// in an instance one template tile can appear in several places, or none
			for (WorldPoint point : WorldPoint.toLocalInstance(view, tile.getPoint()))
			{
				if (point.getPlane() != plane)
				{
					continue;
				}
				LocalPoint local = LocalPoint.fromWorld(view, point);
				if (local == null)
				{
					continue;
				}
				Polygon poly = Perspective.getCanvasTilePoly(client, local);
				if (poly == null)
				{
					continue;
				}
				Color color = tile.getColor();
				OverlayUtil.renderPolygon(graphics, poly, color, new Color(color.getRed(), color.getGreen(), color.getBlue(), FILL_ALPHA), STROKE);
				if (names)
				{
					Point text = Perspective.getCanvasTextLocation(client, graphics, local, tile.getName(), 0);
					if (text != null)
					{
						OverlayUtil.renderTextLocation(graphics, text, tile.getName(), color);
					}
				}
			}
		}
		return null;
	}
}
