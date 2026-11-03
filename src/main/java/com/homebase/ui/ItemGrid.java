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
 * A grid of item icons on the game's brown, with name and quantity tooltips.
 */
class ItemGrid extends JPanel
{
	enum Look
	{
		/**
		 * The inventory as the game shows it: all 28 slots, four across, as one brown panel.
		 */
		INVENTORY,

		/**
		 * A bank-like container: only what's in it, five across, each item on its own tile.
		 */
		CONTAINER,
	}

	/**
	 * The brown of the game's inventory and its darker edge.
	 */
	static final Color BROWN = new Color(62, 53, 41);
	static final Color BROWN_EDGE = new Color(42, 36, 28);

	private static final int INVENTORY_SLOTS = 28;

	ItemGrid(List<Item> items, Look look, ItemManager itemManager, ClientThread clientThread)
	{
		super(new FlowLayout(FlowLayout.CENTER, 0, 0));
		setOpaque(false);

		boolean inventory = look == Look.INVENTORY;
		List<Item> slots = new ArrayList<>();
		for (Item item : items)
		{
			if (item != null || inventory)
			{
				slots.add(item);
			}
		}
		while (inventory && slots.size() < INVENTORY_SLOTS)
		{
			slots.add(null);
		}

		JPanel grid = new JPanel(inventory ? new GridLayout(0, 4, 0, 0) : new GridLayout(0, 5, 1, 1));
		grid.setBackground(inventory ? BROWN : BROWN_EDGE);
		grid.setBorder(BorderFactory.createLineBorder(BROWN_EDGE, inventory ? 2 : 1));
		Dimension size = inventory ? new Dimension(42, 36) : new Dimension(36, 32);

		List<JLabel> labels = new ArrayList<>();
		List<Item> shown = new ArrayList<>();
		for (Item item : slots)
		{
			JLabel label = new JLabel();
			label.setPreferredSize(size);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setOpaque(!inventory);
			label.setBackground(BROWN);
			if (item != null)
			{
				itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1).addTo(label);
				labels.add(label);
				shown.add(item);
			}
			grid.add(label);
		}
		// a bank's last row is filled out with empty tiles so the grid stays even
		while (!inventory && grid.getComponentCount() % 5 != 0)
		{
			JLabel blank = new JLabel();
			blank.setPreferredSize(size);
			blank.setOpaque(true);
			blank.setBackground(BROWN);
			grid.add(blank);
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
}
