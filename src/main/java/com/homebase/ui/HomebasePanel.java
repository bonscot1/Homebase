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
import com.homebase.party.PartyMembers;
import com.homebase.party.Presence;
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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * The sidebar panel: the group's name and connection status at the top, then a tab each for members,
 * updates, requests and goals.
 */
@Slf4j
@Singleton
public class HomebasePanel extends PluginPanel
{
	/**
	 * How long a rebuild waits, so a burst of changes is drawn once.
	 */
	private static final int REBUILD_DELAY_MS = 500;

	/**
	 * One game tick, which is as often as live stats can change.
	 */
	private static final int LIVE_REFRESH_MS = 600;

	/**
	 * Longest player or group name shown, and longest list of strangers in the party.
	 */
	private static final int MAX_NAME = 20;
	private static final int MAX_STRANGERS = 60;

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

	private final HomebasePlugin plugin;
	private final GroupSync groupSync;
	private final PartyService partyService;
	private final Presence presence;
	private final LocalCollector collector;
	private final ItemManager itemManager;
	private final ClientThread clientThread;

	private final JLabel groupLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JButton mainButton = Ui.primaryButton("", () -> runAction(true));
	private final JButton secondButton = Ui.button("", () -> runAction(false));
	private final JPanel buttonRow = new JPanel(new GridLayout(1, 0, 6, 0));
	private final Map<Tab, JLabel> tabLabels = new EnumMap<>(Tab.class);
	private final JPanel tabBar = new JPanel(new GridLayout(1, 0, 0, 0));
	private final JPanel tabContent = new JPanel();
	private final RequestsView requestsView;
	private final UpdatesView updatesView;
	private final GoalsView goalsView;
	private final MemberCard.Context cardContext;
	private final Map<String, MemberCard> cards = new HashMap<>();
	private final Set<String> expandedMembers = new HashSet<>();
	private final Set<String> openSections = new HashSet<>();
	private final Timer rebuildTimer = new Timer(REBUILD_DELAY_MS, e -> rebuild());
	private final Timer liveTimer = new Timer(LIVE_REFRESH_MS, e -> refreshLive());

	private Tab selectedTab = Tab.MEMBERS;
	private GroupView view;
	private boolean storageExpanded;
	private boolean rebuildWhenShown;
	private Runnable mainAction;
	private Runnable secondAction;

