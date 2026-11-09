package com.homebase.ui;

import com.homebase.HomebasePlugin;
import com.homebase.data.Clean;
import com.homebase.data.CollectionLogData;
import com.homebase.data.CombatAchievementsData;
import com.homebase.data.DiariesData;
import com.homebase.data.GroupView;
import com.homebase.data.KillCountsData;
import com.homebase.data.ProfileData;
import com.homebase.data.QuestsData;
import com.homebase.data.Section;
import com.homebase.data.SkillsData;
import com.homebase.hiscores.GroupHiscores;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

/**
 * Any two members side by side: levels, quests, diaries, combat achievements, the collection log, kill
 * counts and best times, with whoever is ahead in green and a winner at the top. Above them, a Group
 * Ironman group's place on the group hiscores, against the group one place above.
 */
class CompareView extends JPanel
{
	private static final int MAX_NAME = 20;
	private static final int VALUE_WIDTH = 38;
	private static final Color AHEAD = ColorScheme.PROGRESS_COMPLETE_COLOR;
	private static final String NONE = "-";

	private static final String OVERVIEW_ID = "compare:overview";
	private static final String SKILLS_ID = "compare:skills";
	private static final String KILLS_ID = "compare:kills";
	private static final String TIMES_ID = "compare:times";
	private static final String GROUP_ID = "compare:group";

	private final HomebasePlugin plugin;
	private final Supplier<GroupView> views;
	private final Supplier<String> me;
	private final MemberCard.Context context;
	private final GroupHiscores hiscores;

	/**
	 * Our Group Ironman group's name, or null for a group that joined with a passphrase.
	 */
	private final Supplier<String> gimGroup;

	/**
	 * The two members chosen, by member id.
	 */
	private String left;
	private String right;

	/**
	 * One row: what's compared, each member's value as shown, and who's ahead.
	 */
	static final class Line
	{
		final JComponent label;
		final String a;
		final String b;

		/**
		 * Shown on hovering each value, for values shortened to fit.
		 */
		final String tipA;
		final String tipB;

		/**
		 * Above 0 if the first member is ahead, below 0 if the second is.
		 */
		final int compare;

		Line(JComponent label, String a, String b, int compare)
		{
			this(label, a, b, a, b, compare);
		}

		Line(JComponent label, String a, String b, String tipA, String tipB, int compare)
		{
			this.label = label;
			this.a = a;
			this.b = b;
			this.tipA = tipA;
			this.tipB = tipB;
			this.compare = compare;
		}
	}

	CompareView(HomebasePlugin plugin, Supplier<GroupView> views, Supplier<String> me, MemberCard.Context context,
		GroupHiscores hiscores, Supplier<String> gimGroup)
	{
		this.plugin = plugin;
		this.views = views;
		this.me = me;
		this.context = context;
		this.hiscores = hiscores;
		this.gimGroup = gimGroup;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
	}

	void refresh()
	{
		removeAll();
		String group = gimGroup.get();
		if (group != null)
		{
			add(groupCard(group));
			add(Box.createVerticalStrut(6));
		}
		GroupView view = views.get();
		Map<String, String> members = view == null ? new LinkedHashMap<>() : members(view);
		if (members.size() < 2)
		{
			add(Ui.empty("Nobody to compare yet", "Once someone else in your group has shared their details, you can "
				+ "compare any two of you here."));
			revalidate();
			repaint();
			return;
		}
		List<String> ids = new ArrayList<>(members.keySet());
		if (left == null || !members.containsKey(left))
		{
			left = ids.get(0);
		}
		if (right == null || !members.containsKey(right) || right.equals(left))
		{
			right = ids.stream().filter(id -> !id.equals(left)).findFirst().orElse(null);
		}

		List<Line> overview = overview(view);
		List<Line> skills = skills(view);
		List<Line> kills = kills(view);
		List<Line> times = bestTimes(view);
		List<Line> all = new ArrayList<>();
		all.addAll(overview);
		all.addAll(skills);
		all.addAll(kills);
		all.addAll(times);

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(7, 8, 8, 8));
		// the two lists say whose numbers are in which column
		card.add(pickers(members));
		card.add(winner(members.get(left), members.get(right), all));

