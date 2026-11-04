package com.homebase.overlay;

import com.homebase.HomebaseConfig;
import com.homebase.collect.LocalCollector;
import com.homebase.data.GroupIndex;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * While the collection log is open, a dot on each item in the colour of every other group member who
 * has it, with their names when the mouse is over it. Only what's been shared can be shown, so someone
 * who hasn't opened the log and clicked Search yet may have more than their dots say.
 */
@Singleton
public class CollectionLogOverlay extends Overlay
{
	private static final int DOT_SIZE = 6;
	private static final int DOT_GAP = 1;
	private static final Stroke DOT_EDGE = new BasicStroke(1f);

	private final Client client;
	private final HomebaseConfig config;
	private final GroupIndex index;
	private final LocalCollector collector;
	private final TooltipManager tooltipManager;

	@Inject
	private CollectionLogOverlay(Client client, HomebaseConfig config, GroupIndex index, LocalCollector collector, TooltipManager tooltipManager)
	{
		this.client = client;
		this.config = config;
		this.index = index;
		this.collector = collector;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget items = config.logOwners() ? client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS) : null;
		Widget[] children = items == null || items.isHidden() ? null : items.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return null;
		}
		// items scrolled out of view still have positions, so keep the dots inside the visible area
		Rectangle visible = items.getBounds();
		Point mouse = client.getMouseCanvasPosition();
		Shape clip = graphics.getClip();
		Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setClip(visible);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		String me = collector.getMemberId();

		for (Widget child : children)
		{
			int itemId = child.getItemId();
			Rectangle bounds = child.getBounds();
			if (itemId <= 0 || child.isHidden() || !bounds.intersects(visible))
			{
				continue;
			}
			List<String> names = new ArrayList<>();
			int x = bounds.x + 1;
			for (GroupIndex.LogOwner owner : index.logOwners(itemId))
			{
				if (owner.getMemberId().equals(me))
				{
					continue;
				}
				drawDot(graphics, x, bounds.y + bounds.height - DOT_SIZE - 1, owner.getColor());
				x += DOT_SIZE + DOT_GAP;
				names.add(owner.getName());
			}
			boolean hovered = mouse != null && visible.contains(mouse.getX(), mouse.getY()) && bounds.contains(mouse.getX(), mouse.getY());
			if (hovered && !client.isMenuOpen() && (!names.isEmpty() || index.hasOtherMembers(me)))
			{
				tooltipManager.add(new Tooltip(names.isEmpty() ? "Nobody else in your group has shown this yet" : "Also has this: " + String.join(", ", names)));
			}
		}

		graphics.setClip(clip);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		return null;
	}

	private static void drawDot(Graphics2D g, int x, int y, Color color)
	{
		g.setColor(color);
		g.fillOval(x, y, DOT_SIZE, DOT_SIZE);
		g.setColor(Color.BLACK);
		g.setStroke(DOT_EDGE);
		g.drawOval(x, y, DOT_SIZE, DOT_SIZE);
	}
}
