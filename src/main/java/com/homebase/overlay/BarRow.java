package com.homebase.overlay;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;

/**
 * Bars side by side on one line of an overlay panel. RuneLite's own panel squashes a row of bars to
 * nothing, and sizes a new one a frame late, so this row lays itself out.
 */
class BarRow implements LayoutableRenderableEntity
{
	private final List<ProgressBarComponent> bars;
	private final int barWidth;
	private final int barHeight;
	private final int gap;

	/**
	 * Space above and below the bars, so they don't touch the lines around them.
	 */
	private final int top;
	private final int bottom;

	private final Rectangle bounds = new Rectangle();
	private Point location = new Point();

	BarRow(List<ProgressBarComponent> bars, int barWidth, int barHeight, int gap, int top, int bottom)
	{
		this.bars = bars;
		this.barWidth = barWidth;
		this.barHeight = barHeight;
		this.gap = gap;
		this.top = top;
		this.bottom = bottom;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int x = location.x;
		int tallest = 0;
		for (ProgressBarComponent bar : bars)
		{
			bar.setPreferredLocation(new Point(x, location.y + top));
			bar.setPreferredSize(new Dimension(barWidth, barHeight));
			tallest = Math.max(tallest, bar.render(graphics).height);
			x += barWidth + gap;
		}
		Dimension size = new Dimension(Math.max(0, x - gap - location.x), top + tallest + bottom);
		bounds.setLocation(location);
		bounds.setSize(size);
		return size;
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point location)
	{
		this.location = location;
	}

	@Override
	public void setPreferredSize(Dimension size)
	{
		// the bars decide the row's size
	}
}
