package com.homebase.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.client.util.AsyncBufferedImage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ItemGridTest
{
	private static final int COLUMN_STEP = 37;
	private static final int ROW_STEP = 33;

	/**
	 * Counts what the grid asks for, with no game behind it.
	 */
	private static final class CountingSprites extends ItemSprites
	{
		final Set<Integer> pictureIds = new HashSet<>();
		final Set<Integer> namedIds = new HashSet<>();
		int loads;

		CountingSprites()
		{
			super(null, null);
		}

		@Override
		AsyncBufferedImage load(Item item)
		{
			loads++;
			pictureIds.add(item.getId());
			return new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
		}

		@Override
		void lookUpNames(Collection<Integer> itemIds)
		{
			namedIds.addAll(itemIds);
		}
	}

	private static List<Item> items(int count)
	{
		List<Item> items = new ArrayList<>();
		for (int id = 1; id <= count; id++)
		{
			items.add(new Item(id, 1));
		}
		return items;
	}

	private static void paint(ItemGrid.Tiles tiles, int clipY, int clipHeight)
	{
		Dimension size = tiles.getPreferredSize();
		BufferedImage canvas = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setClip(0, clipY, size.width, clipHeight);
		tiles.paintComponent(g);
		g.dispose();
	}

	@Test
	public void aFullBankOnlyAsksForWhatsOnScreen()
	{
		CountingSprites sprites = new CountingSprites();
		ItemGrid.Tiles tiles = new ItemGrid.Tiles(items(1000), sprites);

		// six rows' height of a bank, scrolled to the top
		paint(tiles, 0, 6 * ROW_STEP);
		assertTrue("asked for " + sprites.loads, sprites.loads <= 7 * 5);
		assertEquals(sprites.pictureIds, sprites.namedIds);

		// scrolling down asks for the new rows only
		int before = sprites.loads;
		paint(tiles, 0, 6 * ROW_STEP);
		assertEquals(before, sprites.loads);
		paint(tiles, 100 * ROW_STEP, 6 * ROW_STEP);
		assertTrue(sprites.pictureIds.contains(501));
		assertTrue(sprites.loads <= before + 7 * 5);
	}

	@Test
	public void refreshingThePanelDoesntReloadPictures()
	{
		CountingSprites sprites = new CountingSprites();
		paint(new ItemGrid.Tiles(items(50), sprites), 0, 6 * ROW_STEP);
		int loaded = sprites.loads;

		// a rebuild makes a new grid over the same sprites
		paint(new ItemGrid.Tiles(items(50), sprites), 0, 6 * ROW_STEP);
		assertEquals(loaded, sprites.loads);
	}

	@Test
	public void tilesKeepTheirSize()
	{
		ItemGrid.Tiles tiles = new ItemGrid.Tiles(items(7), new CountingSprites());
		assertEquals(new Dimension(1 + 5 * COLUMN_STEP, 1 + 2 * ROW_STEP), tiles.getPreferredSize());
		assertEquals(0, ItemGrid.rowsFor(0));
		assertEquals(1, ItemGrid.rowsFor(5));
		assertEquals(2, ItemGrid.rowsFor(6));
	}

	@Test
	public void findsTheItemUnderTheMouse()
	{
		assertEquals(0, ItemGrid.itemAt(1, 1, 7));
		assertEquals(4, ItemGrid.itemAt(1 + 4 * COLUMN_STEP, 1, 7));
		assertEquals(6, ItemGrid.itemAt(1 + COLUMN_STEP, 1 + ROW_STEP, 7));
		// an empty tile at the end of the last row, and the outer line
		assertEquals(-1, ItemGrid.itemAt(1 + 2 * COLUMN_STEP, 1 + ROW_STEP, 7));
		assertEquals(-1, ItemGrid.itemAt(0, 5, 7));
	}

	@Test
	public void tooltipsNameTheItemAndAmount()
	{
		List<Item> items = new ArrayList<>();
		items.add(new Item(4151, 1));
		items.add(new Item(995, 1200));
		ItemGrid.Tiles tiles = new ItemGrid.Tiles(items, new CountingSprites());

		assertEquals(ItemNames.get(4151), tiles.getToolTipText(mouseAt(tiles, 5, 5)));
		String stack = tiles.getToolTipText(mouseAt(tiles, 5 + COLUMN_STEP, 5));
		assertNotNull(stack);
		assertTrue(stack, stack.startsWith(ItemNames.get(995) + " ×"));
		assertNull(tiles.getToolTipText(mouseAt(tiles, 5 + 2 * COLUMN_STEP, 5)));
	}

	private static MouseEvent mouseAt(ItemGrid.Tiles tiles, int x, int y)
	{
		return new MouseEvent(tiles, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false);
	}
}
