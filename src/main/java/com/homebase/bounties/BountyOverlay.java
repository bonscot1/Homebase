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
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * A padlock on group storage items with a bounty or a request waiting on them: red while some of it is
 * held back from you, green while you can take it, with the reasons and what's free in a tooltip.
 */
@Singleton
public class BountyOverlay extends WidgetItemOverlay
{
	private static final Color LOCKED = new Color(0xEF5350);
	private static final Color OPEN = new Color(0x66BB6A);

	private final Client client;
	private final StorageHolds holds;
	private final TooltipManager tooltipManager;

	@Inject
	private BountyOverlay(Client client, StorageHolds holds, TooltipManager tooltipManager)
	{
		this.client = client;
		this.holds = holds;
		this.tooltipManager = tooltipManager;
		showOnInterfaces(InterfaceID.SHARED_BANK);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		StorageHolds.Hold hold = holds.holdFor(itemId);
		if (!hold.any())
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		drawPadlock(graphics, bounds.x + bounds.width - 9, bounds.y + 1, hold.getHeld() > 0 ? LOCKED : OPEN);

		Point mouse = client.getMouseCanvasPosition();
		if (mouse != null && bounds.contains(mouse.getX(), mouse.getY()))
		{
			StringBuilder text = new StringBuilder(String.join("</br>", hold.getReasons()));
			if (hold.getHeld() > 0)
			{
				int free = StorageHolds.free(widgetItem.getQuantity(), hold.getHeld());
				text.append("</br>").append(free > 0 ? free + " free to take" : "None free to take");
			}
			tooltipManager.add(new Tooltip(text.toString()));
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
