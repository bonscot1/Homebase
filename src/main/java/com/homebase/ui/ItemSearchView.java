package com.homebase.ui;

import com.homebase.data.Clean;
import com.homebase.data.GroupView;
import com.homebase.data.ItemsData;
import com.homebase.data.ProfileData;
import com.homebase.data.Section;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

/**
 * What the search box above the tabs finds: each matching item, and who has how many of it where. Only
 * containers the group shares count, as they were when each was last seen.
 */
class ItemSearchView extends JPanel
{
	private static final int MAX_RESULTS = 40;
	private static final int MAX_NAME = 20;
	private static final int ICON_GAP = 6;

	/**
	 * Each member's containers, by how they're named in the results.
	 */
	private static final Map<Section, String> PLACES = new LinkedHashMap<>();

	static
	{
		PLACES.put(Section.INVENTORY, "Inventory");
		PLACES.put(Section.EQUIPMENT, "Worn");
		PLACES.put(Section.BANK, "Bank");
		PLACES.put(Section.SEED_VAULT, "Seed vault");
		PLACES.put(Section.LOOTING_BAG, "Looting bag");
	}

	private final ItemSprites sprites;
	private final Supplier<GroupView> viewSource;
	private final Predicate<ProfileData> belongs;

	/**
	 * Everything searchable, or null if the group's data has changed since it was gathered.
	 */
	private List<ItemSearch.Holding> holdings;
	private String shownQuery;

	ItemSearchView(ItemSprites sprites, Supplier<GroupView> viewSource, Predicate<ProfileData> belongs)
	{
		this.sprites = sprites;
		this.viewSource = viewSource;
		this.belongs = belongs;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
	}

	/**
	 * The group's data changed, so gather it again next time.
	 */
	void dataChanged()
	{
		holdings = null;
	}

	void search(String query)
	{
		if (holdings == null)
		{
			holdings = gather();
		}
		shownQuery = query;
		List<ItemSearch.Holding> searched = holdings;
		Set<Integer> ids = new HashSet<>();
		for (ItemSearch.Holding holding : searched)
		{
			ids.add(holding.getItemId());
		}
		// names are looked up on the game's thread, a few at a time, the first time they're needed
		sprites.lookUpNames(ids, () ->
		{
			// they may have typed something else by then
			if (query.equals(shownQuery))
			{
				show(query, searched);
			}
		});
	}

	private List<ItemSearch.Holding> gather()
	{
		List<ItemSearch.Holding> out = new ArrayList<>();
		GroupView view = viewSource.get();
		if (view == null)
		{
			return out;
		}
		addHoldings(out, "Group storage", "Group storage", view.get(null, Section.GROUP_STORAGE, ItemsData.class));
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			String name = profile == null ? null : Clean.text(profile.getName(), MAX_NAME);
			if (name == null || !belongs.test(profile))
			{
				continue;
			}
			PLACES.forEach((section, place) -> addHoldings(out, name, place, view.get(id, section, ItemsData.class)));
		}
		return out;
	}

	private static void addHoldings(List<ItemSearch.Holding> out, String owner, String place, ItemsData items)
	{
		if (items == null)
		{
			return;
		}
		for (Item item : items.toItems())
		{
			if (item != null)
			{
				out.add(new ItemSearch.Holding(owner, place, item.getId(), item.getQuantity()));
			}
		}
	}

	private void show(String query, List<ItemSearch.Holding> searched)
	{
		removeAll();
		List<ItemSearch.Result> results = ItemSearch.find(query, searched, ItemNames::known, MAX_RESULTS);
		if (results.isEmpty())
		{
			add(Ui.empty("Nothing found", "Nobody in your group has shown an item with that name."));
		}
		for (ItemSearch.Result result : results)
		{
			add(card(result));
			add(Box.createVerticalStrut(Ui.GAP));
		}
		if (results.size() == MAX_RESULTS)
		{
			add(Ui.muted("Showing the first " + MAX_RESULTS + " items"));
		}
		Ui.alignChildrenLeft(this);
		revalidate();
		repaint();
	}

	private JPanel card(ItemSearch.Result result)
	{
		JPanel card = Ui.card();

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.TOP);
		// one of the item, so its picture has no number on it
		sprites.picture(new Item(result.getItemId(), 1)).addTo(icon);
		icon.setBorder(new EmptyBorder(0, 0, 0, ICON_GAP));
		card.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.add(Ui.wrapped(Ui.escapeHtml(result.getName()) + " ×" + QuantityFormatter.formatNumber(result.getTotal()), Color.WHITE));
		for (Map.Entry<String, Map<String, Long>> owner : result.getOwners().entrySet())
		{
			text.add(Ui.wrapped(Ui.escapeHtml(describe(owner.getKey(), owner.getValue())), ColorScheme.LIGHT_GRAY_COLOR));
		}
		Ui.alignChildrenLeft(text);
		card.add(text, BorderLayout.CENTER);
		return card;
	}

	/**
	 * @return a line such as "Mundell: Bank 5, Worn 1", or "Group storage: 5" where the two are the same
	 */
	static String describe(String owner, Map<String, Long> places)
	{
		StringJoiner where = new StringJoiner(", ");
		places.forEach((place, quantity) -> where.add(
			(place.equals(owner) ? "" : place + " ") + QuantityFormatter.formatNumber(quantity)));
		return owner + ": " + where;
	}
}
