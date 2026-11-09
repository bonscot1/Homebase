package com.homebase.ui;

import com.homebase.data.UpdateEntry;
import com.homebase.data.UpdateLine;
import com.homebase.updates.UpdateDiff;
import com.homebase.updates.UpdateTracker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * What members have done lately, such as levels gained, quests finished and drops. Each line has a
 * picture of the skill, boss or item it's about.
 */
class UpdatesView extends JPanel
{
	/**
	 * Room left for a line's words beside its picture.
	 */
	private static final int LINE_TEXT_WIDTH = Ui.TEXT_WIDTH - 22;

	private final UpdateTracker tracker;
	private final UpdatePictures pictures;

	UpdatesView(UpdateTracker tracker, UpdatePictures pictures)
	{
		this.tracker = tracker;
		this.pictures = pictures;
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

	private JPanel card(UpdateEntry entry)
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
		if (entry.getLines() != null && !entry.getLines().isEmpty())
		{
			for (UpdateLine line : entry.getLines())
			{
				lines.add(line(pictures.of(line), line.getText()));
			}
		}
		else
		{
			for (String change : entry.getChanges())
			{
				lines.add(line(pictures.of(UpdateDiff.fromText(change)), change));
			}
		}
		card.add(lines, BorderLayout.CENTER);
		return card;
	}

	private static JPanel line(JComponent picture, String text)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(picture, BorderLayout.WEST);
		// long lines, such as a new collection log item, wrap rather than run off the edge
		row.add(Ui.wrapped(Ui.escapeHtml(text == null ? "" : text), ColorScheme.LIGHT_GRAY_COLOR, LINE_TEXT_WIDTH), BorderLayout.CENTER);
		return row;
	}
}
