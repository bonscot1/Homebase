package com.homebase.ui;

import com.homebase.data.GroupView;
import com.homebase.data.ItemsData;
import com.homebase.data.ProfileData;
import com.homebase.data.Section;
import com.homebase.data.SkillsData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * One group member: who they are, whether they're online, and when expanded everything we know.
 */
class MemberCard extends JPanel
{
	private static final Color ONLINE = new Color(0x4caf50);

	/**
	 * Skills in the order of the in-game skills tab, read across three columns.
	 */
	private static final List<Skill> SKILL_ORDER = Arrays.asList(
		Skill.ATTACK, Skill.HITPOINTS, Skill.MINING,
		Skill.STRENGTH, Skill.AGILITY, Skill.SMITHING,
		Skill.DEFENCE, Skill.HERBLORE, Skill.FISHING,
		Skill.RANGED, Skill.THIEVING, Skill.COOKING,
		Skill.PRAYER, Skill.CRAFTING, Skill.FIREMAKING,
		Skill.MAGIC, Skill.FLETCHING, Skill.WOODCUTTING,
		Skill.RUNECRAFT, Skill.SLAYER, Skill.FARMING,
		Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING);

	/**
	 * Shared by every card so open sections survive the panel being rebuilt.
	 */
	static class Context
	{
		final ItemManager itemManager;
		final SkillIconManager skillIconManager;
		final ClientThread clientThread;
		final Set<String> openSections;
		final Consumer<String> toggleSection;

		Context(ItemManager itemManager, SkillIconManager skillIconManager, ClientThread clientThread,
			Set<String> openSections, Consumer<String> toggleSection)
		{
			this.itemManager = itemManager;
			this.skillIconManager = skillIconManager;
			this.clientThread = clientThread;
			this.openSections = openSections;
			this.toggleSection = toggleSection;
		}
	}

	private final JLabel statusLabel = new JLabel();
	private final JLabel liveLabel = new JLabel();
	private final ProfileData profile;
	private final long profileUpdatedAt;

