package com.homebase.ui;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Constants;
import net.runelite.client.ui.PluginPanel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

	/**
	 * A dropdown whose list can be said to be open, since there's no screen to open it on in a test.
	 */
	private static final class Dropdown extends JComboBox<String>
	{
		boolean open;

		@Override
		public boolean isPopupVisible()
		{
			return open;
		}
	}

	@Test
	public void anOpenDropdownIsFoundInsideThePanel()
	{
		Dropdown dropdown = new Dropdown();
		JPanel inner = new JPanel();
		inner.add(dropdown);
		JPanel panel = new JPanel();
		panel.add(new JComboBox<String>());
		panel.add(inner);

		assertFalse(Ui.hasOpenDropdown(panel));
		dropdown.open = true;
		assertTrue(Ui.hasOpenDropdown(panel));
	}

	@Test
	public void aChangeToAnOpenListWaitsUntilItCloses() throws Exception
	{
		Dropdown dropdown = new Dropdown();
		AtomicInteger changes = new AtomicInteger();

		Ui.whenClosed(dropdown, changes::incrementAndGet);
		assertEquals(1, changes.get());

		dropdown.open = true;
		Ui.whenClosed(dropdown, changes::incrementAndGet);
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(1, changes.get());

		dropdown.open = false;
		dropdown.firePopupMenuWillBecomeInvisible();
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(2, changes.get());

		// it only waits for the one close
		dropdown.firePopupMenuWillBecomeInvisible();
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(2, changes.get());
	}
}
