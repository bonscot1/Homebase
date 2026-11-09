package com.homebase.ui;

import com.homebase.HomebasePlugin;
import com.homebase.bounties.BountyService;
import com.homebase.data.BountyData;
import com.homebase.data.Clean;
import com.homebase.data.GroupView;
import com.homebase.data.ItemsData;
import com.homebase.data.ProfileData;
import com.homebase.data.Section;
import com.homebase.data.StorageLogData;
import com.homebase.goals.GoalService;
import com.homebase.requests.RequestService;
import com.homebase.storage.ReturnTags;
import com.homebase.storage.StorageHistory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Item;
import net.runelite.client.ui.ColorScheme;

/**
 * Everything about group storage in one place: what's owed back, what's under a bounty or held for a
 * request, the items themselves and who has put in or taken out what. Bounties and requests are only
 * summed up here, and are changed on their own tabs.
 */
class StorageView extends JPanel
{
	private static final int MAX_NAME = 20;

	/**
	 * The colours of the padlocks in group storage, for a bounty that's holding items back and one that isn't.
	 */
	private static final Color LOCKED = new Color(0xEF5350);
	private static final Color FREE = new Color(0x66BB6A);

	private static final String BOUNTIES_ID = "storage:bounties";
	private static final String HELD_ID = "storage:held";
	private static final String ITEMS_ID = "storage:items";

	private final HomebasePlugin plugin;
	private final Supplier<GroupView> views;
	private final ReturnTags returnTags;
	private final BountyService bounties;
	private final GoalService goals;
	private final RequestService requests;
	private final MemberCard.Context context;
	private final Runnable onNamesFound;

	StorageView(HomebasePlugin plugin, Supplier<GroupView> views, ReturnTags returnTags, BountyService bounties,
		GoalService goals, RequestService requests, MemberCard.Context context, Runnable onNamesFound)
	{
		this.plugin = plugin;
		this.views = views;
		this.returnTags = returnTags;
		this.bounties = bounties;
		this.goals = goals;
		this.requests = requests;
		this.context = context;
		this.onNamesFound = onNamesFound;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
	}

	void refresh()
	{
		removeAll();
		GroupView view = views.get();
		ItemsData storage = view == null ? null : view.get(null, Section.GROUP_STORAGE, ItemsData.class);
		if (storage == null)
		{
			add(Ui.empty("Nothing seen yet", "Group Ironman groups see their group storage here once someone in the group "
				+ "has opened it."));
			return;
		}
		List<Item> slots = storage.toItems();
		Map<Integer, Long> marked = returnTags.marked();
		boolean canMark = returnTags.canEdit();
		Map<String, String> names = memberNames(view);
		List<StorageHistory.MemberLog> logs = logs(view);
		List<StorageHistory.Owed> owed = StorageHistory.owed(logs, marked);
		List<ItemBlocks.Block> bountyBlocks = bountyBlocks(names, storedCounts(slots));
		List<ItemBlocks.Block> heldBlocks = heldBlocks(slots);
		int bountyCount = ItemBlocks.itemsIn(bountyBlocks);
		int heldCount = ItemBlocks.itemsIn(heldBlocks);

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(7, 8, 8, 8));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		card.add(header(view, ItemGrid.countFilled(slots)));
		String summary = summary(owed.size(), bountyCount, heldCount);
		if (summary != null)
		{
			card.add(Ui.wrapped(summary, ColorScheme.LIGHT_GRAY_COLOR, StorageHistoryView.TEXT_WIDTH));
		}

		StorageHistoryView.addOwed(card, owed, canMark ? returnTags::unmark : null, context, onNamesFound);
		if (!bountyBlocks.isEmpty() && MemberCard.addFoldRow(card, "Bounties", String.valueOf(bountyCount), BOUNTIES_ID, context))
		{
			MemberCard.addContent(card, ItemBlocks.list(bountyBlocks, context.itemSprites));
		}
		if (!heldBlocks.isEmpty() && MemberCard.addFoldRow(card, "Held for requests", String.valueOf(heldCount), HELD_ID, context))
		{
			MemberCard.addContent(card, ItemBlocks.list(heldBlocks, context.itemSprites));
		}

