package com.homebase.ui;

import java.awt.Color;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import lombok.Value;
import net.runelite.api.Item;

/**
 * Items shown in blocks: a heading, a line in colour saying how they stand, and the items as pictures
 * with their amounts. Everything listed on the Storage tab looks like this.
 */
final class ItemBlocks
{
	@Value
	static class Block
	{
		String heading;

		/**
		 * Shown under the heading in {@link #detailColor}, or null for nothing.
		 */
		String detail;
		Color detailColor;
		List<Item> items;
		ItemGrid.Marks marks;
	}

	private ItemBlocks()
	{
	}

	static Block of(String heading, String detail, Color detailColor, List<Item> items)
	{
		return new Block(heading, detail, detailColor, items, ItemGrid.NO_MARKS);
	}

	static int itemsIn(List<Block> blocks)
	{
		return blocks.stream().mapToInt(block -> block.getItems().size()).sum();
	}

	/**
	 * @return the blocks one under another. Their text is escaped, so names other players chose can go
	 * straight in.
	 */
	static JPanel list(List<Block> blocks, ItemSprites sprites)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		for (Block block : blocks)
		{
			if (list.getComponentCount() > 0)
			{
				list.add(Box.createVerticalStrut(8));
			}
			list.add(Ui.wrapped(Ui.escapeHtml(block.getHeading()), Color.WHITE, StorageHistoryView.TEXT_WIDTH));
			if (block.getDetail() != null)
			{
				list.add(Ui.wrapped(Ui.escapeHtml(block.getDetail()), block.getDetailColor(), StorageHistoryView.TEXT_WIDTH));
			}
			list.add(Box.createVerticalStrut(3));
			list.add(new ItemGrid(block.getItems(), sprites, block.getMarks()));
		}
		Ui.alignChildrenLeft(list);
		return list;
	}
}
