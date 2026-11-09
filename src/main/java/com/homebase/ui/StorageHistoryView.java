package com.homebase.ui;

import com.homebase.data.Clean;
import com.homebase.storage.StorageHistory;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import lombok.Value;
import net.runelite.api.Item;
import net.runelite.client.ui.ColorScheme;

/**
 * The lists on the storage tab: what's owed back, and who put in or took out what, in blocks of item
 * pictures like the bounties.
 */
final class StorageHistoryView
{
	static final String UNMARK = "Doesn't need returning";

	/**
	 * Visits to storage shown in the history, newest first. Anything older is counted underneath.
	 */
	private static final int MAX_VISITS_SHOWN = 10;

	/**
	 * Saves by one member the same way this close together count as one visit, as when putting things
	 * in a few at a time.
	 */
	static final long SAME_VISIT_MS = 10 * 60_000;

	/**
	 * Narrower than other wrapped text, since these rows sit inside the storage section and a list.
	 */
	static final int TEXT_WIDTH = Ui.TEXT_WIDTH - 9;

	/**
	 * Longest player names shown. They come from other members' data.
	 */
	private static final int MAX_NAME = 20;

	private static final String OWED_ID = "storage:owed";
	private static final String HISTORY_ID = "storage:history";

	/**
	 * Green for what went into storage and red for what came out, the same as the bounties' padlocks.
	 */
	private static final Color PUT_IN = new Color(0x66BB6A);
	private static final Color TOOK_OUT = new Color(0xEF5350);

	private StorageHistoryView()
	{
	}

	/**
	 * Add what's owed back, if anything is.
	 *
	 * @param unmark called when someone says an item doesn't need returning after all, or null if they
	 * can't for now
	 * @param onNamesFound run on the Swing thread once item names we didn't have yet have been looked up
	 */
	static void addOwed(JPanel parent, List<StorageHistory.Owed> owed, IntConsumer unmark, MemberCard.Context context,
		Runnable onNamesFound)
	{
		if (owed.isEmpty() || !MemberCard.addFoldRow(parent, "Owed back", String.valueOf(owed.size()), OWED_ID, context))
		{
			return;
		}
		MemberCard.addContent(parent, ItemBlocks.list(owedBlocks(owed, unmark), context.itemSprites));
		Set<Integer> items = new HashSet<>();
		owed.forEach(entry -> items.add(entry.getItem()));
		lookUpMissingNames(items, context, onNamesFound);
	}

	/**
	 * @param unmark called when an item here is right-clicked and said not to need returning, or null if
	 * it can't be. All of an item can be out, so it may not be in the storage grid to right-click there.
	 * @return a block for each member with what they owe back
	 */
	static List<ItemBlocks.Block> owedBlocks(List<StorageHistory.Owed> owed, IntConsumer unmark)
	{
		Map<String, List<StorageHistory.Owed>> byMember = new LinkedHashMap<>();
		for (StorageHistory.Owed entry : owed)
		{
			byMember.computeIfAbsent(name(entry.getWho()), who -> new ArrayList<>()).add(entry);
		}
		List<ItemBlocks.Block> out = new ArrayList<>();
		byMember.forEach((who, entries) ->
		{
			List<Item> items = new ArrayList<>();
			Set<Integer> ids = new HashSet<>();
			long latest = 0;
			for (StorageHistory.Owed entry : entries)
			{
				items.add(new Item(entry.getItem(), (int) Math.min(Integer.MAX_VALUE, entry.getAmount())));
				ids.add(entry.getItem());
				latest = Math.max(latest, entry.getAt());
			}
			// they're all marked, so a right-click can only say one doesn't need returning
			ItemGrid.Marks marks = unmark == null ? ItemGrid.NO_MARKS : new ItemGrid.Marks(ids, item ->
			{
			}, unmark);
			out.add(new ItemBlocks.Block(who, "Owes these back · " + Ago.format(latest), Ui.WARNING, items, marks));
		});
		return out;
	}

