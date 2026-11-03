package com.homebase.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * What a member is wearing, laid out like the game's worn equipment tab: each slot on the game's
 * stone tile, with the slot's faint outline where nothing is worn.
 */
class EquipmentGrid extends JPanel
{
	private static final int SLOT = 36;

	/**
	 * The worn equipment tab's layout, three across and five down; null is a gap.
	 */
	private static final EquipmentInventorySlot[] LAYOUT = {
		null, EquipmentInventorySlot.HEAD, null,
		EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET, EquipmentInventorySlot.AMMO,
		EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD,
		null, EquipmentInventorySlot.LEGS, null,
		EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING,
	};

	EquipmentGrid(List<Item> worn, ItemManager itemManager, ClientThread clientThread, GameSprites sprites)
	{
		super(new FlowLayout(FlowLayout.CENTER, 0, 0));
		setOpaque(false);

		JPanel grid = new JPanel(new GridLayout(5, 3, 6, 4));
		grid.setOpaque(false);
		List<Slot> filled = new ArrayList<>();
		for (EquipmentInventorySlot place : LAYOUT)
		{
			if (place == null)
			{
				JPanel gap = new JPanel();
				gap.setOpaque(false);
				grid.add(gap);
				continue;
			}
			int idx = place.getSlotIdx();
			Item item = idx < worn.size() ? worn.get(idx) : null;
			Slot slot = new Slot(item, emptyIcon(place), itemManager, sprites);
			if (item != null)
			{
				filled.add(slot);
			}
			grid.add(slot);
		}
		add(grid);

		List<Integer> ids = new ArrayList<>();
		for (Slot slot : filled)
		{
			ids.add(slot.item.getId());
		}
		ItemNames.resolve(ids, itemManager, clientThread, () ->
		{
			for (Slot slot : filled)
			{
				Item item = slot.item;
				String name = ItemNames.get(item.getId());
				slot.setToolTipText(item.getQuantity() > 1 ? name + " ×" + QuantityFormatter.formatNumber(item.getQuantity()) : name);
			}
		});
	}

	private static int emptyIcon(EquipmentInventorySlot place)
	{
		switch (place)
		{
			case HEAD:
				return SpriteID.Wornicons.HEAD;
			case CAPE:
				return SpriteID.Wornicons.CAPE;
			case AMULET:
				return SpriteID.Wornicons.NECK;
			case AMMO:
				return SpriteID.Wornicons.AMMUNITION;
			case WEAPON:
				return SpriteID.Wornicons.WEAPON;
			case BODY:
				return SpriteID.Wornicons.TORSO;
			case SHIELD:
				return SpriteID.Wornicons.SHIELD;
			case LEGS:
				return SpriteID.Wornicons.LEGS;
			case GLOVES:
				return SpriteID.Wornicons.HANDS;
			case BOOTS:
				return SpriteID.Wornicons.FEET;
			default:
				return SpriteID.Wornicons.RING;
		}
	}

	/**
	 * One equipment slot: the stone tile, then the item or the slot's outline.
	 */
	private static final class Slot extends JComponent
	{
		private static final Color FALLBACK = new Color(62, 53, 41);

		private final Item item;
		private final int emptyIcon;
		private final GameSprites sprites;
		private final AsyncBufferedImage image;

		Slot(Item item, int emptyIcon, ItemManager itemManager, GameSprites sprites)
		{
			this.item = item;
			this.emptyIcon = emptyIcon;
			this.sprites = sprites;
			setPreferredSize(new Dimension(SLOT, SLOT));
			if (item != null)
			{
				image = itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1);
				image.onLoaded(this::repaint);
			}
			else
			{
				image = null;
			}
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			BufferedImage tile = sprites.get(SpriteID.Miscgraphics.EQUIPMENT_SLOT_TILE, this);
			if (tile != null)
			{
				g.drawImage(tile, (getWidth() - tile.getWidth()) / 2, (getHeight() - tile.getHeight()) / 2, null);
			}
			else
			{
				g.setColor(FALLBACK);
				g.fillRect(0, 0, getWidth(), getHeight());
			}

			BufferedImage top = image != null ? image : sprites.get(emptyIcon, this);
			if (top != null)
			{
				g.drawImage(top, (getWidth() - top.getWidth()) / 2, (getHeight() - top.getHeight()) / 2, null);
			}
		}
	}
}