		if (MemberCard.addFoldRow(card, "Items", String.valueOf(ItemGrid.countFilled(slots)), ITEMS_ID, context))
		{
			MemberCard.addContent(card, new ItemGrid(slots, context.itemSprites,
				new ItemGrid.Marks(marked.keySet(), canMark ? returnTags::mark : null, returnTags::unmark)));
			if (marked.isEmpty() && canMark)
			{
				StorageHistoryView.addRows(card, Collections.singletonList(
					"Right-click an item that belongs in storage to have anyone who takes it owe it back."));
			}
		}
		StorageHistoryView.addHistory(card, logs, context, onNamesFound);
		Ui.alignChildrenLeft(card);
		add(card);
	}

	private JPanel header(GroupView view, int filled)
	{
		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.add(Ui.title("Group storage"), BorderLayout.CENTER);
		header.add(Ui.small(ItemGrid.itemCount(filled), ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		String author = view.authorOf(null, Section.GROUP_STORAGE);
		ProfileData seenBy = author == null ? null : view.get(author, Section.PROFILE, ProfileData.class);
		header.add(Ui.muted("Updated " + Ago.format(view.updatedAt(null, Section.GROUP_STORAGE))
			+ (seenBy == null ? "" : " by " + Clean.text(seenBy.getName(), MAX_NAME))), BorderLayout.SOUTH);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		return header;
	}

	/**
	 * @return a line saying how much is going on, or null if there's nothing to say
	 */
	static String summary(int owed, int bountyCount, int held)
	{
		List<String> parts = new ArrayList<>();
		if (owed > 0)
		{
			parts.add(owed + " owed back");
		}
		if (bountyCount > 0)
		{
			parts.add(bountyCount + (bountyCount == 1 ? " bounty" : " bounties"));
		}
		if (held > 0)
		{
			parts.add(held + " held for requests");
		}
		return parts.isEmpty() ? null : String.join("  ·  ", parts);
	}

	private Map<String, String> memberNames(GroupView view)
	{
		Map<String, String> out = new HashMap<>();
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			String name = profile != null && plugin.belongsToGroup(profile) ? Clean.text(profile.getName(), MAX_NAME) : null;
			if (name != null)
			{
				out.put(id, name);
			}
		}
		return out;
	}

	private List<StorageHistory.MemberLog> logs(GroupView view)
	{
		List<StorageHistory.MemberLog> logs = new ArrayList<>();
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			StorageLogData log = view.get(id, Section.STORAGE_LOG, StorageLogData.class);
			if (profile != null && log != null && plugin.belongsToGroup(profile))
			{
				logs.add(new StorageHistory.MemberLog(profile.getName(), log, view.updatedAt(id, Section.STORAGE_LOG)));
			}
		}
		return logs;
	}

	/**
	 * @param inStorage how many of each item group storage holds, for a bounty on all of an item
	 * @return the bounties in force, gathered by goal and by who they're locked for, with those still
	 * holding something back first
	 */
	private List<ItemBlocks.Block> bountyBlocks(Map<String, String> names, Map<Integer, Integer> inStorage)
	{
		Map<String, ItemBlocks.Block> locked = new LinkedHashMap<>();
		Map<String, ItemBlocks.Block> free = new LinkedHashMap<>();
		for (BountyService.Bounty bounty : bounties.list())
		{
			BountyData data = bounty.getData();
			GoalService.Goal goal = goals.get(data.getGoalId());
			// only someone in the group can set one, and a deleted goal holds nothing back
			if (goal == null || !names.containsKey(data.getCreatedBy()) || data.getLocked() == null)
			{
				continue;
			}
			List<String> who = new ArrayList<>();
			for (String id : data.getLocked())
			{
				if (bounties.isLocked(data, id))
				{
					who.add(names.getOrDefault(id, "someone"));
				}
			}
			boolean lifted = who.isEmpty();
			String detail = lifted ? "Reached, so anyone can take these" : "Locked for " + String.join(", ", who);
			int quantity = data.getQuantity() > 0 ? data.getQuantity() : Math.max(1, inStorage.getOrDefault(data.getItemId(), 1));
			Map<String, ItemBlocks.Block> blocks = lifted ? free : locked;
			blocks.computeIfAbsent(goal.getId() + "|" + detail,
				key -> ItemBlocks.of("Until " + GoalService.title(goal.getData()), detail, lifted ? FREE : LOCKED, new ArrayList<>()))
				.getItems().add(new Item(data.getItemId(), quantity));
		}
		List<ItemBlocks.Block> out = new ArrayList<>(locked.values());
		out.addAll(free.values());
		return out;
	}

	/**
	 * @return what's waiting in storage for each member's requests
	 */
	private List<ItemBlocks.Block> heldBlocks(List<Item> slots)
	{
		Set<Integer> items = new LinkedHashSet<>();
		for (Item item : slots)
		{
			if (item != null)
			{
				items.add(item.getId());
			}
		}
		Map<String, ItemBlocks.Block> out = new LinkedHashMap<>();
		for (int item : items)
		{
			for (RequestService.Reservation held : requests.reservationsFor(item))
			{
				String who = held.getRequestedByName() == null ? "someone" : held.getRequestedByName();
				out.computeIfAbsent(who, key -> ItemBlocks.of("For " + key, null, null, new ArrayList<>()))
					.getItems().add(new Item(item, Math.max(1, held.getQuantity())));
			}
		}
		return new ArrayList<>(out.values());
	}

	private static Map<Integer, Integer> storedCounts(List<Item> slots)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (Item item : slots)
		{
			if (item != null)
			{
				out.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return out;
	}
}