	@Inject
	private HomebasePanel(HomebasePlugin plugin, GroupSync groupSync, PartyService partyService, Presence presence, LocalCollector collector,
		RequestService requestService, UpdateTracker updateTracker, GoalService goalService, BountyService bountyService,
		ItemManager itemManager, SkillIconManager skillIconManager, ClientThread clientThread)
	{
		this.plugin = plugin;
		this.groupSync = groupSync;
		this.partyService = partyService;
		this.presence = presence;
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
		// lines up with the title's text, past its icon
		groupLabel.setBorder(new EmptyBorder(2, 22, 0, 0));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setIconTextGap(6);
		statusLabel.setVerticalTextPosition(SwingConstants.TOP);
		statusLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

		for (Tab tab : Tab.values())
		{
			JLabel label = new JLabel(tab.label, SwingConstants.CENTER);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setOpaque(true);
			label.setHorizontalTextPosition(SwingConstants.LEFT);
			label.setIconTextGap(3);
			Ui.clickable(label, () ->
			{
				selectedTab = tab;
				rebuild();
			});
			tabLabels.put(tab, label);
			tabBar.add(label);
		}
		tabBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel titles = new JPanel();
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
		titles.setOpaque(false);
		titles.add(title);
		titles.add(groupLabel);
		titles.add(statusLabel);

		buttonRow.setOpaque(false);
		buttonRow.setBorder(new EmptyBorder(8, 0, 0, 0));
		buttonRow.add(mainButton);
		buttonRow.add(secondButton);
		buttonRow.setVisible(false);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(titles, BorderLayout.NORTH);
		header.add(buttonRow, BorderLayout.CENTER);
		header.add(tabBar, BorderLayout.SOUTH);
		header.setBorder(new EmptyBorder(0, 0, 8, 0));

		tabContent.setLayout(new BoxLayout(tabContent, BoxLayout.Y_AXIS));
		tabContent.setOpaque(false);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(header, BorderLayout.NORTH);
		north.add(tabContent, BorderLayout.CENTER);
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
			expandedMembers.clear();
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

	@Override
	public void onActivate()
	{
		if (rebuildWhenShown)
		{
			rebuild();
		}
	}

	private void rebuild()
	{
		if (!isShowing())
		{
			// it's rebuilt when it's next opened
			rebuildWhenShown = true;
			return;
		}
		rebuildWhenShown = false;
		cards.clear();
		tabBar.setVisible(view != null);

		if (view == null)
		{
			tabContent.removeAll();
		}
		else
		{
			styleTabs();
			switch (selectedTab)
			{
				case REQUESTS:
					requestsView.refresh();
					showTabView(requestsView);
					break;
				case UPDATES:
					updatesView.refresh();
					showTabView(updatesView);
					break;
				case GOALS:
					goalsView.refresh();
					showTabView(goalsView);
					break;
				default:
					tabContent.removeAll();
					buildMembers();
			}
		}

		refreshLive();
		tabContent.revalidate();
		tabContent.repaint();
	}

	/**
	 * Show a tab's view, leaving it where it is if it's already showing. Taking it out and putting it
	 * back would take the keyboard focus from whatever's being typed into its form.
	 */
	private void showTabView(JComponent tabView)
	{
		if (tabContent.getComponentCount() == 1 && tabContent.getComponent(0) == tabView)
		{
			return;
		}
		tabContent.removeAll();
		tabView.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabContent.add(tabView);
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
			boolean selected = e.getKey() == selectedTab;
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
			MemberCard card;
			try
			{
				card = new MemberCard(id, view, expandedMembers.contains(id), () -> toggleMember(id), cardContext);
			}
			catch (RuntimeException e)
			{
				log.debug("Skipping a member whose data can't be shown", e);
				continue;
			}
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
			tabContent.add(Ui.empty("No group data yet", "Log in and your details will be shared with your group."));
		}
		for (MemberCard card : members)
		{
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			tabContent.add(card);
			tabContent.add(Box.createVerticalStrut(Ui.GAP));
		}
	}

	private void addGroupStorage()
	{
		ItemsData storage = view.get(null, Section.GROUP_STORAGE, ItemsData.class);
		if (storage == null)
		{
			return;
		}
		List<Item> slots = storage.toItems();

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(7, 8, 7, 8));
		Ui.hover(header, ColorScheme.DARKER_GRAY_COLOR, ColorScheme.DARKER_GRAY_HOVER_COLOR);
		Ui.clickable(header, () ->
		{
			storageExpanded = !storageExpanded;
			rebuild();
		});

		JLabel title = Ui.title("Group storage");
		title.setIcon(new Ui.Chevron(storageExpanded));
		title.setIconTextGap(6);
		header.add(title, BorderLayout.CENTER);
		header.add(Ui.small(ItemGrid.countFilled(slots) + " items", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);

		String author = view.authorOf(null, Section.GROUP_STORAGE);
		ProfileData seenBy = author == null ? null : view.get(author, Section.PROFILE, ProfileData.class);
		JLabel seen = Ui.muted("Seen " + Ago.format(view.updatedAt(null, Section.GROUP_STORAGE))
			+ (seenBy == null ? "" : " by " + Clean.text(seenBy.getName(), MAX_NAME)));
		// lines up with the title's text, past the arrow
		seen.setBorder(new EmptyBorder(2, 14, 0, 0));
		header.add(seen, BorderLayout.SOUTH);
		panel.add(header, BorderLayout.NORTH);

		if (storageExpanded)
		{
			ItemGrid grid = new ItemGrid(slots, itemManager, clientThread);
			grid.setBorder(new EmptyBorder(0, 8, 8, 8));
			panel.add(grid, BorderLayout.CENTER);
		}

		tabContent.add(panel);
		tabContent.add(Box.createVerticalStrut(Ui.GAP));
	}

	private void toggleMember(String memberId)
	{
		if (!expandedMembers.remove(memberId))
		{
			expandedMembers.add(memberId);
		}
		rebuild();
	}

