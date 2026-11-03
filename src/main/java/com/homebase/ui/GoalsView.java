package com.homebase.ui;

import com.homebase.bounties.BountyService;
import com.homebase.data.BountyData;
import com.homebase.data.DiariesData;
import com.homebase.data.GoalData;
import com.homebase.data.ItemsData;
import com.homebase.goals.GoalService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Item;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Set goals for the group and watch them tick off, and put bounties on group storage items.
 */
class GoalsView extends JPanel
{
	private static final Color DONE = new Color(0x4caf50);
	private static final Color LOCKED = new Color(0xEF5350);

	private static final Map<GoalData.Type, String> TYPE_LABELS = new LinkedHashMap<>();

	static
	{
		TYPE_LABELS.put(GoalData.Type.SKILL, "Skill level");
		TYPE_LABELS.put(GoalData.Type.TOTAL_LEVEL, "Total level");
		TYPE_LABELS.put(GoalData.Type.QUEST, "Quest");
		TYPE_LABELS.put(GoalData.Type.DIARY, "Achievement diary");
		TYPE_LABELS.put(GoalData.Type.KILL_COUNT, "Kill count");
		TYPE_LABELS.put(GoalData.Type.COMBAT_POINTS, "Combat achievement points");
		TYPE_LABELS.put(GoalData.Type.COLLECTION_LOG, "Collection log slots");
	}

	private static final Map<Integer, String> ITEM_NAMES = Collections.synchronizedMap(new HashMap<>());

	private final GoalService goals;
	private final BountyService bounties;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Supplier<ItemsData> storage;

	private final JComboBox<String> typeBox = new JComboBox<>(TYPE_LABELS.values().toArray(new String[0]));
	private final JComboBox<String> targetBox = new JComboBox<>();
	private final JTextField amountField = new JTextField("50");
	private final JComboBox<String> forBox = new JComboBox<>();
	private final JButton createButton = new JButton("Set goal");
	private final JLabel formHint = new JLabel();
	private final JPanel list = new JPanel();

	/**
	 * The goal whose bounty form is open, and the form's state, kept across rebuilds.
	 */
	private String bountyGoal;
	private JComboBox<String> bountyItem;
	private List<Integer> bountyItemIds = new ArrayList<>();
	private final Map<String, JCheckBox> bountyMembers = new LinkedHashMap<>();

	private List<GoalService.Member> members = new ArrayList<>();

