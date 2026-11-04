package com.homebase.ui;

import java.awt.Color;
import javax.swing.JLabel;
import net.runelite.api.Constants;
import net.runelite.client.ui.PluginPanel;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class UiTest
{
	private static final String LONG_TEXT = "got Dragon warhammer from Lizardman shaman (41.2M), and more than that so it has to wrap onto another line";

	/**
	 * The room for text in a card: the sidebar, less the panel's border and the card's.
	 */
	private static final int CARD_TEXT_ROOM = PluginPanel.PANEL_WIDTH - 2 * 10 - 2 * 8;

	private static int drawnWidth(JLabel label)
	{
		return label.getPreferredSize().width;
	}

	@Test
	public void wrappedTextFitsInACard()
	{
		assertFits(drawnWidth(Ui.wrapped(LONG_TEXT, Color.WHITE)), CARD_TEXT_ROOM);
	}

	@Test
	public void searchResultsFitBesideTheirPicture()
	{
		int room = CARD_TEXT_ROOM - Constants.ITEM_SPRITE_WIDTH - 6;
		assertFits(drawnWidth(Ui.wrapped(LONG_TEXT, Color.WHITE, ItemSearchView.TEXT_WIDTH)), room);
	}

	private static void assertFits(int width, int room)
	{
		assertTrue(width + " pixels of text in " + room, width <= room);
	}
}
