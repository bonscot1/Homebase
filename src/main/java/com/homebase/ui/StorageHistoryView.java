package com.homebase.ui;

import com.homebase.data.Clean;
import com.homebase.storage.StorageHistory;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * Under the group storage items: what's still out, and who put in or took out what.
 */
final class StorageHistoryView
{
	private static final int MAX_BORROWED_SHOWN = 8;
	private static final int MAX_LINES_SHOWN = 30;

	/**
	 * Narrower than other wrapped text, since these rows sit inside the storage section and a list.
	 */
	private static final int TEXT_WIDTH = Ui.TEXT_WIDTH - 24;

	/**
	 * Longest player and item names shown. Player names come from other members' data.
	 */
	private static final int MAX_NAME = 20;
	private static final int MAX_ITEM_NAME = 60;

	private static final String BORROWED_ID = "storage:borrowed";
	private static final String HISTORY_ID = "storage:history";

	private static final String WHO_COLOR = ColorUtil.toHexColor(java.awt.Color.WHITE);
	private static final String TIME_COLOR = ColorUtil.toHexColor(ColorScheme.MEDIUM_GRAY_COLOR);

	private StorageHistoryView()
	{
	}

	/**
	 * Add the lists to the group storage section, if anyone has changed anything.
	 *
	 * @param onNamesFound run on the Swing thread once item names we didn't have yet have been looked up
	 */
	static void add(JPanel parent, List<StorageHistory.MemberLog> logs, MemberCard.Context context, Runnable onNamesFound)
	{
		List<StorageHistory.Line> lines = StorageHistory.lines(logs);
		if (lines.isEmpty())
		{
			return;
		}
		List<StorageHistory.Borrowed> borrowed = StorageHistory.borrowed(logs);
		Set<Integer> shownItems = new HashSet<>();

		parent.add(Box.createVerticalStrut(4));
		if (!borrowed.isEmpty() && MemberCard.addFoldRow(parent, "Still borrowed", String.valueOf(borrowed.size()), BORROWED_ID, context))
		{
			List<String> rows = new ArrayList<>();
			for (StorageHistory.Borrowed out : borrowed.subList(0, Math.min(MAX_BORROWED_SHOWN, borrowed.size())))
			{
				shownItems.add(out.getItem());
				rows.add(text(out));
			}
			addRows(parent, rows, borrowed.size() - rows.size(), " more");
		}

		MemberCard.addSectionRow(parent, "History", String.valueOf(lines.size()), HISTORY_ID, context);
		if (context.openSections.contains(HISTORY_ID))
		{
			List<String> rows = new ArrayList<>();
			for (StorageHistory.Line line : lines.subList(0, Math.min(MAX_LINES_SHOWN, lines.size())))
			{
				shownItems.add(line.getItem());
				rows.add(text(line));
			}
			addRows(parent, rows, lines.size() - rows.size(), " older");
		}

		// asking only when a name is missing, since the panel is drawn again once they're found
		if (!shownItems.stream().allMatch(ItemNames::known))
		{
			context.itemSprites.lookUpNames(shownItems, onNamesFound);
		}
	}

	private static void addRows(JPanel parent, List<String> rows, int hidden, String hiddenNoun)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		for (String row : rows)
		{
			addRow(list, Ui.wrapped(row, ColorScheme.LIGHT_GRAY_COLOR, TEXT_WIDTH));
		}
		if (hidden > 0)
		{
			addRow(list, Ui.muted("and " + hidden + hiddenNoun));
		}
		MemberCard.addContent(parent, list);
	}

	private static void addRow(JPanel list, JLabel row)
	{
		row.setBorder(new EmptyBorder(1, 0, 1, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		list.add(row);
	}

	/**
	 * @return the history line as HTML, such as "Mundell took out Shark ×5 · 2h ago"
	 */
	static String text(StorageHistory.Line line)
	{
		return who(line.getWho()) + (line.getAmount() > 0 ? " put in " : " took out ")
			+ thing(line.getItem(), Math.abs((long) line.getAmount())) + when(line.getAt());
	}

	/**
	 * @return the borrowed line as HTML, such as "Mundell: Shark ×5 · 2h ago"
	 */
	static String text(StorageHistory.Borrowed borrowed)
	{
		return who(borrowed.getWho()) + ": " + thing(borrowed.getItem(), borrowed.getAmount()) + when(borrowed.getAt());
	}

	private static String who(String name)
	{
		String clean = Clean.text(name, MAX_NAME);
		return "<span style='color:" + WHO_COLOR + "'>" + (clean == null ? "Someone" : Ui.escapeHtml(clean)) + "</span>";
	}

	private static String thing(int item, long amount)
	{
		String name = Clean.text(ItemNames.get(item), MAX_ITEM_NAME);
		return (name == null ? "item " + item : Ui.escapeHtml(name))
			+ (amount > 1 ? " ×" + QuantityFormatter.formatNumber(amount) : "");
	}

	private static String when(long at)
	{
		return " <span style='color:" + TIME_COLOR + "'>· " + Ago.format(at) + "</span>";
	}
}
