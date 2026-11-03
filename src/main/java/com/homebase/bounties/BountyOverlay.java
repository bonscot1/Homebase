package com.homebase.bounties;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * A padlock on group storage items with a bounty, red while it's locked for you and green once
 * you've reached its goal, with the goal in a tooltip.
 */
@Singleton
public class BountyOverlay extends WidgetItemOverlay
{
	private static final Color LOCKED = new Color(0xEF5350);
	private static final Color OPEN = new Color(0x66BB6A);

	private final Client client;
	private final ItemManager itemManager;
	private final BountyService bounties;
	private final TooltipManager tooltipManager;

	@Inject
	private BountyOverlay(Client client, ItemManager itemManager, BountyService bounties, TooltipManager tooltipManager)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.bounties = bounties;
		this.tooltipManager = tooltipManager;
		showOnInterfaces(InterfaceID.SHARED_BANK);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		BountyService.Lock lock = bounties.lockFor(itemManager.canonicalize(itemId));
		if (lock == null)
		{
			lock = bounties.lockFor(itemId);
		}
		if (lock == null)
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		drawPadlock(graphics, bounds.x + bounds.width - 9, bounds.y + 1, lock.isLocked() ? LOCKED : OPEN);

		Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && bounds.contains(mouse.getX(), mouse.getY()))
		{
			tooltipManager.add(new Tooltip(lock.isLocked()
				? "Bounty: locked until you reach " + lock.getGoal()
				: "Bounty: " + lock.getGoal() + " (you can take this)"));
		}
	}

	static void drawPadlock(Graphics2D g, int x, int y, Color color)
	{
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(Color.BLACK);
		g.drawArc(x + 1, y, 6, 7, 0, 180);
		g.fillRect(x - 1, y + 3, 10, 8);
		g.setColor(color);
		g.drawArc(x + 2, y + 1, 4, 6, 0, 180);
		g.fillRect(x, y + 4, 8, 6);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
	}
}
