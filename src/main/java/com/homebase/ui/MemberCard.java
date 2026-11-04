package com.homebase.ui;

import com.homebase.data.Clean;
import com.homebase.data.CollectionLogData;
import com.homebase.data.CombatAchievementsData;
import com.homebase.data.DiariesData;
import com.homebase.data.GroupView;
import com.homebase.data.HouseData;
import com.homebase.data.ItemsData;
import com.homebase.data.KillCountsData;
import com.homebase.data.ProfileData;
import com.homebase.data.QuestsData;
import com.homebase.data.Section;
import com.homebase.data.SkillsData;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Experience;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

/**
 * One group member: who they are, whether they're online with live bars, and when expanded
 * everything we know about them in rows that open one at a time.
 */
class MemberCard extends JPanel
{
	/**
	 * Gold, for a level 99 and for new collection log slots.
	 */
	private static final Color MAXED = new Color(0xFFD54F);

	/**
	 * The brown of the in-game skills tab.
	 */
	private static final Color SKILL_TILE = new Color(62, 53, 41);

	/**
	 * Diary tiers shortened, so all four fit beside the region's name.
	 */
	private static final String[] TIER_LABELS = {"E", "M", "H", "El"};

	private static final int DIARY_REGION_WIDTH = 100;

	/**
	 * Newest collection log slots listed above the pages.
	 */
	private static final int RECENT_SLOTS_SHOWN = 5;

	/**
	 * Longest text shown from other players' data: player names, then boss and furniture names, then
	 * quest, collection log page and item names.
	 */
	private static final int MAX_NAME = 20;
	private static final int MAX_SHORT_TEXT = 40;
	private static final int MAX_LONG_TEXT = 60;

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
		final ItemSprites itemSprites;
		final SkillIconManager skillIconManager;
		final Set<String> openSections;
		final Consumer<String> toggleSection;

		/**
		 * Whether cards show live bars, which the player can turn off.
		 */
		final BooleanSupplier showBars;

