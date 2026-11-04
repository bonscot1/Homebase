package com.homebase.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * The items in a container, five across on the game's brown tiles, with name and quantity tooltips.
 * Empty slots are left out, so a few items take a single row.
 */
class ItemGrid extends JPanel
{
	/**
	 * The brown of the game's inventory and bank, and the darker lines between tiles.
	 */
	private static final Color BROWN = new Color(62, 53, 41);
	private static final Color BROWN_EDGE = new Color(42, 36, 28);

	private static final int COLUMNS = 5;
	private static final int TILE_WIDTH = Constants.ITEM_SPRITE_WIDTH;
	private static final int TILE_HEIGHT = Constants.ITEM_SPRITE_HEIGHT;

	/**
	 * The width of the lines around and between the tiles.
	 */
	private static final int LINE = 1;
	private static final int COLUMN_STEP = TILE_WIDTH + LINE;
	private static final int ROW_STEP = TILE_HEIGHT + LINE;

	/**
	 * @param items the container's slots, with null for empty ones
	 */
	ItemGrid(List<Item> items, ItemSprites sprites)
	{
		super(new FlowLayout(FlowLayout.CENTER, 0, 0));
		setOpaque(false);

		List<Item> shown = new ArrayList<>();
		for (Item item : items)
		{
			if (item != null)
			{
				shown.add(item);
			}
		}
		add(new Tiles(shown, sprites));
	}

	/**
	 * @return how many of the slots hold something
	 */
	static int countFilled(List<Item> slots)
	{
		int count = 0;
		for (Item item : slots)
		{
			if (item != null)
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * @return the rows needed to show this many items
	 */
	static int rowsFor(int itemCount)
	{
		return (itemCount + COLUMNS - 1) / COLUMNS;
	}

	/**
	 * @return which item is at a pixel of the grid, or -1 if it's a line or an empty tile
	 */
	static int itemAt(int x, int y, int itemCount)
	{
		int column = (x - LINE) / COLUMN_STEP;
		if (x < LINE || y < LINE || column >= COLUMNS)
		{
			return -1;
		}
		int item = (y - LINE) / ROW_STEP * COLUMNS + column;
		return item < itemCount ? item : -1;
	}

	/**
	 * The tiles themselves, painted as one component rather than a label each. Pictures and names are
	 * only asked for when their tile is first painted, and Swing only paints what's scrolled into view,
	 * so a full bank costs the game no more than the few dozen items on screen.
	 */
	static final class Tiles extends JComponent
	{
		private final List<Item> items;
		private final ItemSprites sprites;
		private final AsyncBufferedImage[] pictures;
		private final BitSet namesRequested = new BitSet();
		private final int rows;

		Tiles(List<Item> items, ItemSprites sprites)
		{
			this.items = items;
			this.sprites = sprites;
			pictures = new AsyncBufferedImage[items.size()];
			rows = rowsFor(items.size());

			Dimension size = new Dimension(LINE + COLUMNS * COLUMN_STEP, LINE + rows * ROW_STEP);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
			setOpaque(true);
			ToolTipManager.sharedInstance().registerComponent(this);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Rectangle clip = g.getClipBounds();
			if (clip == null)
			{
				clip = new Rectangle(getPreferredSize());
			}
			g.setColor(BROWN_EDGE);
			g.fillRect(clip.x, clip.y, clip.width, clip.height);

			int firstRow = Math.max(0, (clip.y - LINE) / ROW_STEP);
			int lastRow = Math.min(rows - 1, (clip.y + clip.height - 1 - LINE) / ROW_STEP);
			List<Integer> unnamed = new ArrayList<>();
			for (int row = firstRow; row <= lastRow; row++)
			{
				for (int column = 0; column < COLUMNS; column++)
				{
					int x = LINE + column * COLUMN_STEP;
					int y = LINE + row * ROW_STEP;
					g.setColor(BROWN);
					g.fillRect(x, y, TILE_WIDTH, TILE_HEIGHT);

					// the last row is filled out with empty tiles so the grid stays even
					int item = row * COLUMNS + column;
					if (item < items.size())
					{
						g.drawImage(picture(item, x, y), x, y, null);
						if (!namesRequested.get(item))
						{
							namesRequested.set(item);
							unnamed.add(items.get(item).getId());
						}
					}
				}
			}
			if (!unnamed.isEmpty())
			{
				sprites.lookUpNames(unnamed);
			}
		}

		private AsyncBufferedImage picture(int item, int x, int y)
		{
			AsyncBufferedImage picture = pictures[item];
			if (picture == null)
			{
				picture = sprites.picture(items.get(item));
				pictures[item] = picture;
				picture.onLoaded(() -> repaint(x, y, TILE_WIDTH, TILE_HEIGHT));
			}
			return picture;
		}

		@Override
		public String getToolTipText(MouseEvent event)
		{
			int index = itemAt(event.getX(), event.getY(), items.size());
			if (index < 0)
			{
				return null;
			}
			Item item = items.get(index);
			String name = ItemNames.get(item.getId());
			return item.getQuantity() > 1 ? name + " ×" + QuantityFormatter.formatNumber(item.getQuantity()) : name;
		}
	}
}