	MemberCard(String memberId, GroupView view, boolean expanded, Runnable onToggle, Context context)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 6, 6, 6));

		profile = view.get(memberId, Section.PROFILE, ProfileData.class);
		profileUpdatedAt = view.updatedAt(memberId, Section.PROFILE);
		SkillsData skills = view.get(memberId, Section.SKILLS, SkillsData.class);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		clickable(header, onToggle);

		JLabel name = new JLabel((expanded ? "▾ " : "▸ ") + (profile == null ? "Unknown" : profile.getName()));
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		header.add(name, BorderLayout.WEST);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		header.add(statusLabel, BorderLayout.EAST);

		JPanel summary = new JPanel(new GridLayout(0, 1));
		summary.setOpaque(false);
		liveLabel.setFont(FontManager.getRunescapeSmallFont());
		liveLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		summary.add(liveLabel);
		if (skills != null)
		{
			summary.add(smallLabel("Total level " + QuantityFormatter.formatNumber(skills.getTotalLevel())));
		}

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(header, BorderLayout.NORTH);
		top.add(summary, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);

		if (expanded)
		{
			add(buildDetails(memberId, view, skills, context), BorderLayout.CENTER);
		}

		updateLive(null, false);
	}

	String getMemberName()
	{
		return profile == null ? null : profile.getName();
	}

	/**
	 * Refresh the parts that change every tick for online members.
	 *
	 * @param live their party data, or null if they aren't in the party right now
	 * @param isLocal true if this card is the logged in player
	 */
	void updateLive(PartyMember live, boolean isLocal)
	{
		boolean online = isLocal || (live != null && live.isLoggedIn());
		if (online)
		{
			int world = profile == null ? 0 : profile.getWorld();
			statusLabel.setText(world > 0 ? "● W" + world : "● Online");
			statusLabel.setForeground(ONLINE);
		}
		else
		{
			statusLabel.setText("Seen " + Ago.format(profileUpdatedAt));
			statusLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}

		if (live != null && live.isLoggedIn() && live.getStatBase(Skill.HITPOINTS) > 0)
		{
			liveLabel.setText(String.format("HP %d/%d  Pray %d/%d  Run %d%%",
				live.getStatBoost(Skill.HITPOINTS), live.getStatBase(Skill.HITPOINTS),
				live.getStatBoost(Skill.PRAYER), live.getStatBase(Skill.PRAYER),
				live.getRunEnergy()));
			liveLabel.setVisible(true);
		}
		else
		{
			liveLabel.setVisible(false);
		}
	}

	private static JPanel buildDetails(String memberId, GroupView view, SkillsData skills, Context context)
	{
		JPanel details = new JPanel();
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setOpaque(false);
		details.setBorder(new EmptyBorder(6, 0, 0, 0));

		if (skills != null)
		{
			details.add(heading("Skills  ·  " + Ago.format(view.updatedAt(memberId, Section.SKILLS))));
			details.add(skillGrid(skills, context.skillIconManager));
		}

		addItems(details, "Inventory", memberId, Section.INVENTORY, 4, true, view, context);
		addItems(details, "Equipment", memberId, Section.EQUIPMENT, 5, false, view, context);
		addCollapsible(details, "Bank", memberId, Section.BANK, view, context);
		addCollapsible(details, "Seed vault", memberId, Section.SEED_VAULT, view, context);
		addCollapsible(details, "Looting bag", memberId, Section.LOOTING_BAG, view, context);

		for (Component c : details.getComponents())
		{
			((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		return details;
	}

	private static void addItems(JPanel parent, String title, String memberId, Section section, int columns,
		boolean keepEmptySlots, GroupView view, Context context)
	{
		ItemsData items = view.get(memberId, section, ItemsData.class);
		if (items == null || (items.isEmpty() && !keepEmptySlots))
		{
			return;
		}
		parent.add(heading(title + "  ·  " + Ago.format(view.updatedAt(memberId, section))));
		parent.add(new ItemGrid(items.toItems(), columns, keepEmptySlots, context.itemManager, context.clientThread));
	}

	/**
	 * Big containers start collapsed, so a full bank doesn't bury everything below it.
	 */
	private static void addCollapsible(JPanel parent, String title, String memberId, Section section,
		GroupView view, Context context)
	{
		ItemsData items = view.get(memberId, section, ItemsData.class);
		if (items == null || items.isEmpty())
		{
			return;
		}
		String id = memberId + ":" + section.getKey();
		boolean open = context.openSections.contains(id);
		List<Item> stacks = new ArrayList<>();
		for (Item item : items.toItems())
		{
			if (item != null)
			{
				stacks.add(item);
			}
		}
		JLabel label = heading((open ? "▾ " : "▸ ") + title + "  ·  " + stacks.size() + " items  ·  "
			+ Ago.format(view.updatedAt(memberId, section)));
		label.setForeground(Color.WHITE);
		clickable(label, () -> context.toggleSection.accept(id));
		parent.add(label);
		if (open)
		{
			parent.add(new ItemGrid(stacks, 5, false, context.itemManager, context.clientThread));
		}
	}

	private static JPanel skillGrid(SkillsData skills, SkillIconManager skillIconManager)
	{
		List<Skill> order = new ArrayList<>(SKILL_ORDER);
		for (Skill skill : Skill.values())
		{
			if (!order.contains(skill))
			{
				order.add(skill);
			}
		}

		JPanel grid = new JPanel(new GridLayout(0, 3, 2, 2));
		grid.setOpaque(false);
		for (Skill skill : order)
		{
			JLabel label = new JLabel(String.valueOf(skills.getLevel(skill)));
			label.setIcon(new ImageIcon(skillIconManager.getSkillImage(skill, true)));
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(Color.WHITE);
			label.setToolTipText(skill.getName() + ": " + QuantityFormatter.formatNumber(skills.getXp(skill)) + " xp");
			grid.add(label);
		}
		return grid;
	}

	static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		return label;
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

	private static JLabel smallLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}
}
