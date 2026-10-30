package com.homebase.ui;

import com.homebase.HomebasePlugin;
import com.homebase.collect.LocalCollector;
import com.homebase.data.Clean;
import com.homebase.data.GroupView;
import com.homebase.data.ItemsData;
import com.homebase.data.ProfileData;
import com.homebase.data.Section;
import com.homebase.party.GroupSync;
import com.homebase.requests.RequestService;
import com.homebase.updates.UpdateTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

@Singleton
public class HomebasePanel extends PluginPanel
{
	private final HomebasePlugin plugin;
	private final GroupSync groupSync;
	private final PartyService partyService;
	private final LocalCollector collector;
	private final ItemManager itemManager;
	private final ClientThread clientThread;

	private final JLabel groupLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JButton actionButton = new JButton();
	private final JToggleButton membersTab = new JToggleButton("Members");
	private final JToggleButton updatesTab = new JToggleButton("Updates");
	private final JToggleButton requestsTab = new JToggleButton("Requests");
	private final JPanel tabs = new JPanel(new GridLayout(1, 3, 2, 0));
	private final JPanel list = new JPanel();
	private final RequestsView requestsView;
	private final UpdatesView updatesView;
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
		RequestService requestService, UpdateTracker updateTracker, ItemManager itemManager, SkillIconManager skillIconManager,
		ClientThread clientThread)
	{
		this.plugin = plugin;
		this.groupSync = groupSync;
		this.partyService = partyService;
		this.collector = collector;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.requestsView = new RequestsView(requestService);
		this.updatesView = new UpdatesView(updateTracker);
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

		groupLabel.setFont(FontManager.getRunescapeBoldFont());
		groupLabel.setForeground(ColorScheme.BRAND_ORANGE);
		groupLabel.setToolTipText("Your Group Ironman group");

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setBorder(new EmptyBorder(4, 0, 4, 0));

		actionButton.setFocusPainted(false);
		actionButton.addActionListener(e ->
		{
			if (action != null)
			{
				action.run();
			}
		});

		ButtonGroup tabGroup = new ButtonGroup();
		for (JToggleButton tab : new JToggleButton[]{membersTab, updatesTab, requestsTab})
		{
			tab.setFocusPainted(false);
			tab.setFont(FontManager.getRunescapeSmallFont());
			tab.setMargin(new Insets(2, 0, 2, 0));
			tab.addActionListener(e -> rebuild());
			tabGroup.add(tab);
			tabs.add(tab);
		}
		membersTab.setSelected(true);
		tabs.setOpaque(false);
		tabs.setBorder(new EmptyBorder(8, 0, 0, 0));

		JPanel titles = new JPanel(new GridLayout(0, 1));
		titles.setOpaque(false);
		titles.add(title);
		titles.add(groupLabel);

		JPanel controls = new JPanel(new BorderLayout());
		controls.setOpaque(false);
		controls.add(actionButton, BorderLayout.NORTH);
		controls.add(tabs, BorderLayout.SOUTH);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(titles, BorderLayout.NORTH);
		header.add(statusLabel, BorderLayout.CENTER);
		header.add(controls, BorderLayout.SOUTH);
		header.setBorder(new EmptyBorder(0, 0, 10, 0));

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
			int open = requestsView.openCount();
			// three tabs leave no room for a count, so open requests turn the tab orange instead
			requestsTab.setForeground(open > 0 ? ColorScheme.BRAND_ORANGE : membersTab.getForeground());
			requestsTab.setToolTipText(open == 1 ? "1 open request" : open + " open requests");
			if (requestsTab.isSelected())
			{
				requestsView.refresh();
				requestsView.setAlignmentX(Component.LEFT_ALIGNMENT);
				list.add(requestsView);
			}
			else if (updatesTab.isSelected())
			{
				updatesView.refresh();
				updatesView.setAlignmentX(Component.LEFT_ALIGNMENT);
				list.add(updatesView);
			}
			else
			{
				buildMembers();
			}
		}

		refreshLive();
		list.revalidate();
		list.repaint();
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
			list.add(hint("No group data yet. Log in and your details will be shared with your group."));
		}
		for (MemberCard card : members)
		{
			card.setAlignmentX(Component.LEFT_ALIGNMENT);
			list.add(card);
			list.add(Box.createVerticalStrut(6));
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
		panel.setBorder(new EmptyBorder(6, 6, 6, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel((storageExpanded ? "▾ " : "▸ ") + "Group storage");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		MemberCard.clickable(title, () ->
		{
			storageExpanded = !storageExpanded;
			rebuild();
		});

		String author = view.authorOf(null, Section.GROUP_STORAGE);
		ProfileData seenBy = author == null ? null : view.get(author, Section.PROFILE, ProfileData.class);
		JLabel seen = new JLabel("Seen " + Ago.format(view.updatedAt(null, Section.GROUP_STORAGE))
			+ (seenBy == null ? "" : " by " + Clean.text(seenBy.getName(), 20)));
		seen.setFont(FontManager.getRunescapeSmallFont());
		seen.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(title, BorderLayout.NORTH);
		top.add(seen, BorderLayout.CENTER);
		panel.add(top, BorderLayout.NORTH);

		if (storageExpanded)
		{
			ItemGrid grid = new ItemGrid(storage.toItems(), 5, false, itemManager, clientThread);
			grid.setBorder(new EmptyBorder(6, 0, 0, 0));
			panel.add(grid, BorderLayout.CENTER);
		}

		list.add(panel);
		list.add(Box.createVerticalStrut(6));
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
				setStatus("Log in on your Group Ironman account and you'll join your group automatically. "
					+ "For other groups, create a passphrase or paste one into this plugin's settings.", "Create group", plugin::createGroup);
				break;
			case DISCONNECTED:
				setStatus("Not connected to your group.", "Join group", groupSync::joinGroupParty);
				break;
			case OTHER_PARTY:
				setStatus("You're in another party, so syncing is paused.", "Switch to group", groupSync::joinGroupParty);
				break;
			case CONNECTED:
				boolean gim = plugin.getGimGroup() != null;
				// a Group Ironman group needs no passphrase, so there's nothing to copy
				setStatus("Connected · " + onlineCount() + " online", gim ? null : "Copy passphrase", gim ? null : plugin::copyPassphrase);
				break;
		}
	}

	private void setStatus(String text, String buttonText, Runnable buttonAction)
	{
		statusLabel.setText("<html><body style='width:180px'>" + text + "</body></html>");
		actionButton.setText(buttonText);
		actionButton.setVisible(buttonText != null);
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

	private static JLabel hint(String text)
	{
		JLabel label = new JLabel("<html><body style='width:180px'>" + text + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}
