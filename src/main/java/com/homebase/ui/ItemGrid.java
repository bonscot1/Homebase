package com.homebase.ui;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Item;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

/**
 * A grid of item icons with name and quantity tooltips.
 */
class ItemGrid extends JPanel
{
	private static final Dimension SLOT_SIZE = new Dimension(36, 32);

	/**
	 * Item names, shared by every grid. Names can only be looked up on the client thread.
	 */
	private static final Map<Integer, String> NAMES = new ConcurrentHashMap<>();

	/**
	 * @param keepEmptySlots true to keep the slot layout, as for the inventory
	 */
	ItemGrid(List<Item> items, int columns, boolean keepEmptySlots, ItemManager itemManager, ClientThread clientThread)
	{
		setLayout(new GridLayout(0, columns, 1, 1));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		List<JLabel> labels = new ArrayList<>();
		List<Item> shown = new ArrayList<>();
		for (Item item : items)
		{
			if (item == null && !keepEmptySlots)
			{
				continue;
			}
			JLabel label = new JLabel();
			label.setPreferredSize(SLOT_SIZE);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setOpaque(true);
			label.setBackground(ColorScheme.DARK_GRAY_COLOR);
			if (item != null)
			{
				itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1).addTo(label);
				labels.add(label);
				shown.add(item);
			}
			add(label);
		}

		List<Integer> missing = new ArrayList<>();
		for (Item item : shown)
		{
			if (!NAMES.containsKey(item.getId()))
			{
				missing.add(item.getId());
			}
		}
		if (missing.isEmpty())
		{
			applyTooltips(labels, shown);
			return;
		}
		clientThread.invokeLater(() ->
		{
			for (int id : missing)
			{
				NAMES.put(id, itemManager.getItemComposition(id).getName());
			}
			SwingUtilities.invokeLater(() -> applyTooltips(labels, shown));
		});
	}

	private static void applyTooltips(List<JLabel> labels, List<Item> items)
	{
		for (int i = 0; i < labels.size(); i++)
		{
			Item item = items.get(i);
			String name = NAMES.getOrDefault(item.getId(), "Item " + item.getId());
			labels.get(i).setToolTipText(item.getQuantity() > 1
				? name + " x " + QuantityFormatter.formatNumber(item.getQuantity())
				: name);
		}
	}
}
