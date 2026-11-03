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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * Set goals for the group and watch them tick off, and put bounties on group storage items.
 */
class GoalsView extends JPanel
{
	private static final Color DONE = new Color(0x4CAF50);
	private static final Color LOCKED = new Color(0xEF5350);

	/**
	 * Goal bars, in the colours of RuneLite's XP tracker.
	 */
	private static final Color GOAL_FG = new Color(0xDC8A00);
	private static final Color GOAL_BG = new Color(61, 56, 49);

	private static final int PROGRESS_BAR_WIDTH = 84;

	/**
	 * Width a bounty's text wraps at, leaving room for the item's picture and the remove button.
	 */
	private static final int BOUNTY_TEXT_WIDTH = 95;

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

	/**
	 * Offered for kill count goals. Any other boss can be typed in.
	 */
	private static final List<String> SUGGESTED_BOSSES = Arrays.asList("Vorkath", "Zulrah", "Barrows",
		"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut", "General Graardor", "Kree'arra",
		"Commander Zilyana", "K'ril Tsutsaroth", "Corporeal Beast", "Giant Mole", "Kalphite Queen",
		"King Black Dragon", "Wintertodt", "Tempoross");

	private final GoalService goals;
	private final BountyService bounties;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Supplier<ItemsData> storage;
	private final Map<Integer, ImageIcon> itemIcons = new HashMap<>();

	private final JComboBox<String> typeBox = new JComboBox<>(TYPE_LABELS.values().toArray(new String[0]));
	private final JComboBox<String> targetBox = new JComboBox<>();
	private final JTextField amountField = new JTextField();
	private final JComboBox<String> forBox = new JComboBox<>();
	private final JButton createButton = Ui.primaryButton("Set goal", this::submit);
	private final JLabel formHint = Ui.muted("");
	private final JPanel list = new JPanel();

	/**
	 * The goal whose bounty form is open, and the form's state, kept across rebuilds.
	 */
	private String bountyGoalId;
	private JComboBox<Integer> bountyItemBox;
	private List<Integer> bountyItemIds = new ArrayList<>();
	private Map<Integer, Integer> storageQuantities = new HashMap<>();
	private final Map<String, JCheckBox> bountyLockedFor = new LinkedHashMap<>();
	private final JTextField bountyAmountField = new JTextField("1");

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
		form.setBorder(new EmptyBorder(8, 8, 8, 8));

		typeBox.addActionListener(e -> typeChanged());
		amountField.setColumns(4);

