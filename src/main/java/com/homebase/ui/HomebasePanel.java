package com.homebase.ui;

import com.homebase.HomebasePlugin;
import com.homebase.bounties.BountyService;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Clean;
import com.homebase.data.GroupView;
import com.homebase.data.ItemsData;
import com.homebase.data.ProfileData;
import com.homebase.data.Section;
import com.homebase.goals.GoalService;
import com.homebase.party.GroupSync;
import com.homebase.requests.RequestService;
import com.homebase.updates.UpdateTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

@Singleton
public class HomebasePanel extends PluginPanel
{
	private final HomebasePlugin plugin;
	private final GroupSync groupSync;
	private final PartyService partyService;
	private final LocalCollector collector;
	private final ItemManager itemManager;
	private final ClientThread clientThread;

	private enum Tab
	{
		MEMBERS("Members"),
		UPDATES("Updates"),
		REQUESTS("Requests"),
		GOALS("Goals");

		final String label;

		Tab(String label)
		{
			this.label = label;
		}
	}

	private final JLabel groupLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JButton actionButton = new JButton();
	private final JPanel actionRow = new JPanel(new BorderLayout());
	private final Map<Tab, JLabel> tabLabels = new EnumMap<>(Tab.class);
	private final JPanel tabs = new JPanel(new GridLayout(1, 0, 0, 0));
	private final JPanel list = new JPanel();
	private Tab tab = Tab.MEMBERS;
	private final RequestsView requestsView;
	private final UpdatesView updatesView;
	private final GoalsView goalsView;
	private final MemberCard.Context cardContext;
	private final Map<String, MemberCard> cards = new HashMap<>();
	private final Set<String> expanded = new HashSet<>();
	private final Set<String> openSections = new HashSet<>();
	private final Timer rebuildTimer = new Timer(500, e -> rebuild());
	private final Timer liveTimer = new Timer(600, e -> refreshLive());

	private GroupView view;
	private boolean storageExpanded;
	private Runnable action;

	@Inject
	private HomebasePanel(HomebasePlugin plugin, GroupSync groupSync, PartyService partyService, LocalCollector collector,
		RequestService requestService, UpdateTracker updateTracker, GoalService goalService, BountyService bountyService,
		ItemManager itemManager, SkillIconManager skillIconManager, ClientThread clientThread)
	{
		this.plugin = plugin;
		this.groupSync = groupSync;
		this.partyService = partyService;
		this.collector = collector;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.requestsView = new RequestsView(requestService);
		this.updatesView = new UpdatesView(updateTracker);
		this.goalsView = new GoalsView(goalService, bountyService, itemManager, clientThread,
			() -> view == null ? null : view.get(null, Section.GROUP_STORAGE, ItemsData.class));
		this.cardContext = new MemberCard.Context(itemManager, skillIconManager, clientThread, openSections, id ->
		{
			if (!openSections.remove(id))
			{
				openSections.add(id);
			}
			rebuild();
		});

		rebuildTimer.setRepeats(false);
		liveTimer.setRepeats(false);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Group Homebase");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setIcon(new ImageIcon(ImageUtil.loadImageResource(HomebasePlugin.class, "icon.png")));
		title.setIconTextGap(6);

		groupLabel.setFont(FontManager.getRunescapeBoldFont());
		groupLabel.setForeground(ColorScheme.BRAND_ORANGE);
		groupLabel.setToolTipText("Your Group Ironman group");
		groupLabel.setBorder(new EmptyBorder(2, 22, 0, 0));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setIconTextGap(6);
		statusLabel.setVerticalTextPosition(SwingConstants.TOP);
		statusLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

		Ui.primary(actionButton);
		actionButton.addActionListener(e ->
		{
			if (action != null)
			{
				action.run();
			}
		});

		for (Tab t : Tab.values())
		{
			JLabel label = new JLabel(t.label, SwingConstants.CENTER);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setOpaque(true);
			label.setHorizontalTextPosition(SwingConstants.LEFT);
			label.setIconTextGap(3);
			Ui.clickable(label, () ->
			{
				tab = t;
				rebuild();
			});
			tabLabels.put(t, label);
			tabs.add(label);
		}
		tabs.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel titles = new JPanel();
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
		titles.setOpaque(false);
		titles.add(title);
		titles.add(groupLabel);
		titles.add(statusLabel);

		actionRow.setOpaque(false);
		actionRow.setBorder(new EmptyBorder(8, 0, 0, 0));
		actionRow.add(actionButton, BorderLayout.CENTER);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(titles, BorderLayout.NORTH);
		header.add(actionRow, BorderLayout.CENTER);
		header.add(tabs, BorderLayout.SOUTH);
		header.setBorder(new EmptyBorder(0, 0, 8, 0));
		actionRow.setVisible(false);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setOpaque(false);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(header, BorderLayout.NORTH);
		north.add(list, BorderLayout.CENTER);
		add(north, BorderLayout.NORTH);

		rebuild();
	}

