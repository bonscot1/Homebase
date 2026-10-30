package com.homebase.ui;

import com.google.common.base.Strings;
import com.homebase.data.RequestData;
import com.homebase.requests.RequestService;
import com.homebase.requests.RequestService.Request;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Ask the group for items, and take on what others have asked for.
 */
class RequestsView extends JPanel
{
	private static final Color UNCLAIMED = new Color(0xff9800);

	private final RequestService service;
	private final JTextField itemField = new JTextField();
	private final JTextField quantityField = new JTextField("1");
	private final JTextField noteField = new JTextField();
	private final JButton requestButton = new JButton("Request");
	private final JLabel formHint = new JLabel();
	private final JPanel list = new JPanel();

	RequestsView(RequestService service)
	{
		this.service = service;
		setLayout(new BorderLayout());
		setOpaque(false);

		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		form.setBorder(new EmptyBorder(6, 6, 6, 6));

		JPanel itemRow = new JPanel(new BorderLayout(4, 0));
		itemRow.setOpaque(false);
		itemRow.add(field("Item", itemField), BorderLayout.CENTER);
		JPanel quantity = field("Qty", quantityField);
		quantityField.setColumns(4);
		itemRow.add(quantity, BorderLayout.EAST);

		form.add(itemRow);
		form.add(Box.createVerticalStrut(4));
		form.add(field("Note (optional)", noteField));
		form.add(Box.createVerticalStrut(6));
		requestButton.setFocusPainted(false);
		requestButton.addActionListener(e -> submit());
		itemField.addActionListener(e -> submit());
		form.add(requestButton);
		formHint.setFont(FontManager.getRunescapeSmallFont());
		formHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		form.add(formHint);
		for (Component c : form.getComponents())
		{
			((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		list.setBorder(new EmptyBorder(8, 0, 0, 0));

		add(form, BorderLayout.NORTH);
		add(list, BorderLayout.CENTER);
	}

	/**
	 * Redraw the list. The form is left alone so typing isn't interrupted.
	 */
	void refresh()
	{
		boolean canEdit = service.canEdit();
		requestButton.setEnabled(canEdit);
		formHint.setText(canEdit ? "" : "Log in to make or take on requests.");
		formHint.setVisible(!canEdit);

		list.removeAll();
		List<Request> requests = service.list();
		if (requests.isEmpty())
		{
			list.add(MemberCard.heading("No requests yet."));
		}
		for (Request request : requests)
		{
			JPanel card = card(request, canEdit);
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(card);
			list.add(Box.createVerticalStrut(6));
		}
		list.revalidate();
		list.repaint();
	}

	int openCount()
	{
		int open = 0;
		for (Request request : service.list())
		{
			if (!request.getData().isDone())
			{
				open++;
			}
		}
		return open;
	}

	private void submit()
	{
		String item = itemField.getText().trim();
		if (item.isEmpty() || !service.canEdit())
		{
			return;
		}
		int quantity;
		try
		{
			quantity = Math.min(1_000_000, Math.max(1, Integer.parseInt(quantityField.getText().trim())));
		}
		catch (NumberFormatException e)
		{
			quantity = 1;
		}
		service.create(item, quantity, noteField.getText());
		itemField.setText("");
		quantityField.setText("1");
		noteField.setText("");
	}

	private JPanel card(Request request, boolean canEdit)
	{
		RequestData data = request.getData();
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 6, 6, 6));

		JLabel title = new JLabel(data.getQuantity() > 1 ? data.getItem() + " x " + data.getQuantity() : data.getItem());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(data.isDone() ? ColorScheme.MEDIUM_GRAY_COLOR : Color.WHITE);
		card.add(title);

		card.add(small("For " + name(data.getRequestedByName()) + "  ·  " + Ago.format(data.getCreatedAt()),
			ColorScheme.LIGHT_GRAY_COLOR));
		if (!Strings.isNullOrEmpty(data.getNote()))
		{
			card.add(small("<html><body style='width:170px'>“" + escape(data.getNote()) + "”</body></html>",
				ColorScheme.LIGHT_GRAY_COLOR));
		}

		if (data.isDone())
		{
			card.add(small("Done by " + name(data.getAssignedToName()), ColorScheme.PROGRESS_COMPLETE_COLOR));
		}
		else if (data.getAssignedTo() != null)
		{
			card.add(small(name(data.getAssignedToName()) + " is on it", ColorScheme.PROGRESS_INPROGRESS_COLOR));
		}
		else
		{
			card.add(small("Nobody has taken this yet", UNCLAIMED));
		}

		if (canEdit)
		{
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(4, 0, 0, 0));
			JPanel grid = new JPanel(new GridLayout(1, 0, 4, 0));
			grid.setOpaque(false);
			String id = request.getId();
			boolean mine = service.isMe(data.getRequestedBy());
			boolean assignedToMe = service.isMe(data.getAssignedTo());
			if (!data.isDone() && data.getAssignedTo() == null)
			{
				grid.add(button("I'll get it", () -> service.claim(id)));
			}
			if (!data.isDone() && assignedToMe)
			{
				grid.add(button("Done", () -> service.complete(id)));
				grid.add(button("Give back", () -> service.unclaim(id)));
			}
			if (mine || data.isDone())
			{
				grid.add(button(data.isDone() ? "Clear" : "Cancel", () -> service.delete(id)));
			}
			if (grid.getComponentCount() > 0)
			{
				buttons.add(grid);
				card.add(buttons);
			}
		}

		for (Component c : card.getComponents())
		{
			((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		return card;
	}

	private static JPanel field(String label, JTextField field)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setOpaque(false);
		panel.add(small(label, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.NORTH);
		panel.add(field, BorderLayout.CENTER);
		return panel;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setMargin(new Insets(1, 6, 1, 6));
		button.setFocusPainted(false);
		button.addActionListener(e -> action.run());
		return button;
	}

	private static JLabel small(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	private static String name(String name)
	{
		return Strings.isNullOrEmpty(name) ? "someone" : name;
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
