package com.homebase.ui;

import com.homebase.data.UpdateEntry;
import com.homebase.updates.UpdateTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * What each member has done since your last update: levels gained, quests finished, diaries completed.
 */
class UpdatesView extends JPanel
{
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
			JLabel hint = new JLabel("<html><body style='width:180px'>Nothing new yet. When someone in your group levels up, "
				+ "finishes a quest or completes a diary, a summary appears here.</body></html>");
			hint.setFont(FontManager.getRunescapeSmallFont());
			hint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			hint.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(hint);
		}
		for (UpdateEntry entry : entries)
		{
			JPanel card = card(entry);
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(card);
			add(Box.createVerticalStrut(6));
		}
		revalidate();
		repaint();
	}

	private static JPanel card(UpdateEntry entry)
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 6, 6, 6));

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel name = new JLabel(entry.getName());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		header.add(name, BorderLayout.WEST);
		JLabel when = new JLabel(Ago.format(entry.getTime()));
		when.setFont(FontManager.getRunescapeSmallFont());
		when.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		header.add(when, BorderLayout.EAST);
		card.add(header, BorderLayout.NORTH);

		JPanel lines = new JPanel();
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setOpaque(false);
		for (String change : entry.getChanges())
		{
			JLabel line = new JLabel("• " + change);
			line.setFont(FontManager.getRunescapeSmallFont());
			line.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			lines.add(line);
		}
		card.add(lines, BorderLayout.CENTER);
		return card;
	}
}