		form.add(Ui.title("Set a goal"));
		form.add(Box.createVerticalStrut(6));
		form.add(Ui.field("Goal", typeBox));
		form.add(Box.createVerticalStrut(4));
		JPanel targetRow = new JPanel(new BorderLayout(4, 0));
		targetRow.setOpaque(false);
		targetRow.add(Ui.field("What", targetBox), BorderLayout.CENTER);
		targetRow.add(Ui.field("Amount", amountField), BorderLayout.EAST);
		form.add(targetRow);
		form.add(Box.createVerticalStrut(4));
		form.add(Ui.field("For", forBox));
		form.add(Box.createVerticalStrut(8));
		JPanel buttonRow = new JPanel(new BorderLayout());
		buttonRow.setOpaque(false);
		buttonRow.add(createButton, BorderLayout.CENTER);
		form.add(buttonRow);
		form.add(formHint);
		Ui.alignChildrenLeft(form);

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
		List<GoalService.Goal> allGoals = goals.list();
		if (allGoals.isEmpty())
		{
			list.add(Ui.empty("No goals yet", "Set one above, for example 50 Firemaking for everyone before Wintertodt."));
		}
		List<BountyService.Bounty> allBounties = bounties.list();
		for (GoalService.Goal goal : allGoals)
		{
			JPanel card = goalCard(goal, allBounties, canEdit);
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(card);
			list.add(Box.createVerticalStrut(Ui.GAP));
		}
		revalidate();
		repaint();
	}

	private JPanel goalCard(GoalService.Goal goal, List<BountyService.Bounty> allBounties, boolean canEdit)
	{
		GoalData data = goal.getData();
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(8, 8, 8, 8));

		List<JPanel> progressRows = new ArrayList<>();
		Map<String, String> names = new HashMap<>();
		int memberCount = 0;
		int doneCount = 0;
		for (GoalService.Member member : members)
		{
			names.put(member.getId(), member.getName());
			if (!data.appliesTo(member.getId()))
			{
				continue;
			}
			memberCount++;
			GoalService.Progress progress = goals.progress(data, member.getId());
			if (progress.isDone())
			{
				doneCount++;
			}
			progressRows.add(progressRow(member.getName(), progress));
		}

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.add(Ui.title(GoalService.title(data)), BorderLayout.CENTER);
		if (memberCount > 0)
		{
			header.add(Ui.small(doneCount + "/" + memberCount + " done", doneCount == memberCount ? DONE : ColorScheme.LIGHT_GRAY_COLOR),
				BorderLayout.EAST);
		}
		card.add(header);
		JLabel setBy = Ui.small("Set by " + (data.getCreatedByName() == null ? "someone" : data.getCreatedByName())
			+ (data.getMembers() == null || data.getMembers().isEmpty() ? " for everyone" : ""), ColorScheme.MEDIUM_GRAY_COLOR);
		setBy.setBorder(new EmptyBorder(1, 0, 4, 0));
		card.add(setBy);
		for (JPanel row : progressRows)
		{
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
			boolean formOpen = goal.getId().equals(bountyGoalId);
			JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
			buttons.setOpaque(false);
			buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
			buttons.add(Ui.button(formOpen ? "Close bounty" : "Add bounty", () ->
			{
				bountyGoalId = formOpen ? null : goal.getId();
				resetBountyForm();
				refresh();
			}));
			buttons.add(Ui.button("Delete", () ->
			{
				goals.delete(goal.getId());
				refresh();
			}));
			card.add(buttons);
			if (formOpen)
			{
				card.add(bountyForm(goal));
			}
		}

		Ui.alignChildrenLeft(card);
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
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, locked.isEmpty() ? DONE : LOCKED),
			new EmptyBorder(3, 4, 3, 4)));
		// the item's picture tells apart items with similar names
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(Constants.ITEM_SPRITE_WIDTH, Constants.ITEM_SPRITE_HEIGHT));
		itemManager.getImage(data.getItemId()).addTo(icon);
		row.add(icon, BorderLayout.WEST);

		String item = (data.getItemName() == null ? "item " + data.getItemId() : data.getItemName())
			+ (data.getQuantity() > 0 ? " ×" + data.getQuantity() : " (all)");
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.add(Ui.small(item, Color.WHITE));
		// the text sits between the picture and the remove button, so names wrap rather than get cut off
		text.add(Ui.small(narrow(locked.isEmpty() ? "Lifted for everyone" : "Locked for " + String.join(", ", locked)),
			locked.isEmpty() ? DONE : LOCKED));
		if (!lifted.isEmpty() && !locked.isEmpty())
		{
			text.add(Ui.small(narrow("Lifted for " + String.join(", ", lifted)), DONE));
		}
		row.add(text, BorderLayout.CENTER);
		if (canEdit)
		{
			JButton remove = Ui.button("✕", () ->
			{
				bounties.delete(bounty.getId());
				refresh();
			});
			remove.setToolTipText("Remove this bounty");
			JPanel east = new JPanel(new BorderLayout());
			east.setOpaque(false);
			east.add(remove, BorderLayout.NORTH);
			row.add(east, BorderLayout.EAST);
		}
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(new EmptyBorder(6, 0, 0, 0));
		wrap.add(row, BorderLayout.CENTER);
		return wrap;
	}

	/**
	 * One member's progress on a goal: a tick once they're there, otherwise a bar for goals counted in
	 * numbers, or a word for the rest.
	 */
	private static JPanel progressRow(String name, GoalService.Progress progress)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.add(Ui.small(name, progress.isDone() ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.CENTER);
		if (progress.isDone())
		{
			row.add(Ui.small("✓ Done", DONE), BorderLayout.EAST);
		}
		else if (progress.getNeed() > 0)
		{
			Ui.Meter bar = Ui.bar(GOAL_FG, GOAL_BG);
			bar.setPreferredSize(new Dimension(PROGRESS_BAR_WIDTH, bar.getPreferredSize().height));
			bar.set(progress.getHave(), progress.getNeed(), progress.getText());
			row.add(bar, BorderLayout.EAST);
		}
		else
		{
			row.add(Ui.small(progress.getText(), ColorScheme.MEDIUM_GRAY_COLOR), BorderLayout.EAST);
		}
		return row;
	}

	private JPanel bountyForm(GoalService.Goal goal)
	{
		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBackground(ColorScheme.DARK_GRAY_COLOR);
		form.setBorder(new CompoundBorder(new MatteBorder(6, 0, 0, 0, ColorScheme.DARKER_GRAY_COLOR), new EmptyBorder(6, 6, 6, 6)));
		form.add(Ui.small("New bounty", Color.WHITE));
		form.add(Box.createVerticalStrut(4));

		ItemsData items = storage.get();
		Map<Integer, Integer> quantities = new LinkedHashMap<>();
		if (items != null)
		{
			for (Item item : items.toItems())
			{
				if (item != null && item.getId() > 0)
				{
					quantities.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		List<Integer> ids = new ArrayList<>(quantities.keySet());
		if (ids.isEmpty())
		{
			form.add(hint("Open your group storage in game first, so the plugin knows what's in it."));
			return form;
		}

		storageQuantities = quantities;
		if (bountyItemBox == null || !ids.equals(bountyItemIds))
		{
			bountyItemIds = ids;
			// pictures and counts tell apart items with similar names, such as the black and blue wizard hats
			bountyItemBox = new JComboBox<>(sortedByName(ids));
			bountyItemBox.setRenderer(new ItemRenderer());
			// tall enough for the pictures, and narrow so a long name can't widen the panel; it stretches to fit
			bountyItemBox.setPreferredSize(new Dimension(50, 38));
			resolveNames(ids);
		}
		form.add(Ui.field("Item in group storage", bountyItemBox));
		bountyAmountField.setColumns(5);
		bountyAmountField.setToolTipText("How many are held back; anything more in group storage is free for anyone. 0 holds them all.");
		JPanel amountRow = new JPanel(new BorderLayout(6, 0));
		amountRow.setOpaque(false);
		amountRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		amountRow.add(Ui.small("How many (0 for all)", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.CENTER);
		amountRow.add(bountyAmountField, BorderLayout.EAST);
		form.add(amountRow);
		JLabel lockedFor = Ui.small("Locked until they reach the goal:", ColorScheme.LIGHT_GRAY_COLOR);
		lockedFor.setBorder(new EmptyBorder(6, 0, 2, 0));
		form.add(lockedFor);
		for (GoalService.Member member : members)
		{
			if (!goal.getData().appliesTo(member.getId()))
			{
				continue;
			}
			JCheckBox box = bountyLockedFor.computeIfAbsent(member.getId(), id ->
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
		JPanel createRow = new JPanel(new BorderLayout());
		createRow.setOpaque(false);
		createRow.setBorder(new EmptyBorder(6, 0, 0, 0));
		createRow.add(Ui.primaryButton("Create bounty", () -> createBounty(goal)), BorderLayout.CENTER);
		form.add(createRow);
		Ui.alignChildrenLeft(form);
		return form;
	}

	private void createBounty(GoalService.Goal goal)
	{
		Integer itemId = (Integer) bountyItemBox.getSelectedItem();
		List<String> locked = new ArrayList<>();
		for (Map.Entry<String, JCheckBox> e : bountyLockedFor.entrySet())
		{
			if (e.getValue().isSelected())
			{
				locked.add(e.getKey());
			}
		}
		if (itemId == null || locked.isEmpty())
		{
			return;
		}
		int amount;
		try
		{
			amount = Math.max(0, Integer.parseInt(bountyAmountField.getText().trim()));
		}
		catch (NumberFormatException e)
		{
			amount = 1;
		}
		bounties.create(itemId, ItemNames.get(itemId), amount, goal.getId(), locked);
		bountyGoalId = null;
		resetBountyForm();
		refresh();
	}

	private void resetBountyForm()
	{
		bountyItemBox = null;
		bountyLockedFor.clear();
		bountyAmountField.setText("1");
	}

	private static Integer[] sortedByName(List<Integer> itemIds)
	{
		Integer[] sorted = itemIds.toArray(new Integer[0]);
		Arrays.sort(sorted, Comparator.comparing(ItemNames::get, String.CASE_INSENSITIVE_ORDER));
		return sorted;
	}

	/**
	 * Shows a group storage item in the bounty list with its picture, name and how many there are.
	 */
	private class ItemRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
		{
			super.getListCellRendererComponent(list, value, index, selected, focused);
			if (value instanceof Integer)
			{
				int id = (Integer) value;
				int quantity = storageQuantities.getOrDefault(id, 0);
				setText(ItemNames.get(id) + (quantity > 1 ? " ×" + QuantityFormatter.formatNumber(quantity) : ""));
				setIcon(itemIcon(id));
			}
			return this;
		}
	}

	private ImageIcon itemIcon(int itemId)
	{
		return itemIcons.computeIfAbsent(itemId, id ->
		{
			AsyncBufferedImage image = itemManager.getImage(id);
			// pictures finish loading on the client thread
			image.onLoaded(() -> SwingUtilities.invokeLater(() ->
			{
				if (bountyItemBox != null)
				{
					bountyItemBox.repaint();
				}
			}));
			return new ImageIcon(image);
		});
	}

	/**
	 * Look up the items' names, then put the list in A to Z order, keeping any choice already made.
	 */
	private void resolveNames(List<Integer> ids)
	{
		ItemNames.resolve(ids, itemManager, clientThread, () ->
		{
			if (bountyItemBox != null && ids.equals(bountyItemIds))
			{
				// the first item is only chosen by default, so whichever item sorts first now takes its place
				Object chosen = bountyItemBox.getSelectedIndex() > 0 ? bountyItemBox.getSelectedItem() : null;
				bountyItemBox.setModel(new DefaultComboBoxModel<>(sortedByName(ids)));
				if (chosen != null)
				{
					bountyItemBox.setSelectedItem(chosen);
				}
			}
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
				options.addAll(SUGGESTED_BOSSES);
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
		List<String> forMembers = new ArrayList<>();
		// the first choice is everyone, then each member in the order of members
		int forIndex = forBox.getSelectedIndex();
		if (forIndex > 0 && forIndex - 1 < members.size())
		{
			forMembers.add(members.get(forIndex - 1).getId());
		}
		String id = goals.create(type, target == null ? null : target.toString(), amount, forMembers);
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

	/**
	 * @return the text wrapped to fit beside a bounty's picture
	 */
	private static String narrow(String text)
	{
		return "<html><body style='width:" + BOUNTY_TEXT_WIDTH + "px'>" + text + "</body></html>";
	}

	private static JLabel hint(String text)
	{
		JLabel label = Ui.wrapped(text, ColorScheme.MEDIUM_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}
