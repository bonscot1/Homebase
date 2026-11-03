package com.homebase.ui;

import com.homebase.data.UpdateEntry;
import com.homebase.updates.UpdateTracker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * What each member has done since your last update: levels gained, quests finished, diaries completed.
 */
class UpdatesView extends JPanel
{
	private static final String BULLET = "<span style='color:#FF9800'>•</span> ";

	private final UpdateTracker tracker;

	UpdatesView(UpdateTracker tracker)
	{
		this.tracker = tracker;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
	}

	void refresh()
	{
		removeAll();
		List<UpdateEntry> entries = tracker.getEntries();
		if (entries.isEmpty())
		{
			add(Ui.empty("Nothing new yet", "When someone in your group levels up, finishes a quest or completes a diary, "
				+ "a summary appears here."));
		}
		for (UpdateEntry entry : entries)
		{
			JPanel card = card(entry);
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(card);
			add(Box.createVerticalStrut(Ui.GAP));
		}
		revalidate();
		repaint();
	}

	private static JPanel card(UpdateEntry entry)
	{
		JPanel card = Ui.card();

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, 4, 0));
		header.add(Ui.title(entry.getName()), BorderLayout.CENTER);
		header.add(Ui.muted(Ago.format(entry.getTime())), BorderLayout.EAST);
		card.add(header, BorderLayout.NORTH);

		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setOpaque(false);
		for (String change : entry.getChanges())
		{
			// long lines, such as a new collection log item, wrap rather than run off the edge
			JLabel line = Ui.wrapped(BULLET + Ui.escapeHtml(change), ColorScheme.LIGHT_GRAY_COLOR);
			line.setBorder(new EmptyBorder(1, 0, 1, 0));
			line.setAlignmentX(Component.LEFT_ALIGNMENT);
			lines.add(line);
		}
		card.add(lines, BorderLayout.CENTER);
		return card;
	}
}
