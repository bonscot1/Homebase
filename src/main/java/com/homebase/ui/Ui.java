package com.homebase.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.PluginErrorPanel;

/**
 * The panel's shared look, so every tab is built from the same few pieces: cards, collapsible
 * section rows, bars, small labels and flat buttons. Colours and spacing follow RuneLite's own panels.
 */
final class Ui
{
	static final Color ONLINE = new Color(0x4CAF50);
	static final Color AWAY = new Color(0x9E9E9E);
	static final Color WARNING = new Color(0xFFA726);

	/**
	 * Bar colours used by RuneLite's Party panel, so members look the same in both.
	 */
	static final Color HP_FG = new Color(0, 146, 54, 230);
	static final Color HP_BG = new Color(102, 15, 16, 230);
	static final Color PRAY_FG = new Color(0, 149, 151);
	static final Color PRAY_BG = Color.BLACK;
	static final Color RUN_FG = new Color(0xC8A01E);
	static final Color RUN_BG = new Color(0x3A3220);

	/**
	 * Gap between cards.
	 */
	static final int GAP = 6;

	/**
	 * Width HTML labels wrap at. Wider than this clips inside the sidebar.
	 */
	static final int TEXT_WIDTH = 160;

	private Ui()
	{
	}

	/**
	 * @return an empty card: a darker grey box for one entry in a list
	 */
	static JPanel card()
	{
		JPanel card = new JPanel(new BorderLayout());
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(7, 8, 7, 8));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		return label;
	}

	static JLabel small(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	static JLabel muted(String text)
	{
		return small(text, ColorScheme.MEDIUM_GRAY_COLOR);
	}

	/**
	 * @return a small label that wraps inside the sidebar. The text is HTML, so anything other players
	 * typed must go through {@link #escapeHtml} first.
	 */
	static JLabel wrapped(String text, Color color)
	{
		return wrapped(text, color, TEXT_WIDTH);
	}

	/**
	 * @return the same, wrapping at a narrower width for text that's indented further than usual
	 */
	static JLabel wrapped(String text, Color color, int width)
	{
		return small("<html><body style='width:" + width + "px'>" + text + "</body></html>", color);
	}

	static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * @return a form input with its small grey label above it
	 */
	static JPanel field(String label, JComponent input)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setOpaque(false);
		panel.add(small(label, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.NORTH);
		panel.add(input, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Line up everything in a vertical box on the left. BoxLayout lines children up by their
	 * alignment, and panels and labels don't agree on it by default.
	 */
	static void alignChildrenLeft(Container parent)
	{
		for (Component child : parent.getComponents())
		{
			((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
	}

	/**
	 * @return RuneLite's own empty-state message, a title and a line under it
	 */
	static JComponent empty(String title, String description)
	{
		PluginErrorPanel panel = new PluginErrorPanel();
		panel.setContent(title, description);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	/**
	 * A row that opens and closes a section: an arrow, a title, and a short summary on the right.
	 * Lights up under the mouse like RuneLite's own list rows.
	 */
	static JPanel sectionRow(String title, String summary, boolean open, Runnable onToggle)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel left = small(title, Color.WHITE);
		left.setIcon(new Chevron(open));
		left.setIconTextGap(6);
		row.add(left, BorderLayout.WEST);
		if (summary != null)
		{
			row.add(small(summary, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		}
		hover(row, ColorScheme.DARK_GRAY_COLOR, ColorScheme.DARK_GRAY_HOVER_COLOR);
		clickable(row, onToggle);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * A plain row with a title on the left and a value on the right, for facts that don't open.
	 */
	static JPanel infoRow(String title, String value)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(small(title, Color.WHITE), BorderLayout.WEST);
		row.add(small(value, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * @return a bar like the Party panel's hitpoints and prayer bars, with the numbers on it
	 */
	static Meter bar(Color fg, Color bg)
	{
		return new Meter(fg, bg);
	}

	/**
	 * A filled bar with its numbers centred across the whole bar. RuneLite's own bar gives its middle
	 * label only a third of the width, which cuts "46/50" short in narrow bars.
	 */
	static final class Meter extends JComponent
	{
		private static final int HEIGHT = 16;

		private final Color fg;
		private final Color bg;
		private int value;
		private int max = 1;
		private String label = "";

		Meter(Color fg, Color bg)
		{
			this.fg = fg;
			this.bg = bg;
			setFont(FontManager.getRunescapeSmallFont());
			setPreferredSize(new Dimension(0, HEIGHT));
			setMinimumSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		}

		void set(int value, int max, String label)
		{
			this.max = Math.max(1, max);
			this.value = Math.max(0, Math.min(value, this.max));
			this.label = label == null ? "" : label;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			int w = getWidth();
			int h = getHeight();
			g.setColor(bg);
			g.fillRect(0, 0, w, h);
			g.setColor(fg);
			g.fillRect(0, 0, (int) ((long) w * value / max), h);
			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			int x = (w - fm.stringWidth(label)) / 2;
			int y = (h - fm.getHeight()) / 2 + fm.getAscent();
			// a one pixel shadow, like the game's own text
			g.setColor(Color.BLACK);
			g.drawString(label, x + 1, y + 1);
			g.setColor(Color.WHITE);
			g.drawString(label, x, y);
		}
	}

	/**
	 * @return a flat button in the style of the sidebar, smaller than Swing's default
	 */
	static JButton button(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(Color.WHITE);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setBorder(new EmptyBorder(4, 8, 4, 8));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hover(button, ColorScheme.DARK_GRAY_COLOR, ColorScheme.DARK_GRAY_HOVER_COLOR);
		button.addActionListener(e -> action.run());
		return button;
	}

	/**
	 * @return a form's main button, in RuneLite's orange
	 */
	static JButton primaryButton(String text, Runnable action)
	{
		JButton button = new JButton(text);
		primary(button);
		button.addActionListener(e -> action.run());
		return button;
	}

	/**
	 * Style a form's main button: the same flat look in RuneLite's orange.
	 */
	static void primary(JButton button)
	{
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(Color.WHITE);
		button.setBackground(ColorScheme.BRAND_ORANGE.darker());
		button.setBorder(new EmptyBorder(6, 8, 6, 8));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hover(button, ColorScheme.BRAND_ORANGE.darker(), ColorScheme.BRAND_ORANGE);
	}

	static void clickable(JComponent component, Runnable onClick)
	{
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
	}

	static void hover(JComponent component, Color normal, Color hovered)
	{
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				component.setBackground(hovered);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				component.setBackground(normal);
			}
		});
	}

	/**
	 * A small filled circle, for online and connection status.
	 */
	static final class Dot implements Icon
	{
		private static final int SIZE = 7;

		private final Color color;

		Dot(Color color)
		{
			this.color = color;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fillOval(x, y + 1, SIZE, SIZE);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return 9;
		}
	}

	/**
	 * The arrow on section rows, pointing right when closed and down when open.
	 */
	static final class Chevron implements Icon
	{
		private final boolean open;

		Chevron(boolean open)
		{
			this.open = open;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			int cy = y + 4;
			if (open)
			{
				g2.fillPolygon(new int[]{x, x + 8, x + 4}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
			}
			else
			{
				g2.fillPolygon(new int[]{x + 2, x + 7, x + 2}, new int[]{cy - 4, cy, cy + 4}, 3);
			}
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 8;
		}

		@Override
		public int getIconHeight()
		{
			return 9;
		}
	}
}