	/**
	 * Add who put in or took out what, if anyone has changed anything.
	 *
	 * @param onNamesFound run on the Swing thread once item names we didn't have yet have been looked up
	 */
	static void addHistory(JPanel parent, List<StorageHistory.MemberLog> logs, MemberCard.Context context, Runnable onNamesFound)
	{
		List<StorageHistory.Line> lines = StorageHistory.lines(logs);
		if (lines.isEmpty() || !MemberCard.addFoldRow(parent, "History", String.valueOf(lines.size()), HISTORY_ID, context))
		{
			return;
		}
		Shown shown = historyBlocks(lines, MAX_VISITS_SHOWN);
		List<ItemBlocks.Block> blocks = shown.getBlocks();
		JPanel list = ItemBlocks.list(blocks, context.itemSprites);
		if (shown.getOlder() > 0)
		{
			list.add(Box.createVerticalStrut(6));
			list.add(Ui.muted("and " + shown.getOlder() + " older"));
			Ui.alignChildrenLeft(list);
		}
		MemberCard.addContent(parent, list);
		Set<Integer> items = new HashSet<>();
		blocks.forEach(block -> block.getItems().forEach(item -> items.add(item.getId())));
		lookUpMissingNames(items, context, onNamesFound);
	}

	/**
	 * What the history shows: a block for each visit to storage, and how many changes are too old to fit.
	 */
	@Value
	static class Shown
	{
		List<ItemBlocks.Block> blocks;
		int older;
	}

	/**
	 * @param lines the history, newest first
	 * @return a block for each of the newest visits, newest first. A visit is a member putting in, or
	 * taking out, over saves no more than {@link #SAME_VISIT_MS} apart.
	 */
	static Shown historyBlocks(List<StorageHistory.Line> lines, int maxVisits)
	{
		List<Visit> visits = new ArrayList<>();
		int older = 0;
		for (StorageHistory.Line line : lines)
		{
			boolean putIn = line.getAmount() > 0;
			Visit visit = null;
			for (Visit candidate : visits)
			{
				if (Objects.equals(candidate.who, line.getWho()) && candidate.putIn == putIn && candidate.oldest - line.getAt() <= SAME_VISIT_MS)
				{
					visit = candidate;
					break;
				}
			}
			if (visit == null)
			{
				if (visits.size() >= maxVisits)
				{
					older++;
					continue;
				}
				visit = new Visit(line.getWho(), putIn, line.getAt());
				visits.add(visit);
			}
			visit.oldest = Math.min(visit.oldest, line.getAt());
			visit.items.merge(line.getItem(), Math.abs((long) line.getAmount()), Long::sum);
		}
		List<ItemBlocks.Block> blocks = new ArrayList<>();
		for (Visit visit : visits)
		{
			List<Item> items = new ArrayList<>();
			visit.items.forEach((item, amount) -> items.add(new Item(item, (int) Math.min(Integer.MAX_VALUE, amount))));
			blocks.add(ItemBlocks.of(name(visit.who), (visit.putIn ? "Put in" : "Took out") + " · " + Ago.format(visit.newest),
				visit.putIn ? PUT_IN : TOOK_OUT, items));
		}
		return new Shown(blocks, older);
	}

	private static final class Visit
	{
		final String who;
		final boolean putIn;
		final long newest;
		long oldest;
		final Map<Integer, Long> items = new LinkedHashMap<>();

		Visit(String who, boolean putIn, long at)
		{
			this.who = who;
			this.putIn = putIn;
			newest = at;
			oldest = at;
		}
	}

	private static String name(String who)
	{
		String clean = Clean.text(who, MAX_NAME);
		return clean == null ? "Someone" : clean;
	}

	private static void lookUpMissingNames(Set<Integer> items, MemberCard.Context context, Runnable onNamesFound)
	{
		// asking only when a name is missing, since the panel is drawn again once they're found
		if (items.stream().anyMatch(item -> ItemNames.known(item) == null))
		{
			context.itemSprites.lookUpNames(items, onNamesFound);
		}
	}

	static void addRows(JPanel parent, List<String> rows)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		for (String row : rows)
		{
			JLabel label = Ui.wrapped(row, ColorScheme.LIGHT_GRAY_COLOR, TEXT_WIDTH);
			label.setBorder(new EmptyBorder(1, 0, 1, 0));
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(label);
		}
		MemberCard.addContent(parent, list);
	}
}