	/**
	 * Show a group, or null when no passphrase is set. Safe to call from any thread.
	 */
	public void setView(GroupView view)
	{
		SwingUtilities.invokeLater(() ->
		{
			this.view = view;
			expanded.clear();
			openSections.clear();
			storageExpanded = false;
			rebuild();
		});
	}

	/**
	 * Group data changed. Rebuilds are batched, so this is cheap to call often from any thread.
	 */
	public void scheduleRebuild()
	{
		SwingUtilities.invokeLater(rebuildTimer::restart);
	}

	/**
	 * Party state or live stats changed. Safe to call from any thread.
	 */
	public void scheduleLiveRefresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (!liveTimer.isRunning())
			{
				liveTimer.start();
			}
		});
	}

	private void rebuild()
	{
		list.removeAll();
		cards.clear();
		tabs.setVisible(view != null);

		if (view != null)
		{
			styleTabs();
			switch (tab)
			{
				case REQUESTS:
					requestsView.refresh();
					requestsView.setAlignmentX(Component.LEFT_ALIGNMENT);
					list.add(requestsView);
					break;
				case UPDATES:
					updatesView.refresh();
					updatesView.setAlignmentX(Component.LEFT_ALIGNMENT);
					list.add(updatesView);
					break;
				case GOALS:
					goalsView.refresh();
					goalsView.setAlignmentX(Component.LEFT_ALIGNMENT);
					list.add(goalsView);
					break;
				default:
					buildMembers();
			}
		}

		refreshLive();
		list.revalidate();
		list.repaint();
	}

	/**
	 * The chosen tab is white with an orange line under it, like RuneLite's own tabs. Open requests
	 * put an orange dot on the Requests tab, since there's no room for a number.
	 */
	private void styleTabs()
	{
		int open = requestsView.openCount();
		for (Map.Entry<Tab, JLabel> e : tabLabels.entrySet())
		{
			boolean selected = e.getKey() == tab;
			JLabel label = e.getValue();
			label.setForeground(selected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			label.setBackground(selected ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			label.setBorder(new CompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR),
				new EmptyBorder(6, 0, 4, 0)));
		}
		JLabel requests = tabLabels.get(Tab.REQUESTS);
		requests.setIcon(open > 0 ? new Ui.Dot(ColorScheme.BRAND_ORANGE) : null);
		requests.setToolTipText(open == 0 ? null : open == 1 ? "1 open request" : open + " open requests");
	}

	private void buildMembers()
	{
		addGroupStorage();

		String localId = collector.getMemberId();
		List<MemberCard> members = new ArrayList<>();
		for (String id : view.memberIds())
		{
			ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
			if (profile == null || !plugin.belongsToGroup(profile))
			{
				// an account that isn't in this Group Ironman group
				continue;
			}
			MemberCard card = new MemberCard(id, view, expanded.contains(id), () -> toggle(id), cardContext);
			cards.put(id, card);
			members.add(card);
		}
		// you first, then everyone else by name
		MemberCard localCard = localId == null ? null : cards.get(localId);
		members.sort(Comparator
			.comparingInt((MemberCard c) -> c == localCard ? 0 : 1)
			.thenComparing(c -> c.getMemberName() == null ? "" : c.getMemberName(), String.CASE_INSENSITIVE_ORDER));

		if (members.isEmpty())
		{
			list.add(Ui.empty("No group data yet", "Log in and your details will be shared with your group."));
		}
		for (MemberCard card : members)
		{
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(card);
			list.add(Box.createVerticalStrut(Ui.GAP));
		}
	}

	private void addGroupStorage()
	{
		ItemsData storage = view.get(null, Section.GROUP_STORAGE, ItemsData.class);
		if (storage == null)
		{
			return;
		}

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		int stacks = 0;
		for (net.runelite.api.Item item : storage.toItems())
		{
			if (item != null)
			{
				stacks++;
			}
		}
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setBorder(new EmptyBorder(7, 8, 7, 8));
		Ui.hover(top, ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARKER_GRAY_HOVER_COLOR);
		Ui.clickable(top, () ->
		{
			storageExpanded = !storageExpanded;
			rebuild();
		});

		JLabel title = Ui.title("Group storage");
		title.setIcon(new Ui.Chevron(storageExpanded));
		title.setIconTextGap(6);
		top.add(title, BorderLayout.CENTER);
		top.add(Ui.small(stacks + " items", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);

		String author = view.authorOf(null, Section.GROUP_STORAGE);
		ProfileData seenBy = author == null ? null : view.get(author, Section.PROFILE, ProfileData.class);
		JLabel seen = Ui.muted("Seen " + Ago.format(view.updatedAt(null, Section.GROUP_STORAGE))
			+ (seenBy == null ? "" : " by " + Clean.text(seenBy.getName(), 20)));
		seen.setBorder(new EmptyBorder(2, 14, 0, 0));
		top.add(seen, BorderLayout.SOUTH);
		panel.add(top, BorderLayout.NORTH);

		if (storageExpanded)
		{
			ItemGrid grid = new ItemGrid(storage.toItems(), itemManager, clientThread);
			grid.setBorder(new EmptyBorder(0, 8, 8, 8));
			panel.add(grid, BorderLayout.CENTER);
		}

		list.add(panel);
		list.add(Box.createVerticalStrut(Ui.GAP));
	}

	private void toggle(String memberId)
	{
		if (!expanded.remove(memberId))
		{
			expanded.add(memberId);
		}
		rebuild();
	}

	private void refreshLive()
	{
		updateStatus();
		updateGroupName();
		String localId = collector.getMemberId();
		boolean inGroupParty = groupSync.isGroupParty();
		for (Map.Entry<String, MemberCard> entry : cards.entrySet())
		{
			MemberCard card = entry.getValue();
			String name = card.getMemberName();
			PartyMember live = inGroupParty && name != null ? partyService.getMemberByDisplayName(name) : null;
			card.updateLive(live, entry.getKey().equals(localId));
		}
	}

	/**
	 * Show the Group Ironman group's name: yours if you're logged in, otherwise whichever name the
	 * group's members share.
	 */
	private void updateGroupName()
	{
		String name = plugin.getGimGroup();
		if (name == null && view != null)
		{
			String localId = collector.getMemberId();
			ProfileData local = localId == null ? null : view.get(localId, Section.PROFILE, ProfileData.class);
			if (local != null && local.getGroupName() != null)
			{
				name = local.getGroupName();
			}
			else
			{
				Map<String, Integer> counts = new HashMap<>();
				for (String id : view.memberIds())
				{
					ProfileData profile = view.get(id, Section.PROFILE, ProfileData.class);
					if (profile != null && profile.getGroupName() != null)
					{
						counts.merge(profile.getGroupName(), 1, Integer::sum);
					}
				}
				int best = 0;
				for (Map.Entry<String, Integer> e : counts.entrySet())
				{
					if (e.getValue() > best)
					{
						best = e.getValue();
						name = e.getKey();
					}
				}
			}
		}
		name = Clean.text(name, 20);
		groupLabel.setText(name == null ? "" : name);
		groupLabel.setVisible(name != null);
	}

	private void updateStatus()
	{
		switch (groupSync.getStatus())
		{
			case NO_GROUP:
				setStatus(ColorScheme.MEDIUM_GRAY_COLOR, "Log in on your Group Ironman account and you'll join your group automatically. "
					+ "For other groups, create a passphrase or paste one into this plugin's settings.", "Create group", plugin::createGroup);
				break;
			case DISCONNECTED:
				setStatus(ColorScheme.PROGRESS_ERROR_COLOR, "Not connected to your group", "Join group", groupSync::joinGroupParty);
				break;
			case OTHER_PARTY:
				setStatus(Ui.WARNING, "You're in another party, so syncing is paused", "Switch to group", groupSync::joinGroupParty);
				break;
			case CONNECTED:
				boolean gim = plugin.getGimGroup() != null;
				int online = onlineCount();
				// a Group Ironman group needs no passphrase, so there's nothing to copy
				setStatus(Ui.ONLINE, "Connected  ·  " + online + " online", gim ? null : "Copy passphrase", gim ? null : plugin::copyPassphrase);
				break;
		}
	}

	private void setStatus(Color dot, String text, String buttonText, Runnable buttonAction)
	{
		statusLabel.setIcon(new Ui.Dot(dot));
		statusLabel.setText("<html><body style='width:" + (Ui.TEXT_WIDTH - 15) + "px'>" + text + "</body></html>");
		actionButton.setText(buttonText);
		actionRow.setVisible(buttonText != null);
		action = buttonAction;
	}

	private int onlineCount()
	{
		int count = 0;
		for (PartyMember member : partyService.getMembers())
		{
			if (member.isLoggedIn())
			{
				count++;
			}
		}
		return count;
	}

}
