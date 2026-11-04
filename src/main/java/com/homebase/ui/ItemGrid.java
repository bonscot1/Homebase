package com.homebase.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.client.ui.ColorScheme;
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
	 * The dot on an item that's marked as needing to be returned, in the corner the quantity doesn't use.
	 */
	private static final int MARK_SIZE = 6;

	/**
	 * Items that can be marked as needing to be returned, by right-clicking them.
	 */
	static final class Marks
	{
		private final Set<Integer> marked;
		private final IntConsumer mark;
		private final IntConsumer unmark;

		/**
		 * @param mark called when an item is marked, or null if they can only be looked at for now
		 */
		Marks(Set<Integer> marked, IntConsumer mark, IntConsumer unmark)
		{
			this.marked = marked;
			this.mark = mark;
			this.unmark = unmark;
		}
	}

	private static final Marks NO_MARKS = new Marks(Collections.emptySet(), null, null);

	/**
	 * @param items the container's slots, with null for empty ones
	 */
	ItemGrid(List<Item> items, ItemSprites sprites)
	{
		this(items, sprites, NO_MARKS);
	}

	ItemGrid(List<Item> items, ItemSprites sprites, Marks marks)
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
		add(new Tiles(shown, sprites, marks));
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
		private final Marks marks;
		private final AsyncBufferedImage[] pictures;
		private final BitSet namesRequested = new BitSet();
		private final int rows;

		Tiles(List<Item> items, ItemSprites sprites)
		{
			this(items, sprites, NO_MARKS);
		}

		Tiles(List<Item> items, ItemSprites sprites, Marks marks)
		{
			this.items = items;
			this.sprites = sprites;
			this.marks = marks;
			pictures = new AsyncBufferedImage[items.size()];
			rows = rowsFor(items.size());

			Dimension size = new Dimension(LINE + COLUMNS * COLUMN_STEP, LINE + rows * ROW_STEP);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
			setOpaque(true);
			ToolTipManager.sharedInstance().registerComponent(this);
			if (marks.mark != null)
			{
				addMouseListener(new MouseAdapter()
				{
					// which of these it comes on differs between systems
					@Override
					public void mousePressed(MouseEvent e)
					{
						showMenu(e);
					}

					@Override
					public void mouseReleased(MouseEvent e)
					{
						showMenu(e);
					}
				});
			}
		}

		private void showMenu(MouseEvent e)
		{
			int index = e.isPopupTrigger() ? itemAt(e.getX(), e.getY(), items.size()) : -1;
			if (index < 0)
			{
				return;
			}
			int id = items.get(index).getId();
			(marks.marked.contains(id)
				? Ui.rightClickMenu(StorageHistoryView.UNMARK, () -> marks.unmark.accept(id))
				: Ui.rightClickMenu("Needs returning", () -> marks.mark.accept(id))).show(this, e.getX(), e.getY());
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
						if (marks.marked.contains(items.get(item).getId()))
						{
							g.setColor(ColorScheme.BRAND_ORANGE);
							g.fillOval(x + TILE_WIDTH - MARK_SIZE - 1, y + TILE_HEIGHT - MARK_SIZE - 1, MARK_SIZE, MARK_SIZE);
						}
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
				sprites.lookUpNames(unnamed, () ->
				{
				});
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
			String tip = item.getQuantity() > 1 ? name + " ×" + QuantityFormatter.formatNumber(item.getQuantity()) : name;
			return marks.marked.contains(item.getId()) ? tip + " (needs returning)" : tip;
		}
	}
}
