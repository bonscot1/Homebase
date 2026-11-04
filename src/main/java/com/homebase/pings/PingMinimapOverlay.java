package com.homebase.pings;

import com.homebase.HomebaseConfig;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Shows group pings on the minimap too, so one behind the camera still gets noticed.
 */
@Singleton
public class PingMinimapOverlay extends Overlay
{
	private final Client client;
	private final PingService pings;
	private final HomebaseConfig config;

	@Inject
	private PingMinimapOverlay(Client client, PingService pings, HomebaseConfig config)
	{
		this.client = client;
		this.pings = pings;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		if (!config.pingMinimap() || client.getGameState() != GameState.LOGGED_IN || view == null)
		{
			return null;
		}
		long now = System.currentTimeMillis();
		long lifetime = pings.lifetimeMs();
		Object aa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (Ping ping : pings.active())
		{
			if (ping.getWorld() != client.getWorld())
			{
				continue;
			}
			float age = ping.age(now, lifetime);
			NPC npc = PingOverlay.npcFor(view, ping);
			if (npc != null)
			{
				draw(graphics, npc.getLocalLocation(), ping.getColor(), age);
				continue;
			}
			for (LocalPoint local : PingOverlay.localPoints(view, ping.getPoint()))
			{
				draw(graphics, local, ping.getColor(), age);
			}
		}
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
		return null;
	}

	private void draw(Graphics2D graphics, LocalPoint local, Color color, float age)
	{
		Point at = local == null ? null : Perspective.localToMinimap(client, local);
		if (at == null)
		{
			return;
		}
		int x = at.getX();
		int y = at.getY();
		graphics.setColor(Color.BLACK);
		graphics.fillOval(x - 4, y - 4, 9, 9);
		graphics.setColor(PingOverlay.fade(color, age, 255));
		graphics.fillOval(x - 3, y - 3, 7, 7);
		// a ring that keeps spreading out while the ping lasts
		int r = 4 + (int) (((age * 3) % 1f) * 8);
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.setColor(PingOverlay.fade(color, age, 200));
		graphics.drawOval(x - r, y - r, r * 2, r * 2);
	}
}
