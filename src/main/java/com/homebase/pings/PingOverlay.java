package com.homebase.pings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Draws group pings in the game world: a pulsing outline on a pinged monster, or a filled tile with a
 * ripple, each with the pinger's name, fading out over the ping's life.
 */
@Singleton
public class PingOverlay extends Overlay
{
	/**
	 * Share of a ping's life the ripple takes.
	 */
	private static final float RIPPLE = 0.35f;

	private final Client client;
	private final PingService pings;
	private final ModelOutlineRenderer outlines;

	@Inject
	private PingOverlay(Client client, PingService pings, ModelOutlineRenderer outlines)
	{
		this.client = client;
		this.pings = pings;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		if (client.getGameState() != GameState.LOGGED_IN || view == null)
		{
			return null;
		}
		long now = System.currentTimeMillis();
		long lifetime = pings.lifetimeMs();
		for (Ping ping : pings.active())
		{
			if (ping.getWorld() != client.getWorld())
			{
				continue;
			}
			float age = ping.age(now, lifetime);
			NPC npc = npcFor(view, ping);
			if (npc != null)
			{
				drawNpc(graphics, npc, ping, age);
			}
			else
			{
				for (LocalPoint local : localPoints(view, ping.getPoint()))
				{
					drawTile(graphics, local, ping, age);
				}
			}
		}
		return null;
	}

	private void drawNpc(Graphics2D graphics, NPC npc, Ping ping, float age)
	{
		// a quick double pulse at the start, then a steady outline that fades
		double pulse = age < RIPPLE ? Math.abs(Math.sin(age / RIPPLE * Math.PI * 2)) : 0;
		outlines.drawOutline(npc, 3 + (int) Math.round(pulse * 3), fade(ping.getColor(), age, 255), 4);
		String text = ping.getName();
		Point at = npc.getCanvasTextLocation(graphics, text, npc.getLogicalHeight() + 40);
		if (at != null)
		{
			OverlayUtil.renderTextLocation(graphics, at, text, fade(ping.getColor(), age, 255));
		}
	}

	private void drawTile(Graphics2D graphics, LocalPoint local, Ping ping, float age)
	{
		Polygon poly = Perspective.getCanvasTilePoly(client, local);
		if (poly == null)
		{
			return;
		}
		Color color = ping.getColor();
		graphics.setStroke(new BasicStroke(2f));
		graphics.setColor(fade(color, age, 90));
		graphics.fill(poly);
		graphics.setColor(fade(color, age, 255));
		graphics.draw(poly);

		if (age < RIPPLE)
		{
			// a ring spreading out from the tile, so the eye catches it
			float t = age / RIPPLE;
			graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (200 * (1 - t))));
			graphics.draw(scaled(poly, 1 + 2.5 * t));
		}

		String text = ping.getName();
		Point at = Perspective.getCanvasTextLocation(client, graphics, local, text, 120);
		if (at != null)
		{
			OverlayUtil.renderTextLocation(graphics, at, text, fade(color, age, 255));
		}
	}

	static NPC npcFor(WorldView view, Ping ping)
	{
		if (!ping.isNpc())
		{
			return null;
		}
		NPC npc = view.npcs().byIndex(ping.getNpcIndex());
		// the index is only the same monster while it's the same kind
		return npc != null && npc.getId() == ping.getNpcId() ? npc : null;
	}

	/**
	 * @return where a template point is in the scene; inside instances one template tile can be in
	 * several places, or none
	 */
	static List<LocalPoint> localPoints(WorldView view, WorldPoint point)
	{
		List<LocalPoint> out = new ArrayList<>();
		for (WorldPoint wp : WorldPoint.toLocalInstance(view, point))
		{
			if (wp.getPlane() != view.getPlane())
			{
				continue;
			}
			LocalPoint local = LocalPoint.fromWorld(view, wp);
			if (local != null)
			{
				out.add(local);
			}
		}
		return out;
	}

	/**
	 * @return the colour faded for a ping's age: bright for most of its life, then quickly gone
	 */
	static Color fade(Color color, float age, int alpha)
	{
		float left = 1 - age * age;
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, (int) (alpha * left))));
	}

	private static Polygon scaled(Polygon poly, double factor)
	{
		Rectangle bounds = poly.getBounds();
		double cx = bounds.getCenterX();
		double cy = bounds.getCenterY();
		Polygon out = new Polygon();
		for (int i = 0; i < poly.npoints; i++)
		{
			out.addPoint((int) Math.round(cx + (poly.xpoints[i] - cx) * factor), (int) Math.round(cy + (poly.ypoints[i] - cy) * factor));
		}
		return out;
	}
}