		addSection(card, "Overview", overview, OVERVIEW_ID);
		addSection(card, "Skills", skills, SKILLS_ID);
		addSection(card, "Kill counts", kills, KILLS_ID);
		addSection(card, "Best times", times, TIMES_ID);
		Ui.alignChildrenLeft(card);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(card);
		revalidate();
		repaint();
	}

	/**
	 * The group's place on the Group Ironman hiscores, what it'd take to pass the group one place above,
	 * and the two groups side by side.
	 */
	private JPanel groupCard(String groupName)
	{
		GroupHiscores.Standing standing = hiscores.standing(groupName, this::refresh);
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(7, 8, 8, 8));

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setOpaque(false);
		header.setBorder(new EmptyBorder(0, 0, 2, 0));
		header.add(Ui.title(Clean.text(groupName, MAX_NAME)), BorderLayout.CENTER);
		card.add(header);

		switch (standing.getState())
		{
			case LOOKING:
				card.add(Ui.muted("Looking your group up on the hiscores..."));
				break;
			case NOT_FOUND:
				card.add(Ui.wrapped("Your group isn't on the Group Ironman hiscores.", ColorScheme.MEDIUM_GRAY_COLOR));
				break;
			case FAILED:
				card.add(Ui.wrapped("Couldn't reach the hiscores. It'll try again in a few minutes.", ColorScheme.MEDIUM_GRAY_COLOR));
				break;
			default:
				addStanding(card, header, standing);
		}
		Ui.alignChildrenLeft(card);
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	private void addStanding(JPanel card, JPanel header, GroupHiscores.Standing standing)
	{
		GroupHiscores.Group group = standing.getGroup();
		GroupHiscores.Row above = standing.getAbove();
		String board = standing.getBoard().getTitle();
		JLabel rank = Ui.title("#" + QuantityFormatter.formatNumber(standing.getRow().getRank()));
		rank.setToolTipText("Your rank on the " + board + " hiscores for groups of " + group.getSize());
		header.add(rank, BorderLayout.EAST);
		card.add(Ui.muted(board + ", groups of " + group.getSize()));
		card.add(Box.createVerticalStrut(6));
		if (above == null)
		{
			card.add(Ui.wrapped("You're top of the board.", Color.WHITE));
			return;
		}
		String name = Clean.text(above.getName(), MAX_NAME);
		card.add(Ui.wrapped("Next up is <b>" + Ui.escapeHtml(name) + "</b> at #" + QuantityFormatter.formatNumber(above.getRank()) + ". "
			+ GroupHiscores.toPass(group, above), Color.WHITE));
		card.add(Box.createVerticalStrut(6));

		List<Line> lines = groupLines(group, above, standing.getAboveGroup());
		int[] score = score(lines);
		if (!MemberCard.addFoldRow(card, "You and " + name, score[0] + " – " + score[1], GROUP_ID, context))
		{
			return;
		}
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 1));
		rows.setOpaque(false);
		rows.add(row(new Line(Ui.small("", Color.WHITE), "You", "Them", 0), ColorScheme.MEDIUM_GRAY_COLOR));
		for (Line line : lines)
		{
			rows.add(row(line));
		}
		MemberCard.addContent(card, rows);
	}

	/**
	 * Our group against the group above: total level and XP, then each skill when we know theirs.
	 */
	private List<Line> groupLines(GroupHiscores.Group us, GroupHiscores.Row above, GroupHiscores.Group them)
	{
		List<Line> out = new ArrayList<>();
		out.add(new Line(Ui.small("Total level", ColorScheme.LIGHT_GRAY_COLOR), text(us.getTotalLevel()), text(above.getLevel()),
			compare(us.getTotalLevel(), above.getLevel())));
		out.add(new Line(Ui.small("Contributed XP", ColorScheme.LIGHT_GRAY_COLOR), shortNumber(us.getTotalXp()), shortNumber(above.getXp()),
			xpTip(us.getTotalXp()), xpTip(above.getXp()), Long.compare(us.getTotalXp(), above.getXp())));
		if (them == null)
		{
			return out;
		}
		for (Map.Entry<String, Integer> entry : us.getLevels().entrySet())
		{
			String skill = entry.getKey();
			Integer theirs = them.getLevels().get(skill);
			JLabel label = Ui.small(skill, ColorScheme.LIGHT_GRAY_COLOR);
			Skill icon = skillNamed(skill);
			if (icon != null)
			{
				label.setIcon(new ImageIcon(context.skillIconManager.getSkillImage(icon, true)));
				label.setIconTextGap(4);
			}
			out.add(new Line(label, text(entry.getValue()), text(theirs), xpTip(us.getXp().get(skill)), xpTip(them.getXp().get(skill)),
				compare(entry.getValue(), theirs)));
		}
		return out;
	}

	private static Skill skillNamed(String name)
	{
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(name))
			{
				return skill;
			}
		}
		return null;
	}

	private static String xpTip(Long xp)
	{
		return xp == null ? NONE : QuantityFormatter.formatNumber(xp) + " XP";
	}

	/**
	 * @return a number short enough for its column, such as "84,120", "312K", "8.1M" or "1.6B"
	 */
	static String shortNumber(long number)
	{
		if (number < 100_000)
		{
			return QuantityFormatter.formatNumber(number);
		}
		if (number < 1_000_000)
		{
			return number / 1_000 + "K";
		}
		if (number < 1_000_000_000)
		{
			return String.format(Locale.ROOT, "%.1fM", Math.floor(number / 100_000.0) / 10);
		}
		return String.format(Locale.ROOT, "%.1fB", Math.floor(number / 100_000_000.0) / 10);
	}

	/**
	 * @return everyone in the group with shared details, by member id, us first and then by name
	 */
	private Map<String, String> members(GroupView view)
	{
		String self = me.get();
		Map<String, String> names = new LinkedHashMap<>();
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			String name = profile != null && plugin.belongsToGroup(profile) ? Clean.text(profile.getName(), MAX_NAME) : null;
			if (name != null)
			{
				names.put(id, name);
			}
		}
		List<String> ids = new ArrayList<>(names.keySet());
		ids.sort(Comparator.comparing((String id) -> !id.equals(self)).thenComparing(id -> names.get(id).toLowerCase()));
		Map<String, String> out = new LinkedHashMap<>();
		ids.forEach(id -> out.put(id, names.get(id)));
		return out;
	}

	private JPanel pickers(Map<String, String> members)
	{
		JPanel pickers = new JPanel(new GridLayout(1, 2, 6, 0));
		pickers.setOpaque(false);
		pickers.setBorder(new EmptyBorder(0, 0, 6, 0));
		pickers.add(picker(members, left, id -> left = id));
		pickers.add(picker(members, right, id -> right = id));
		return pickers;
	}

	private JComboBox<String> picker(Map<String, String> members, String chosen, Consumer<String> choose)
	{
		List<String> ids = new ArrayList<>(members.keySet());
		JComboBox<String> box = new JComboBox<>(members.values().toArray(new String[0]));
		box.setSelectedIndex(Math.max(0, ids.indexOf(chosen)));
		box.setFocusable(false);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setToolTipText("Choose who to compare");
		box.addActionListener(e ->
		{
			int index = box.getSelectedIndex();
			if (index >= 0)
			{
				choose.accept(ids.get(index));
				refresh();
			}
		});
		return box;
	}

	/**
	 * Who's ahead on more of the rows below, and by how many.
	 */
	private static JPanel winner(String leftName, String rightName, List<Line> lines)
	{
		int[] score = score(lines);
		JPanel winner = new JPanel(new BorderLayout(6, 0));
		winner.setOpaque(false);
		winner.setBorder(new EmptyBorder(0, 0, 8, 0));
		String title = score[0] == score[1] ? "It's a draw" : (score[0] > score[1] ? leftName : rightName) + " wins";
		winner.add(Ui.title(title), BorderLayout.CENTER);
		winner.add(Ui.small(score[0] + " – " + score[1], score[0] == score[1] ? Color.WHITE : AHEAD), BorderLayout.EAST);
		winner.add(Ui.muted("Ahead on more of the rows below"), BorderLayout.SOUTH);
		return winner;
	}

	/**
	 * @return how many rows each member is ahead on, the first member's count first
	 */
	static int[] score(List<Line> lines)
	{
		int[] score = new int[2];
		for (Line line : lines)
		{
			if (line.compare > 0)
			{
				score[0]++;
			}
			else if (line.compare < 0)
			{
				score[1]++;
			}
		}
		return score;
	}

	/**
	 * A folding section with its own score beside its name.
	 */
	private void addSection(JPanel card, String title, List<Line> lines, String id)
	{
		if (lines.isEmpty())
		{
			return;
		}
		int[] score = score(lines);
		if (!MemberCard.addFoldRow(card, title, score[0] + " – " + score[1], id, context))
		{
			return;
		}
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 1));
		rows.setOpaque(false);
		for (Line line : lines)
		{
			rows.add(row(line));
		}
		MemberCard.addContent(card, rows);
	}

	private List<Line> overview(GroupView view)
	{
		SkillsData a = view.get(left, Section.SKILLS, SkillsData.class);
		SkillsData b = view.get(right, Section.SKILLS, SkillsData.class);
		QuestsData qa = view.get(left, Section.QUESTS, QuestsData.class);
		QuestsData qb = view.get(right, Section.QUESTS, QuestsData.class);
		DiariesData da = view.get(left, Section.DIARIES, DiariesData.class);
		DiariesData db = view.get(right, Section.DIARIES, DiariesData.class);
		CombatAchievementsData ca = view.get(left, Section.COMBAT_ACHIEVEMENTS, CombatAchievementsData.class);
		CombatAchievementsData cb = view.get(right, Section.COMBAT_ACHIEVEMENTS, CombatAchievementsData.class);
		CollectionLogData la = view.get(left, Section.COLLECTION_LOG, CollectionLogData.class);
		CollectionLogData lb = view.get(right, Section.COLLECTION_LOG, CollectionLogData.class);

		List<Line> out = new ArrayList<>();
		out.add(numbers("Combat level", a == null ? null : combatLevel(a), b == null ? null : combatLevel(b)));
		out.add(numbers("Total level", a == null ? null : a.getTotalLevel(), b == null ? null : b.getTotalLevel()));
		out.add(numbers("Quests", qa == null ? null : qa.count(QuestsData.FINISHED), qb == null ? null : qb.count(QuestsData.FINISHED)));
		// older versions of the plugin don't share quest points
		out.add(numbers("Quest points", qa == null ? null : positive(qa.getPoints()), qb == null ? null : positive(qb.getPoints())));
		out.add(numbers("Diaries", da == null ? null : da.countDone(), db == null ? null : db.countDone()));
		out.add(numbers("CA points", ca == null ? null : ca.getPoints(), cb == null ? null : cb.getPoints()));
		out.add(numbers("Log slots", la == null ? null : la.getObtained(), lb == null ? null : lb.getObtained()));
		return out;
	}

	private List<Line> skills(GroupView view)
	{
		SkillsData a = view.get(left, Section.SKILLS, SkillsData.class);
		SkillsData b = view.get(right, Section.SKILLS, SkillsData.class);
		List<Line> out = new ArrayList<>();
		if (a == null && b == null)
		{
			return out;
		}
		for (Skill skill : MemberCard.SKILL_ORDER)
		{
			JLabel name = Ui.small(skill.getName(), ColorScheme.LIGHT_GRAY_COLOR);
			name.setIcon(new ImageIcon(context.skillIconManager.getSkillImage(skill, true)));
			name.setIconTextGap(4);
			Integer levelA = a == null ? null : a.getLevel(skill);
			Integer levelB = b == null ? null : b.getLevel(skill);
			out.add(new Line(name, text(levelA), text(levelB), compare(levelA, levelB)));
		}
		return out;
	}

	private List<Line> kills(GroupView view)
	{
		KillCountsData a = view.get(left, Section.KILL_COUNTS, KillCountsData.class);
		KillCountsData b = view.get(right, Section.KILL_COUNTS, KillCountsData.class);
		TreeSet<String> bosses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		addNames(bosses, a == null ? null : a.getCounts());
		addNames(bosses, b == null ? null : b.getCounts());
		List<String> rows = merged(bosses);
		rows.removeIf(boss -> count(a, boss) <= 0 && count(b, boss) <= 0);
		rows.sort(Comparator.comparing((String boss) -> -Math.max(count(a, boss), count(b, boss))));
		List<Line> out = new ArrayList<>();
		for (String boss : rows)
		{
			out.add(numbers(boss, positive(count(a, boss)), positive(count(b, boss))));
		}
		return out;
	}

	private List<Line> bestTimes(GroupView view)
	{
		KillCountsData a = view.get(left, Section.KILL_COUNTS, KillCountsData.class);
		KillCountsData b = view.get(right, Section.KILL_COUNTS, KillCountsData.class);
		TreeSet<String> bosses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		addNames(bosses, a == null ? null : a.getBestTimes());
		addNames(bosses, b == null ? null : b.getBestTimes());
		List<Line> out = new ArrayList<>();
		for (String boss : merged(bosses))
		{
			Double timeA = a == null ? null : a.bestTimeFor(boss);
			Double timeB = b == null ? null : b.bestTimeFor(boss);
			// a faster time is better, so the lower one wins
			int compare = timeA == null || timeB == null ? 0 : -Double.compare(timeA, timeB);
			out.add(new Line(Ui.small(boss, ColorScheme.LIGHT_GRAY_COLOR), timeA == null ? NONE : KillCountsData.formatTime(timeA),
				timeB == null ? NONE : KillCountsData.formatTime(timeB), compare));
		}
		return out;
	}

	private static void addNames(TreeSet<String> into, Map<String, ?> names)
	{
		if (names != null)
		{
			into.addAll(names.keySet());
		}
	}

	/**
	 * @return the bosses, with names that only differ in how they're written ("Gauntlet" and "The Gauntlet") as one
	 */
	private static List<String> merged(TreeSet<String> bosses)
	{
		Map<String, String> byKey = new LinkedHashMap<>();
		for (String boss : bosses)
		{
			byKey.putIfAbsent(KillCountsData.bossKey(boss), boss);
		}
		return new ArrayList<>(byKey.values());
	}

	private static int count(KillCountsData kills, String boss)
	{
		Integer count = kills == null ? null : kills.countFor(boss);
		return count == null ? 0 : count;
	}

	private static Integer positive(Integer count)
	{
		return count == null || count <= 0 ? null : count;
	}

	private static int combatLevel(SkillsData skills)
	{
		return Experience.getCombatLevel(skills.getLevel(Skill.ATTACK), skills.getLevel(Skill.STRENGTH),
			skills.getLevel(Skill.DEFENCE), skills.getLevel(Skill.HITPOINTS), skills.getLevel(Skill.MAGIC),
			skills.getLevel(Skill.RANGED), skills.getLevel(Skill.PRAYER));
	}

	private static Line numbers(String label, Integer a, Integer b)
	{
		return new Line(Ui.small(label, ColorScheme.LIGHT_GRAY_COLOR), text(a), text(b), compare(a, b));
	}

	private static String text(Integer number)
	{
		return number == null ? NONE : QuantityFormatter.formatNumber(number);
	}

	/**
	 * @return above 0 if the first is ahead, below 0 if the second is, and 0 for a tie or if either is missing
	 */
	static int compare(Integer a, Integer b)
	{
		return a == null || b == null ? 0 : Integer.compare(a, b);
	}

	/**
	 * A row with a label and each member's value in a column of its own, the one ahead in green.
	 */
	private static JPanel row(Line line)
	{
		return row(line, Color.WHITE);
	}

	private static JPanel row(Line line, Color color)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(line.label, BorderLayout.CENTER);
		JPanel values = new JPanel(new GridLayout(1, 2, 4, 0));
		values.setOpaque(false);
		values.add(value(line.a, line.tipA, line.compare > 0 ? AHEAD : color));
		values.add(value(line.b, line.tipB, line.compare < 0 ? AHEAD : color));
		row.add(values, BorderLayout.EAST);
		return row;
	}

	private static JLabel value(String text, String tip, Color color)
	{
		JLabel label = Ui.small(text, color);
		label.setHorizontalAlignment(JLabel.RIGHT);
		label.setPreferredSize(new Dimension(VALUE_WIDTH, label.getPreferredSize().height));
		label.setToolTipText(tip);
		return label;
	}
}