	private void refreshLive()
	{
		String gimGroup = Clean.text(plugin.getGimGroup(), MAX_NAME);
		updateStatus(gimGroup);
		updateGroupName(gimGroup);
		String localId = collector.getMemberId();
		boolean inGroupParty = groupSync.isGroupParty();
		for (Map.Entry<String, MemberCard> entry : cards.entrySet())
		{
			MemberCard card = entry.getValue();
			String name = card.getMemberName();
			PartyMember live = inGroupParty && name != null ? PartyMembers.byName(partyService, name) : null;
			if (!presence.isOnline(live))
			{
				// gone quiet or said they were leaving, even if the party still lists them
				live = null;
			}
			card.updateLive(live, entry.getKey().equals(localId));
		}
	}

	/**
	 * Show the Group Ironman group's name while that's the group being synced. A passphrase group has
	 * no name, even when its members are Group Ironmen.
	 */
	private void updateGroupName(String gimGroup)
	{
		groupLabel.setText(gimGroup == null ? "" : gimGroup);
		groupLabel.setVisible(gimGroup != null);
	}

	private void updateStatus(String gimGroup)
	{
		boolean isGim = gimGroup != null;
		switch (groupSync.getStatus())
		{
			case NO_GROUP:
				setStatus(ColorScheme.MEDIUM_GRAY_COLOR, "Log in on your Group Ironman account to set up your group. "
					+ "For other groups, create a passphrase or paste one into this plugin's settings.", "Create group", plugin::createGroup, null, null);
				break;
			case LOG_IN:
				setStatus(ColorScheme.MEDIUM_GRAY_COLOR, "Log in on an account in " + gimGroup + " to connect", null, null, null, null);
				break;
			case NOT_IN_GROUP:
				setStatus(ColorScheme.MEDIUM_GRAY_COLOR, "This account isn't in " + gimGroup + ", so it can't connect", null, null, null, null);
				break;
			case DISCONNECTED:
				// a passphrase group may have just been made, so its passphrase can be copied straight away
				setStatus(ColorScheme.PROGRESS_ERROR_COLOR, "Not connected to your group", "Connect", groupSync::joinGroupParty,
					isGim ? null : "Copy passphrase", isGim ? null : plugin::copyPassphrase);
				break;
			case OTHER_PARTY:
				boolean canSwitch = groupSync.canConnect();
				setStatus(Ui.WARNING, "You're in another party, so syncing is paused", canSwitch ? "Switch to group" : null,
					canSwitch ? groupSync::joinGroupParty : null, null, null);
				break;
			case PAUSED:
				setStatus(Ui.WARNING, "Nothing is shared while " + Clean.text(String.join(", ", groupSync.strangers()), MAX_STRANGERS)
					+ " is in the party and not in " + gimGroup, null, null, "Disconnect", groupSync::leaveGroupParty);
				break;
			case CONNECTED:
				// a Group Ironman group needs no passphrase, so there's nothing to copy
				setStatus(Ui.ONLINE, "Connected  ·  " + onlineCount() + " online", isGim ? null : "Copy passphrase",
					isGim ? null : plugin::copyPassphrase, "Disconnect", groupSync::leaveGroupParty);
				break;
		}
	}

	/**
	 * Show the status, with up to two buttons under it: the main one in orange and another beside it.
	 * A button is hidden when its text is null.
	 */
	private void setStatus(Color dotColor, String text, String mainText, Runnable mainAction, String secondText, Runnable secondAction)
	{
		statusLabel.setIcon(new Ui.Dot(dotColor));
		// narrower than other wrapped text, to leave room for the dot
		statusLabel.setText("<html><body style='width:" + (Ui.TEXT_WIDTH - 15) + "px'>" + text + "</body></html>");
		mainButton.setText(mainText);
		mainButton.setVisible(mainText != null);
		this.mainAction = mainAction;
		secondButton.setText(secondText);
		secondButton.setVisible(secondText != null);
		this.secondAction = secondAction;
		buttonRow.setVisible(mainText != null || secondText != null);
	}

	private void runAction(boolean main)
	{
		Runnable action = main ? mainAction : secondAction;
		if (action != null)
		{
			action.run();
		}
	}

	private int onlineCount()
	{
		int count = 0;
		for (PartyMember member : PartyMembers.current(partyService))
		{
			if (presence.isOnline(member))
			{
				count++;
			}
		}
		return count;
	}

}
