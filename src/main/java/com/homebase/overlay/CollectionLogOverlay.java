package com.homebase.overlay;

import com.homebase.HomebaseConfig;
import com.homebase.collect.LocalCollector;
import com.homebase.data.GroupIndex;
import com.homebase.data.KillCountsData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.FontTypeFace;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * While the collection log is open, on a page or in search results, a dot on each item in the colour
 * of every other group member who has it, with their names when the mouse is over it. A boss's page
 * also has the other members' kill counts and best times after its own. Only what's been shared can be
 * shown, so someone who hasn't opened the log and clicked Search yet may have more than their dots say.
 */
@Singleton
public class CollectionLogOverlay extends Overlay
{
	private static final int DOT_SIZE = 6;
	private static final int DOT_GAP = 1;
	private static final Stroke DOT_EDGE = new BasicStroke(1f);

	/**
	 * The page's own kill count line, such as "Vorkath kills: 50".
	 */
	private static final Pattern HEADER_KILLS = Pattern.compile("^(.+?) kills: [\\d,]+$", Pattern.CASE_INSENSITIVE);

	/**
	 * How the page's best time line starts, in lower case.
	 */
	private static final String PERSONAL_BEST = "personal best";

	@Value
	private static class Shown
	{
		String text;
		GroupIndex.BossRecord record;
	}

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
		if (!config.logOwners())
		{
			return null;
		}
		Point mouse = client.getMouseCanvasPosition();
		drawGroupValues(graphics, mouse);
		Widget items = itemList();
		Widget[] children = items == null || items.isHidden() ? null : items.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return null;
		}
		// items scrolled out of view still have positions, so keep the dots inside the visible area
		Rectangle visible = visibleArea(items);
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

	/**
	 * On a boss's page, the other members' kill counts and best times after the page's own, such as
	 * "Vorkath kills: 50, 120, 34", each in the member's colour, with their name when the mouse is over it.
	 */
	private void drawGroupValues(Graphics2D graphics, Point mouse)
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget[] lines = header == null || header.isHidden() ? null : header.getDynamicChildren();
		if (lines == null)
		{
			return;
		}
		String boss = null;
		Widget killsLine = null;
		Widget bestLine = null;
		for (Widget line : lines)
		{
			String text = line.getText() == null ? "" : Text.removeTags(line.getText()).trim();
			String named = bossOf(text);
			if (named != null)
			{
				boss = named;
				killsLine = line;
			}
			else if (text.toLowerCase(Locale.ROOT).startsWith(PERSONAL_BEST))
			{
				bestLine = line;
			}
		}
		if (boss == null)
		{
			return;
		}
		String me = collector.getMemberId();
		List<GroupIndex.BossRecord> others = new ArrayList<>();
		for (GroupIndex.BossRecord record : index.bossRecords(boss))
		{
			// the page already shows our own
			if (!record.getMemberId().equals(me))
			{
				others.add(record);
			}
		}

		// the records come fastest first, which is the order the times go in
		List<Shown> times = new ArrayList<>();
		for (GroupIndex.BossRecord record : others)
		{
			if (record.getBestTime() != null)
			{
				times.add(new Shown(KillCountsData.formatTime(record.getBestTime()), record));
			}
		}
		List<Shown> kills = new ArrayList<>();
		others.sort(Comparator.comparing((GroupIndex.BossRecord record) -> record.getKills() == null ? 0 : record.getKills()).reversed());
		for (GroupIndex.BossRecord record : others)
		{
			if (record.getKills() != null && record.getKills() > 0)
			{
				kills.add(new Shown(QuantityFormatter.formatNumber(record.getKills()), record));
			}
		}
		append(graphics, killsLine, kills, mouse);
		if (bestLine != null)
		{
			append(graphics, bestLine, times, mouse);
		}
	}

	/**
	 * Draw values after a line of the page's own text, each after a comma in the line's colour.
	 */
	private void append(Graphics2D graphics, Widget line, List<Shown> values, Point mouse)
	{
		FontTypeFace gameFont = line.getFont();
		if (values.isEmpty() || line.isHidden() || gameFont == null)
		{
			return;
		}
		graphics.setFont(fontLike(line.getFontId()));
		FontMetrics metrics = graphics.getFontMetrics();
		Rectangle bounds = line.getBounds();
		int textWidth = gameFont.getTextWidth(line.getText());
		int x = bounds.x + textWidth;
		if (line.getXTextAlignment() == WidgetTextAlignment.CENTER)
		{
			x += (bounds.width - textWidth) / 2;
		}
		else if (line.getXTextAlignment() == WidgetTextAlignment.RIGHT)
		{
			x += bounds.width - textWidth;
		}
		int lineHeight = line.getLineHeight() > 0 ? line.getLineHeight() : metrics.getHeight();
		int baseline = bounds.y + gameFont.getBaseline();
		if (line.getYTextAlignment() == WidgetTextAlignment.CENTER)
		{
			baseline += (bounds.height - lineHeight) / 2;
		}
		else if (line.getYTextAlignment() == WidgetTextAlignment.BOTTOM)
		{
			baseline += bounds.height - lineHeight;
		}
		Color comma = new Color(line.getTextColor());
		boolean shadow = line.getTextShadowed();
		for (Shown value : values)
		{
			draw(graphics, ",", x, baseline, comma, shadow);
			x += metrics.stringWidth(", ");
			int width = metrics.stringWidth(value.getText());
			draw(graphics, value.getText(), x, baseline, GroupHudOverlay.readable(value.getRecord().getColor()), shadow);
			Rectangle area = new Rectangle(x, baseline - metrics.getAscent(), width, metrics.getHeight());
			if (mouse != null && !client.isMenuOpen() && area.contains(mouse.getX(), mouse.getY()))
			{
				tooltipManager.add(new Tooltip(value.getRecord().getName()));
			}
			x += width;
		}
	}

	/**
	 * @return RuneLite's copy of the game font a line is written in, so what's added matches it
	 */
	private static Font fontLike(int fontId)
	{
		switch (fontId)
		{
			case FontID.PLAIN_12:
				return FontManager.getRunescapeFont();
			case FontID.BOLD_12:
				return FontManager.getRunescapeBoldFont();
			default:
				return FontManager.getRunescapeSmallFont();
		}
	}

	private static void draw(Graphics2D graphics, String text, int x, int y, Color color, boolean shadow)
	{
		if (shadow)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, x + 1, y + 1);
		}
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	/**
	 * @return the boss a page is for, from its kill count line such as "Vorkath kills: 50", or null for any other line
	 */
	static String bossOf(String line)
	{
		Matcher m = HEADER_KILLS.matcher(line);
		return m.matches() ? m.group(1) : null;
	}

	/**
	 * @return the list of items on show: the results of a search, or else a page
	 */
	private Widget itemList()
	{
		Widget results = client.getWidget(InterfaceID.Collection.SEARCH_RESULTS);
		return results != null && !results.isHidden() ? results : client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
	}

	private Rectangle visibleArea(Widget items)
	{
		Widget search = client.getWidget(InterfaceID.Collection.SEARCH_CONTAINER);
		return search != null && !search.isHidden() ? items.getBounds().intersection(search.getBounds()) : items.getBounds();
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
