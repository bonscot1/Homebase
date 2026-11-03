package com.homebase.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.Item;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
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
	static final Color BROWN = new Color(62, 53, 41);
	static final Color BROWN_EDGE = new Color(42, 36, 28);

	private static final int COLUMNS = 5;
	private static final Dimension SLOT = new Dimension(36, 32);

	ItemGrid(List<Item> items, ItemManager itemManager, ClientThread clientThread)
	{
		super(new FlowLayout(FlowLayout.CENTER, 0, 0));
		setOpaque(false);

		JPanel grid = new JPanel(new GridLayout(0, COLUMNS, 1, 1));
		grid.setBackground(BROWN_EDGE);
		grid.setBorder(BorderFactory.createLineBorder(BROWN_EDGE, 1));

		List<JLabel> labels = new ArrayList<>();
		List<Item> shown = new ArrayList<>();
		for (Item item : items)
		{
			if (item == null)
			{
				continue;
			}
			JLabel label = tile();
			itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1).addTo(label);
			labels.add(label);
			shown.add(item);
			grid.add(label);
		}
		// the last row is filled out with empty tiles so the grid stays even
		while (grid.getComponentCount() % COLUMNS != 0)
		{
			grid.add(tile());
		}
		add(grid);

		List<Integer> ids = new ArrayList<>();
		for (Item item : shown)
		{
			ids.add(item.getId());
		}
		ItemNames.resolve(ids, itemManager, clientThread, () ->
		{
			for (int i = 0; i < labels.size(); i++)
			{
				Item item = shown.get(i);
				String name = ItemNames.get(item.getId());
				labels.get(i).setToolTipText(item.getQuantity() > 1
					? name + " ×" + QuantityFormatter.formatNumber(item.getQuantity())
					: name);
			}
		});
	}

	private static JLabel tile()
	{
		JLabel label = new JLabel();
		label.setPreferredSize(SLOT);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setOpaque(true);
		label.setBackground(BROWN);
		return label;
	}
}