		Context(ItemSprites itemSprites, SkillIconManager skillIconManager, Set<String> openSections,
			Consumer<String> toggleSection, BooleanSupplier showBars)
		{
			this.showBars = showBars;
			this.itemSprites = itemSprites;
			this.skillIconManager = skillIconManager;
			this.openSections = openSections;
			this.toggleSection = toggleSection;
		}
	}

	private final JLabel nameLabel;
	private final JLabel statusLabel = Ui.small("", ColorScheme.MEDIUM_GRAY_COLOR);
	private final JPanel bars = new JPanel(new GridLayout(1, 4, 3, 0));
	private final Ui.Meter hpBar = Ui.bar(Ui.HP_FG, Ui.HP_BG);
	private final Ui.Meter prayerBar = Ui.bar(Ui.PRAY_FG, Ui.PRAY_BG);
	private final Ui.Meter runBar = Ui.bar(Ui.RUN_FG, Ui.RUN_BG);
	private final Ui.Meter specBar = Ui.bar(Ui.SPEC_FG, Ui.SPEC_BG);
	private final BooleanSupplier showBars;
	private final ProfileData profile;
	private final long profileUpdatedAt;

	MemberCard(String memberId, GroupView view, boolean expanded, Runnable onToggle, Context context)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		showBars = context.showBars;

		profile = view.get(memberId, Section.PROFILE, ProfileData.class);
		profileUpdatedAt = view.updatedAt(memberId, Section.PROFILE);
		SkillsData skills = view.get(memberId, Section.SKILLS, SkillsData.class);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setBorder(new EmptyBorder(7, 8, 7, 8));
		Ui.hover(top, ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARKER_GRAY_HOVER_COLOR);
		Ui.clickable(top, onToggle);
		top.setToolTipText(expanded ? "Click to close" : "Click for everything we know about them");

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		nameLabel = Ui.title(profile == null ? "Unknown" : Clean.text(profile.getName(), MAX_NAME));
		nameLabel.setIconTextGap(6);
		header.add(nameLabel, BorderLayout.CENTER);
		header.add(statusLabel, BorderLayout.EAST);
		top.add(align(header));

		if (skills != null)
		{
			int combat = Experience.getCombatLevel(skills.getLevel(Skill.ATTACK), skills.getLevel(Skill.STRENGTH),
				skills.getLevel(Skill.DEFENCE), skills.getLevel(Skill.HITPOINTS), skills.getLevel(Skill.MAGIC),
				skills.getLevel(Skill.RANGED), skills.getLevel(Skill.PRAYER));
			JLabel levels = Ui.small("Combat " + combat + "  ·  Total " + QuantityFormatter.formatNumber(skills.getTotalLevel()),
				ColorScheme.LIGHT_GRAY_COLOR);
			// lines up with the name, past the online dot
			levels.setBorder(new EmptyBorder(2, 13, 0, 0));
			top.add(align(levels));
		}

		bars.setOpaque(false);
		bars.setBorder(new EmptyBorder(6, 0, 0, 0));
		hpBar.setToolTipText("Hitpoints");
		prayerBar.setToolTipText("Prayer");
		runBar.setToolTipText("Run energy");
		specBar.setToolTipText("Special attack");
		bars.add(hpBar);
		bars.add(prayerBar);
		bars.add(runBar);
		bars.add(specBar);
		top.add(align(bars));
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
		nameLabel.setIcon(new Ui.Dot(online ? Ui.ONLINE : Ui.AWAY));
		if (online)
		{
			int world = profile == null ? 0 : profile.getWorld();
			statusLabel.setText(world > 0 ? "World " + world : "Online");
			statusLabel.setForeground(Ui.ONLINE);
		}
		else
		{
			statusLabel.setText("Seen " + Ago.format(profileUpdatedAt));
			statusLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}

		if (showBars.getAsBoolean() && live != null && live.isLoggedIn() && live.getStatBase(Skill.HITPOINTS) > 0)
		{
			int hp = live.getStatBoost(Skill.HITPOINTS);
			int maxHp = live.getStatBase(Skill.HITPOINTS);
			int prayer = live.getStatBoost(Skill.PRAYER);
			int maxPrayer = live.getStatBase(Skill.PRAYER);
			hpBar.set(hp, maxHp, hp + "/" + maxHp);
			prayerBar.set(prayer, maxPrayer, prayer + "/" + maxPrayer);
			runBar.set(live.getRunEnergy(), 100, live.getRunEnergy() + "%");
			specBar.set(live.getSpecEnergy(), 100, live.getSpecEnergy() + "%");
			bars.setVisible(true);
		}
		else
		{
			bars.setVisible(false);
		}
	}

	private static JPanel buildDetails(String memberId, GroupView view, SkillsData skills, Context context)
	{
		JPanel details = new JPanel();
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		details.setBorder(new EmptyBorder(0, 8, 8, 8));

		if (skills != null && addFoldRow(details, "Skills", "Total " + QuantityFormatter.formatNumber(skills.getTotalLevel())
			+ "  ·  " + Ago.format(view.updatedAt(memberId, Section.SKILLS)), memberId + ":" + Section.SKILLS.getKey(), context))
		{
			addContent(details, skillGrid(skills, context.skillIconManager));
		}

		addQuests(details, memberId, view, context);
		addDiaries(details, memberId, view, context);
		addCombatAchievements(details, memberId, view);
		addCollectionLog(details, memberId, view, context);
		addKillCounts(details, memberId, view, context);
		addHouse(details, memberId, view, context);
		details.add(Box.createVerticalStrut(6));
		addItems(details, "Inventory", " items", memberId, Section.INVENTORY, view, context);
		addItems(details, "Equipment", " worn", memberId, Section.EQUIPMENT, view, context);
		addCollapsible(details, "Bank", memberId, Section.BANK, view, context);
		addCollapsible(details, "Seed vault", memberId, Section.SEED_VAULT, view, context);
		addCollapsible(details, "Looting bag", memberId, Section.LOOTING_BAG, view, context);

		Ui.alignChildrenLeft(details);
		return details;
	}

	/**
	 * A row that opens a section, with a little space under it.
	 */
	private static void addSectionRow(JPanel parent, String title, String summary, String id, Context context)
	{
		parent.add(Ui.sectionRow(title, summary, context.openSections.contains(id), () -> context.toggleSection.accept(id)));
		parent.add(Box.createVerticalStrut(2));
	}

	/**
	 * A row for a section that starts open and can be folded away, such as skills. Folding is
	 * remembered while RuneLite is open.
	 *
	 * @return true if the section is open
	 */
	private static boolean addFoldRow(JPanel parent, String title, String summary, String id, Context context)
	{
		String folded = id + ":folded";
		boolean open = !context.openSections.contains(folded);
		parent.add(Ui.sectionRow(title, summary, open, () -> context.toggleSection.accept(folded)));
		parent.add(Box.createVerticalStrut(2));
		return open;
	}

	/**
	 * What an open section shows, indented a little under its row.
	 */
	private static void addContent(JPanel parent, JComponent content)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(new EmptyBorder(2, 6, 6, 6));
		wrap.add(content, BorderLayout.CENTER);
		parent.add(wrap);
	}

	/**
	 * The inventory or worn equipment, which are small enough to start open.
	 */
	private static void addItems(JPanel parent, String title, String noun, String memberId, Section section,
		GroupView view, Context context)
	{
		ItemsData items = view.get(memberId, section, ItemsData.class);
		if (items == null)
		{
			return;
		}
		List<Item> slots = items.toItems();
		int count = ItemGrid.countFilled(slots);
		if (addFoldRow(parent, title, count + noun + "  ·  " + Ago.format(view.updatedAt(memberId, section)),
			memberId + ":" + section.getKey(), context) && count > 0)
		{
			parent.add(new ItemGrid(slots, context.itemSprites));
			parent.add(Box.createVerticalStrut(4));
		}
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
		List<Item> slots = items.toItems();
		String id = memberId + ":" + section.getKey();
		addSectionRow(parent, title, ItemGrid.countFilled(slots) + " items · " + Ago.format(view.updatedAt(memberId, section)), id, context);
		if (context.openSections.contains(id))
		{
			parent.add(new ItemGrid(slots, context.itemSprites));
			parent.add(Box.createVerticalStrut(4));
		}
	}

	private static void addQuests(JPanel parent, String memberId, GroupView view, Context context)
	{
		QuestsData quests = view.get(memberId, Section.QUESTS, QuestsData.class);
		if (quests == null || quests.getQuests() == null)
		{
			return;
		}
		int mini = quests.countMiniquests(QuestsData.FINISHED);
		String id = memberId + ":" + Section.QUESTS.getKey();
		addSectionRow(parent, "Quests", quests.count(QuestsData.FINISHED) + " done" + (mini > 0 ? " + " + mini + " mini" : ""), id, context);
		if (!context.openSections.contains(id))
		{
			return;
		}
		TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, String> e : quests.getQuests().entrySet())
		{
			String name = Clean.text(e.getKey(), MAX_LONG_TEXT);
			if (name != null)
			{
				sorted.put(name, e.getValue());
			}
		}
		JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));
		list.setOpaque(false);
		for (Map.Entry<String, String> e : sorted.entrySet())
		{
			boolean done = QuestsData.FINISHED.equals(e.getValue());
			JLabel quest = Ui.small((done ? "✓ " : "… ") + e.getKey() + (QuestsData.isMiniquest(e.getKey()) ? " (mini)" : ""),
				done ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_INPROGRESS_COLOR);
			list.add(quest);
		}
		addContent(parent, list);
	}

	private static void addDiaries(JPanel parent, String memberId, GroupView view, Context context)
	{
		DiariesData diaries = view.get(memberId, Section.DIARIES, DiariesData.class);
		if (diaries == null || diaries.getDone() == null)
		{
			return;
		}
		String id = memberId + ":" + Section.DIARIES.getKey();
		int total = DiariesData.REGIONS.size() * DiariesData.TIERS.size();
		addSectionRow(parent, "Achievement diaries", diaries.countDone() + "/" + total, id, context);
		if (!context.openSections.contains(id))
		{
			return;
		}
		JPanel grid = new JPanel(new GridLayout(0, 1, 0, 1));
		grid.setOpaque(false);
		for (String region : DiariesData.REGIONS)
		{
			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			JLabel name = Ui.small(region, ColorScheme.LIGHT_GRAY_COLOR);
			// a fixed width, so every region's tiers line up in columns
			name.setPreferredSize(new Dimension(DIARY_REGION_WIDTH, name.getPreferredSize().height));
			name.setToolTipText(region);
			row.add(name, BorderLayout.WEST);
			JPanel tiers = new JPanel(new GridLayout(1, DiariesData.TIERS.size(), 2, 0));
			tiers.setOpaque(false);
			for (int tier = 0; tier < DiariesData.TIERS.size(); tier++)
			{
				boolean done = diaries.isDone(region, tier);
				JLabel cell = Ui.small(TIER_LABELS[tier], done ? Color.WHITE : ColorScheme.MEDIUM_GRAY_COLOR);
				cell.setHorizontalAlignment(SwingConstants.CENTER);
				cell.setOpaque(true);
				cell.setBackground(done ? ColorScheme.PROGRESS_COMPLETE_COLOR.darker() : ColorScheme.DARK_GRAY_COLOR);
				cell.setToolTipText(region + " " + DiariesData.TIERS.get(tier) + (done ? ": done" : ": not done"));
				tiers.add(cell);
			}
			row.add(tiers, BorderLayout.CENTER);
			grid.add(row);
		}
		addContent(parent, grid);
	}

	private static void addCombatAchievements(JPanel parent, String memberId, GroupView view)
	{
		CombatAchievementsData ca = view.get(memberId, Section.COMBAT_ACHIEVEMENTS, CombatAchievementsData.class);
		if (ca == null)
		{
			return;
		}
		String tier = ca.tierName();
		JPanel row = Ui.infoRow("Combat achievements", ca.getPoints() + " pts" + (tier == null ? "" : " · " + tier));
		row.setToolTipText(ca.getTasks() + " tasks done. " + (tier == null ? "No tier reached yet." : "Highest tier reached: " + tier + "."));
		parent.add(row);
		parent.add(Box.createVerticalStrut(2));
	}

	private static void addCollectionLog(JPanel parent, String memberId, GroupView view, Context context)
	{
		CollectionLogData log = view.get(memberId, Section.COLLECTION_LOG, CollectionLogData.class);
		if (log == null)
		{
			return;
		}
		Map<String, CollectionLogData.Page> pages = log.getPages() == null ? new HashMap<>() : log.getPages();
		List<String> recent = log.getRecent() == null ? new ArrayList<>() : log.getRecent();
		String id = memberId + ":" + Section.COLLECTION_LOG.getKey();
		String counts = log.getObtained() > 0 && log.getTotal() > 0
			? QuantityFormatter.formatNumber(log.getObtained()) + "/" + QuantityFormatter.formatNumber(log.getTotal())
			: "";
		addSectionRow(parent, "Collection log", counts, id, context);
		if (!context.openSections.contains(id))
		{
			return;
		}
		JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));
		list.setOpaque(false);
		int shown = 0;
		for (String item : recent)
		{
			String name = Clean.text(item, MAX_LONG_TEXT);
			if (name != null && shown++ < RECENT_SLOTS_SHOWN)
			{
				list.add(Ui.small("New: " + name, MAXED));
			}
		}
		TreeMap<String, CollectionLogData.Page> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Map.Entry<String, CollectionLogData.Page> e : pages.entrySet())
		{
			String name = Clean.text(e.getKey(), MAX_LONG_TEXT);
			if (name != null && e.getValue() != null)
			{
				sorted.put(name, e.getValue());
			}
		}
		int listed = 0;
		for (Map.Entry<String, CollectionLogData.Page> e : sorted.entrySet())
		{
			CollectionLogData.Page page = e.getValue();
			int have = page.getItems() == null ? 0 : page.getItems().size();
			if (have == 0)
			{
				// only pages with something on them are worth showing
				continue;
			}
			boolean complete = have >= page.getSize() && page.getSize() > 0;
			JPanel row = valueRow(e.getKey(), have + "/" + page.getSize(), complete ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
			row.setToolTipText("Seen " + Ago.format(page.getSeen()));
			list.add(row);
			listed++;
		}
		if (listed == 0 && recent.isEmpty())
		{
			list.add(Ui.wrapped("Pages appear here once they open their collection log and click Search.", ColorScheme.MEDIUM_GRAY_COLOR));
		}
		addContent(parent, list);
	}

	private static void addKillCounts(JPanel parent, String memberId, GroupView view, Context context)
	{
		KillCountsData kills = view.get(memberId, Section.KILL_COUNTS, KillCountsData.class);
		if (kills == null || kills.getCounts() == null)
		{
			return;
		}
		List<Map.Entry<String, Integer>> rows = new ArrayList<>();
		for (Map.Entry<String, Integer> e : kills.getCounts().entrySet())
		{
			String name = Clean.text(e.getKey(), MAX_SHORT_TEXT);
			// a collection log page shows "kills: 0" for bosses never fought, which says nothing
			if (name != null && e.getValue() != null && e.getValue() > 0)
			{
				rows.add(new AbstractMap.SimpleEntry<>(name, e.getValue()));
			}
		}
		if (rows.isEmpty())
		{
			return;
		}
		rows.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
		String id = memberId + ":" + Section.KILL_COUNTS.getKey();
		addSectionRow(parent, "Kill counts", rows.size() + (rows.size() == 1 ? " boss" : " bosses"), id, context);
		if (!context.openSections.contains(id))
		{
			return;
		}
		JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));
		list.setOpaque(false);
		for (Map.Entry<String, Integer> e : rows)
		{
			list.add(valueRow(e.getKey(), QuantityFormatter.formatNumber(e.getValue()), Color.WHITE));
		}
		addContent(parent, list);
	}

	private static void addHouse(JPanel parent, String memberId, GroupView view, Context context)
	{
		HouseData house = view.get(memberId, Section.HOUSE, HouseData.class);
		if (house == null)
		{
			return;
		}
		List<String> furniture = house.getFurniture() == null ? new ArrayList<>() : house.getFurniture();
		List<Integer> costumes = house.getCostumes() == null ? new ArrayList<>() : house.getCostumes();
		String id = memberId + ":" + Section.HOUSE.getKey();
		addSectionRow(parent, "House", furniture.size() + " built" + (costumes.isEmpty() ? "" : " · " + costumes.size() + " costumes"), id, context);
		if (!context.openSections.contains(id))
		{
			return;
		}
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		for (String item : furniture)
		{
			String name = Clean.text(item, MAX_SHORT_TEXT);
			if (name != null)
			{
				content.add(align(Ui.small(name, ColorScheme.LIGHT_GRAY_COLOR)));
			}
		}
		if (!costumes.isEmpty())
		{
			List<Item> items = new ArrayList<>();
			for (Integer itemId : costumes)
			{
				if (itemId != null && itemId > 0)
				{
					items.add(new Item(itemId, 1));
				}
			}
			content.add(align(caption("Costume room")));
			content.add(align(new ItemGrid(items, context.itemSprites)));
		}
		addContent(parent, content);
	}

	/**
	 * Skills laid out like the in-game skills tab: icon and yellow level on a brown tile, gold at 99.
	 */
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
			int level = skills.getLevel(skill);
			JLabel label = Ui.small(String.valueOf(level), level >= Experience.MAX_REAL_LEVEL ? MAXED : Color.YELLOW);
			label.setIcon(new ImageIcon(skillIconManager.getSkillImage(skill, true)));
			label.setIconTextGap(6);
			label.setOpaque(true);
			label.setBackground(SKILL_TILE);
			label.setBorder(new EmptyBorder(3, 6, 3, 4));
			label.setToolTipText(skill.getName() + ": " + QuantityFormatter.formatNumber(skills.getXp(skill)) + " xp");
			grid.add(label);
		}
		return grid;
	}

	/**
	 * A small grey heading over a block.
	 */
	private static JPanel caption(String title)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setBorder(new EmptyBorder(6, 0, 3, 0));
		row.add(Ui.small(title, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static JPanel valueRow(String name, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(Ui.small(name, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.CENTER);
		row.add(Ui.small(value, valueColor), BorderLayout.EAST);
		return row;
	}

	private static <T extends JComponent> T align(T component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		return component;
	}
}