	GoalsView(GoalService goals, BountyService bounties, ItemManager itemManager, ClientThread clientThread, Supplier<ItemsData> storage)
	{
		this.goals = goals;
		this.bounties = bounties;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.storage = storage;
		setLayout(new BorderLayout());
		setOpaque(false);

		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		form.setBorder(new EmptyBorder(6, 6, 6, 6));

		typeBox.addActionListener(e -> typeChanged());
		targetBox.setEditable(true);
		amountField.setColumns(4);

		form.add(field("Goal", typeBox));
		form.add(Box.createVerticalStrut(4));
		JPanel targetRow = new JPanel(new BorderLayout(4, 0));
		targetRow.setOpaque(false);
		targetRow.add(field("What", targetBox), BorderLayout.CENTER);
		targetRow.add(field("Amount", amountField), BorderLayout.EAST);
		form.add(targetRow);
		form.add(Box.createVerticalStrut(4));
		form.add(field("For", forBox));
		form.add(Box.createVerticalStrut(6));
		createButton.setFocusPainted(false);
		createButton.addActionListener(e -> submit());
		form.add(createButton);
		formHint.setFont(FontManager.getRunescapeSmallFont());
		formHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		form.add(formHint);
		for (Component c : form.getComponents())
		{
			((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);
		list.setBorder(new EmptyBorder(8, 0, 0, 0));

		add(form, BorderLayout.NORTH);
		add(list, BorderLayout.CENTER);
		typeChanged();
	}

	void refresh()
	{
		members = goals.members();
		String chosen = (String) forBox.getSelectedItem();
		DefaultComboBoxModel<String> forModel = new DefaultComboBoxModel<>();
		forModel.addElement("Everyone");
		for (GoalService.Member member : members)
		{
			forModel.addElement(member.getName());
		}
		forBox.setModel(forModel);
		if (chosen != null)
		{
			forBox.setSelectedItem(chosen);
		}

		boolean canEdit = goals.canEdit();
		createButton.setEnabled(canEdit);
		formHint.setText(canEdit ? "" : "Log in to set goals or bounties.");

		list.removeAll();
		List<GoalService.Goal> all = goals.list();
		if (all.isEmpty())
		{
			list.add(hint("No goals yet. Set one above, for example 50 Firemaking for everyone before Wintertodt."));
		}
		List<BountyService.Bounty> allBounties = bounties.list();
		for (GoalService.Goal goal : all)
		{
			JPanel card = card(goal, allBounties, canEdit);
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(card);
			list.add(Box.createVerticalStrut(6));
		}
		revalidate();
		repaint();
	}

	private JPanel card(GoalService.Goal goal, List<BountyService.Bounty> allBounties, boolean canEdit)
	{
		GoalData data = goal.getData();
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 6, 6, 6));

		JLabel title = new JLabel(GoalService.title(data));
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		card.add(title);
		card.add(small("Set by " + (data.getCreatedByName() == null ? "someone" : data.getCreatedByName())
			+ (data.getMembers() == null || data.getMembers().isEmpty() ? " for everyone" : ""), ColorScheme.MEDIUM_GRAY_COLOR));

		Map<String, String> names = new HashMap<>();
		for (GoalService.Member member : members)
		{
			names.put(member.getId(), member.getName());
			if (!data.appliesTo(member.getId()))
			{
				continue;
			}
			GoalService.Progress progress = goals.progress(data, member.getId());
			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			JLabel name = small((progress.isDone() ? "✓ " : "   ") + member.getName(),
				progress.isDone() ? DONE : ColorScheme.LIGHT_GRAY_COLOR);
			row.add(name, BorderLayout.WEST);
			if (!progress.isDone())
			{
				row.add(small(progress.getText(), ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
			}
			card.add(row);
		}

		for (BountyService.Bounty bounty : allBounties)
		{
			if (goal.getId().equals(bounty.getData().getGoalId()))
			{
				card.add(bountyRow(bounty, names, canEdit));
			}
		}

		if (canEdit)
		{
			JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(4, 0, 0, 0));
			buttons.add(button(goal.getId().equals(bountyGoal) ? "Close bounty" : "Add bounty", () ->
			{
				bountyGoal = goal.getId().equals(bountyGoal) ? null : goal.getId();
				bountyItem = null;
				bountyMembers.clear();
				refresh();
			}));
			buttons.add(button("Delete", () ->
			{
				goals.delete(goal.getId());
				refresh();
			}));
			card.add(buttons);
			if (goal.getId().equals(bountyGoal))
			{
				card.add(bountyForm(goal));
			}
		}

		for (Component c : card.getComponents())
		{
			((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		return card;
	}

	private JPanel bountyRow(BountyService.Bounty bounty, Map<String, String> names, boolean canEdit)
	{
		BountyData data = bounty.getData();
		List<String> locked = new ArrayList<>();
		List<String> lifted = new ArrayList<>();
		if (data.getLocked() != null)
		{
			for (String id : data.getLocked())
			{
				String name = names.getOrDefault(id, "someone");
				(bounties.isLocked(data, id) ? locked : lifted).add(name);
			}
		}
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(4, 0, 0, 0));
		String item = data.getItemName() == null ? "item " + data.getItemId() : data.getItemName();
		String text = "<html><body style='width:150px'>Bounty: " + item
			+ (locked.isEmpty() ? "<br>Lifted for everyone" : "<br>Locked for " + String.join(", ", locked))
			+ (lifted.isEmpty() || locked.isEmpty() ? "" : "<br>Lifted for " + String.join(", ", lifted))
			+ "</body></html>";
		JLabel label = small(text, locked.isEmpty() ? DONE : LOCKED);
		row.add(label, BorderLayout.CENTER);
		if (canEdit)
		{
			row.add(button("Remove", () ->
			{
				bounties.delete(bounty.getId());
				refresh();
			}), BorderLayout.EAST);
		}
		return row;
	}

	private JPanel bountyForm(GoalService.Goal goal)
	{
		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setOpaque(false);
		form.setBorder(new EmptyBorder(6, 0, 0, 0));

		ItemsData items = storage.get();
		List<Integer> ids = new ArrayList<>();
		if (items != null)
		{
			for (Item item : items.toItems())
			{
				if (item != null && item.getId() > 0 && !ids.contains(item.getId()))
				{
					ids.add(item.getId());
				}
			}
		}
		if (ids.isEmpty())
		{
			form.add(hint("Open your group storage in game first, so the plugin knows what's in it."));
			return form;
		}

		if (bountyItem == null || !ids.equals(bountyItemIds))
		{
			bountyItemIds = ids;
			bountyItem = new JComboBox<>(names(ids));
			resolveNames(ids);
		}
		form.add(field("Item in group storage", bountyItem));
		form.add(small("Locked until they reach the goal:", ColorScheme.LIGHT_GRAY_COLOR));
		for (GoalService.Member member : members)
		{
			if (!goal.getData().appliesTo(member.getId()))
			{
				continue;
			}
			JCheckBox box = bountyMembers.computeIfAbsent(member.getId(), id ->
			{
				JCheckBox b = new JCheckBox(member.getName());
				// people who already reached the goal wouldn't be held back anyway
				b.setSelected(!goals.progress(goal.getData(), id).isDone());
				b.setOpaque(false);
				b.setFont(FontManager.getRunescapeSmallFont());
				return b;
			});
			form.add(box);
		}
		form.add(button("Create bounty", () ->
		{
			int index = bountyItem.getSelectedIndex();
			List<String> locked = new ArrayList<>();
			for (Map.Entry<String, JCheckBox> e : bountyMembers.entrySet())
			{
				if (e.getValue().isSelected())
				{
					locked.add(e.getKey());
				}
			}
			if (index < 0 || index >= bountyItemIds.size() || locked.isEmpty())
			{
				return;
			}
			int itemId = bountyItemIds.get(index);
			bounties.create(itemId, ITEM_NAMES.getOrDefault(itemId, "item " + itemId), goal.getId(), locked);
			bountyGoal = null;
			bountyItem = null;
			bountyMembers.clear();
			refresh();
		}));
		for (Component c : form.getComponents())
		{
			((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		return form;
	}

	private String[] names(List<Integer> ids)
	{
		String[] out = new String[ids.size()];
		for (int i = 0; i < ids.size(); i++)
		{
			out[i] = ITEM_NAMES.getOrDefault(ids.get(i), "item " + ids.get(i));
		}
		return out;
	}

	/**
	 * Item names come from the game's cache, which can only be read on the client thread.
	 */
	private void resolveNames(List<Integer> ids)
	{
		List<Integer> missing = new ArrayList<>();
		for (Integer id : ids)
		{
			if (!ITEM_NAMES.containsKey(id))
			{
				missing.add(id);
			}
		}
		if (missing.isEmpty())
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			for (Integer id : missing)
			{
				ITEM_NAMES.put(id, itemManager.getItemComposition(id).getName());
			}
			SwingUtilities.invokeLater(() ->
			{
				if (bountyItem != null && ids.equals(bountyItemIds))
				{
					int selected = bountyItem.getSelectedIndex();
					bountyItem.setModel(new DefaultComboBoxModel<>(names(ids)));
					bountyItem.setSelectedIndex(Math.max(0, selected));
				}
			});
		});
	}

	private void typeChanged()
	{
		GoalData.Type type = selectedType();
		List<String> options = new ArrayList<>();
		String amount = "";
		boolean needsTarget = true;
		switch (type)
		{
			case SKILL:
				for (Skill skill : Skill.values())
				{
					options.add(skill.getName());
				}
				amount = "50";
				break;
			case QUEST:
				for (Quest quest : Quest.values())
				{
					options.add(quest.getName());
				}
				Collections.sort(options, String.CASE_INSENSITIVE_ORDER);
				break;
			case DIARY:
				for (String region : DiariesData.REGIONS)
				{
					for (String tier : DiariesData.TIERS)
					{
						options.add(region + " " + tier);
					}
				}
				break;
			case KILL_COUNT:
				options.addAll(Arrays.asList("Vorkath", "Zulrah", "Barrows", "Chambers of Xeric", "Theatre of Blood",
					"Tombs of Amascut", "General Graardor", "Kree'arra", "Commander Zilyana", "K'ril Tsutsaroth",
					"Corporeal Beast", "Giant Mole", "Kalphite Queen", "King Black Dragon", "Wintertodt", "Tempoross"));
				amount = "50";
				break;
			case TOTAL_LEVEL:
				needsTarget = false;
				amount = "500";
				break;
			default:
				needsTarget = false;
				amount = "100";
		}
		targetBox.setModel(new DefaultComboBoxModel<>(options.toArray(new String[0])));
		targetBox.setEnabled(needsTarget);
		targetBox.setEditable(type == GoalData.Type.KILL_COUNT || type == GoalData.Type.QUEST);
		amountField.setText(amount);
		amountField.setEnabled(!amount.isEmpty());
	}

	private void submit()
	{
		GoalData.Type type = selectedType();
		Object target = targetBox.isEnabled() ? targetBox.getEditor().getItem() : null;
		int amount = 0;
		if (amountField.isEnabled())
		{
			try
			{
				amount = Integer.parseInt(amountField.getText().trim());
			}
			catch (NumberFormatException e)
			{
				formHint.setText("Amount must be a number.");
				return;
			}
		}
		List<String> for_ = new ArrayList<>();
		int who = forBox.getSelectedIndex();
		if (who > 0 && who - 1 < members.size())
		{
			for_.add(members.get(who - 1).getId());
		}
		String id = goals.create(type, target == null ? null : target.toString(), amount, for_);
		if (id == null)
		{
			formHint.setText(type == GoalData.Type.SKILL ? "Pick a skill and a level from 1 to 99." : "Check the goal's details.");
			return;
		}
		formHint.setText("");
		refresh();
	}

	private GoalData.Type selectedType()
	{
		int index = typeBox.getSelectedIndex();
		return new ArrayList<>(TYPE_LABELS.keySet()).get(Math.max(0, index));
	}

	private static JButton button(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setMargin(new Insets(1, 4, 1, 4));
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

	private static JLabel hint(String text)
	{
		JLabel label = new JLabel("<html><body style='width:170px'>" + text + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel field(String label, JComponent input)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		JLabel l = new JLabel(label);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(l, BorderLayout.NORTH);
		panel.add(input, BorderLayout.CENTER);
		return panel;
	}
}
