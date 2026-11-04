package com.homebase;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.homebase.bounties.BountyGuard;
import com.homebase.bounties.BountyOverlay;
import com.homebase.bounties.BountyService;
import com.homebase.collect.ActivityCollector;
import com.homebase.collect.CollectionLogSearch;
import com.homebase.collect.FeedCollector;
import com.homebase.collect.LocalCollector;
import com.homebase.data.GroupIndex;
import com.homebase.data.GroupView;
import com.homebase.data.Ids;
import com.homebase.data.ProfileData;
import com.homebase.data.UpdateEntry;
import com.homebase.goals.GoalService;
import com.homebase.map.GroupWorldMap;
import com.homebase.map.FindOnMap;
import com.homebase.map.MapPins;
import com.homebase.overlay.CollectionLogOverlay;
import com.homebase.overlay.GroupHudOverlay;
import com.homebase.overlay.GroupTileOverlay;
import com.homebase.party.GroupSync;
import com.homebase.party.Presence;
import com.homebase.pings.GroupHighlightOverlay;
import com.homebase.pings.GroupHighlights;
import com.homebase.pings.PingMinimapOverlay;
import com.homebase.pings.PingOverlay;
import com.homebase.pings.PingService;
import com.homebase.requests.RequestDelivery;
import com.homebase.requests.RequestService;
import com.homebase.storage.ReturnTags;
import com.homebase.storage.StorageWatcher;
import com.homebase.sync.GroupStore;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import com.homebase.sync.WireCodec;
import com.homebase.tiles.GroupTiles;
import com.homebase.ui.HomebasePanel;
import com.homebase.updates.UpdateDiff;
import com.homebase.updates.UpdateTracker;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.messages.PartyData;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "The Group Ironman Homebase",
	description = "One home for your group: see each other's stats, gear and banks, ping and highlight together, and share requests, goals and bank bounties",
	tags = {"group", "ironman", "gim", "party", "bank", "storage", "sync", "goals", "requests", "ping", "highlight", "hud", "map", "pin", "search", "drops"},
	internalName = "group-ironman-homebase"
)
public class HomebasePlugin extends Plugin
{
	private static final long SAVE_INTERVAL_SECONDS = 30;
	private static final long FLUSH_INTERVAL_SECONDS = 1;
	private static final int WORLD_MAP_UPDATE_TICKS = 2;
	private static final long GOAL_CHECK_DELAY_SECONDS = 2;
	private static final String CHAT_PREFIX = "<col=ff9800>[Homebase]</col> ";

	/**
	 * How long deleted items are remembered, so an old copy relayed by someone who was away can't
	 * bring them back.
	 */
	private static final long TOMBSTONE_LIFETIME_MS = TimeUnit.DAYS.toMillis(60);

	/**
	 * Request changes older than this aren't announced in chat.
	 */
	private static final long ANNOUNCE_WINDOW_MS = TimeUnit.MINUTES.toMillis(2);

	/**
	 * At most this many request messages in chat within this long.
	 */
	private static final int ANNOUNCE_BURST = 3;
	private static final long ANNOUNCE_BURST_MS = 10_000;

	private static final Type UPDATES_TYPE = new TypeToken<List<UpdateEntry>>()
	{
	}.getType();

	@Inject
	private ClientThread clientThread;

	@Inject
	private HomebaseConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PartyService partyService;

	@Inject
	private PartyDataService partyDataService;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private GroupSync groupSync;

	@Inject
	private Presence presence;

	@Inject
	private LocalCollector collector;

	@Inject
	private RequestService requestService;

	@Inject
	private UpdateTracker updateTracker;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GroupIndex groupIndex;

	@Inject
	private GroupHudOverlay hudOverlay;

	@Inject
	private GroupTileOverlay tileOverlay;

	@Inject
	private GroupTiles groupTiles;

	@Inject
	private GroupWorldMap worldMap;

	@Inject
	private ActivityCollector activityCollector;

	@Inject
	private CollectionLogSearch collectionLogSearch;

	@Inject
	private FeedCollector feedCollector;

	@Inject
	private MapPins mapPins;

	@Inject
	private FindOnMap findOnMap;

	@Inject
	private CollectionLogOverlay collectionLogOverlay;

	@Inject
	private GoalService goalService;

	@Inject
	private BountyService bountyService;

	@Inject
	private ReturnTags returnTags;

	@Inject
	private BountyGuard bountyGuard;

	@Inject
	private BountyOverlay bountyOverlay;

	@Inject
	private RequestDelivery requestDelivery;

	@Inject
	private StorageWatcher storageWatcher;

	@Inject
	private PingService pingService;

	@Inject
	private PingOverlay pingOverlay;

	@Inject
	private PingMinimapOverlay pingMinimapOverlay;

	@Inject
	private GroupHighlights groupHighlights;

	@Inject
	private GroupHighlightOverlay highlightOverlay;

	/**
	 * The Group Ironman group being synced automatically, or null when using a passphrase or no group.
	 */
	@Getter
	private volatile String gimGroup;

	private volatile HomebasePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> flushTask;
	private ScheduledFuture<?> saveTask;
	private volatile OpenGroup currentGroup;

	/**
	 * The passphrase {@link #currentGroup} was opened with, or empty when no group is open.
	 */
	private String openPassphrase;
	private volatile boolean updatesToAnnounce;

	/**
	 * Whether run energy, special attack, hitpoints and prayer are being shared through the party service.
	 */
	private boolean liveShared;

	/**
	 * Whether position is too, which the player can turn off by itself.
	 */
	private boolean positionShared;

	/**
	 * When the latest request messages went to chat.
	 */
	private final Deque<Long> recentAnnouncements = new ArrayDeque<>();
	private int mapTicks;
	private final AtomicBoolean goalCheckScheduled = new AtomicBoolean();
	private final Consumer<UpdateEntry> updateListener = this::onUpdate;

	/**
	 * The group currently loaded, and where it is saved.
	 */
	private static final class OpenGroup
	{
		final GroupStore store = new GroupStore();
		final SyncEngine engine = new SyncEngine(store, System::currentTimeMillis, new Random());
		final AtomicBoolean dirty = new AtomicBoolean();
		final AtomicBoolean updatesDirty = new AtomicBoolean();

		/**
		 * Set once the saved copy is loaded; until then, records coming in are old news.
		 */
		volatile boolean loaded;
		final Filepath file;
		final Filepath updatesFile;
		GroupStore.Listener listener;

		OpenGroup(Filepath file, Filepath updatesFile)
		{
			this.file = file;
			this.updatesFile = updatesFile;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		groupSync.start();
		presence.start();
		eventBus.register(groupSync);
		eventBus.register(presence);
		eventBus.register(collector);
		eventBus.register(feedCollector);
		eventBus.register(mapPins);
		eventBus.register(findOnMap);
		findOnMap.start(this::addChatLine);
		eventBus.register(groupTiles);
		eventBus.register(activityCollector);
		eventBus.register(collectionLogSearch);
		clientThread.invokeLater(collectionLogSearch::preload);
		eventBus.register(bountyGuard);
		eventBus.register(requestDelivery);
		eventBus.register(storageWatcher);
		pingService.start();
		eventBus.register(pingService);
		eventBus.register(groupHighlights);
		collector.setGroupListener(this::onGroupNameRead);
		collector.setSharePolicy(this::belongsToGroup);
		collector.start();
		activityCollector.start();

		// RuneLite's party service shares live stats; our own sync handles everything that persists. Names
		// are always sent, so the group can tell who's in the party; the rest only once that's checked.
		partyDataService.subscribe(this, PartyDataType.CHARACTER_NAME);

		updateTracker.addListener(updateListener);
		panel = injector.getInstance(HomebasePanel.class);
		groupSync.addStatusListener(panel::scheduleLiveRefresh);
		presence.addListener(panel::onlineChanged);
		navButton = NavigationButton.builder()
			.tooltip("Group Homebase")
			.icon(ImageUtil.loadImageResource(HomebasePlugin.class, "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(hudOverlay);
		overlayManager.add(tileOverlay);
		overlayManager.add(bountyOverlay);
		overlayManager.add(highlightOverlay);
		overlayManager.add(pingOverlay);
		overlayManager.add(pingMinimapOverlay);
		overlayManager.add(collectionLogOverlay);

		selectGroup();

		flushTask = executor.scheduleWithFixedDelay(() -> clientThread.invokeLater(() ->
		{
			groupSync.flush();
			updateLiveSharing();
		}), FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
		saveTask = executor.scheduleWithFixedDelay(this::saveCurrent, SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown() throws Exception
	{
		flushTask.cancel(false);
		saveTask.cancel(false);
		closeGroup();
		groupSync.leaveGroupParty();

		requestService.setEngine(null);
		updateTracker.removeListener(updateListener);
		updateTracker.setGroup(null, null);
		collector.stop();
		activityCollector.stop();
		goalService.setGroup(null, null, null);
		bountyService.setEngine(null);
		returnTags.setEngine(null);
		eventBus.unregister(groupHighlights);
		eventBus.unregister(pingService);
		pingService.stop();
		eventBus.unregister(storageWatcher);
		eventBus.unregister(requestDelivery);
		eventBus.unregister(bountyGuard);
		eventBus.unregister(activityCollector);
		eventBus.unregister(collectionLogSearch);
		// these clear state the client thread uses, so they're cleared there
		clientThread.invokeLater(() ->
		{
			requestDelivery.reset();
			storageWatcher.reset();
			bountyGuard.reset();
			collectionLogSearch.reset();
		});
		eventBus.unregister(groupTiles);
		findOnMap.stop();
		eventBus.unregister(findOnMap);
		eventBus.unregister(mapPins);
		eventBus.unregister(feedCollector);
		eventBus.unregister(collector);
		eventBus.unregister(presence);
		eventBus.unregister(groupSync);
		groupSync.stop();
		presence.stop();

		partyDataService.unsubscribe(this, PartyDataType.values());
		partyDataService.unsubscribe(this, Skill.values());
		liveShared = false;
		positionShared = false;

		overlayManager.remove(collectionLogOverlay);
		overlayManager.remove(hudOverlay);
		overlayManager.remove(tileOverlay);
		overlayManager.remove(bountyOverlay);
		overlayManager.remove(highlightOverlay);
		overlayManager.remove(pingOverlay);
		overlayManager.remove(pingMinimapOverlay);
		worldMap.clear();
		groupIndex.setView(null, p -> true);
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		openPassphrase = null;
		gimGroup = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!HomebaseConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		switch (event.getKey())
		{
			case HomebaseConfig.PASSPHRASE_KEY:
			case HomebaseConfig.GIM_SECRET_KEY:
			case HomebaseConfig.GIM_GROUP_KEY:
				selectGroup();
				break;
			case HomebaseConfig.TILE_COLOR_KEY:
				clientThread.invokeLater(() ->
				{
					groupTiles.colorChanged();
					groupHighlights.colorChanged();
					collector.colorChanged();
					mapPins.colorChanged();
				});
				break;
			default:
				if (event.getKey().startsWith(HomebaseConfig.SHARE_KEY_PREFIX))
				{
					clientThread.invokeLater(collector::sharingChanged);
				}
		}
		// plenty of options change what the panel shows
		groupIndex.invalidate();
		HomebasePanel panel = this.panel;
		if (panel != null)
		{
			panel.scheduleRebuild();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (++mapTicks >= WORLD_MAP_UPDATE_TICKS)
		{
			mapTicks = 0;
			worldMap.update();
		}
		if (updatesToAnnounce && collector.getMemberId() != null)
		{
			updatesToAnnounce = false;
			// taken even when the messages are turned off, so turning them on later doesn't replay old ones
			List<UpdateEntry> unannounced = updateTracker.takeUnannounced();
			if (config.updateMessages())
			{
				for (UpdateEntry entry : unannounced)
				{
					addChatLine(UpdateDiff.chatLine(entry.getName(), entry.getChanges()));
				}
			}
		}
	}

	/**
	 * A summary of someone's progress is ready. Runs on the executor.
	 */
	private void onUpdate(UpdateEntry entry)
	{
		OpenGroup group = currentGroup;
		if (group != null)
		{
			group.updatesDirty.set(true);
		}
		HomebasePanel panel = this.panel;
		if (panel != null)
		{
			panel.scheduleRebuild();
		}
		// shown on the next game tick, or after login if you're not logged in yet
		updatesToAnnounce = true;
	}

	@Subscribe
	public void onPartyData(PartyData event)
	{
		HomebasePanel panel = this.panel;
		if (panel != null)
		{
			panel.scheduleLiveRefresh();
		}
	}

	/**
	 * Generate a new passphrase for a brand new group and join it.
	 */
	public void createGroup()
	{
		clientThread.invokeLater(() ->
			configManager.setConfiguration(HomebaseConfig.GROUP, HomebaseConfig.PASSPHRASE_KEY, partyService.generatePassphrase()));
	}

	/**
	 * @return true if the account's data belongs in the current group. With a passphrase every account
	 * shares; for a Group Ironman group only that group's members do, so alts stay out.
	 */
	public boolean belongsToGroup(ProfileData profile)
	{
		String gim = gimGroup;
		return gim == null || (profile.getGroupName() != null && normalize(profile.getGroupName()).equals(normalize(gim)));
	}

	public void copyPassphrase()
	{
		String passphrase = config.groupPassphrase();
		if (!Strings.isNullOrEmpty(passphrase))
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(passphrase), null);
		}
	}

	/**
	 * Pick the group to sync: the passphrase if one is set, otherwise the Group Ironman group this
	 * computer last logged in to.
	 */
	private void selectGroup()
	{
		String passphrase = Strings.nullToEmpty(config.groupPassphrase()).trim();
		if (!passphrase.isEmpty())
		{
			openGroup(passphrase, null);
			return;
		}
		String gim = Strings.emptyToNull(Strings.nullToEmpty(config.gimGroup()).trim());
		openGroup(gim == null ? "" : gimPassphrase(gim, config.gimSecret()), gim);
		// who belongs to the group may have changed with it
		groupIndex.invalidate();
	}

	/**
	 * The party passphrase for a Group Ironman group, so every member arrives at the same party
	 * without sharing anything.
	 */
	static String gimPassphrase(String groupName, String secret)
	{
		String trimmedSecret = Strings.nullToEmpty(secret).trim();
		return "homebase-gim:" + normalize(groupName) + (trimmedSecret.isEmpty() ? "" : ":" + trimmedSecret);
	}

	private static String normalize(String groupName)
	{
		return groupName.replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * The collector read the logged in account's Group Ironman group. Runs on the client thread.
	 */
	private void onGroupNameRead(String name)
	{
		if (Strings.isNullOrEmpty(config.groupPassphrase()) && !name.equals(config.gimGroup()))
		{
			// remembering it changes the config, which switches to the group
			configManager.setConfiguration(HomebaseConfig.GROUP, HomebaseConfig.GIM_GROUP_KEY, name);
		}
	}

	/**
	 * Switch to the group for a passphrase, or to no group when it's empty.
	 *
	 * @param gim the Group Ironman group's name, or null for a passphrase group
	 */
	private void openGroup(String passphrase, String gim)
	{
		passphrase = Strings.nullToEmpty(passphrase).trim();
		gimGroup = gim;
		if (currentGroup != null && passphrase.equals(openPassphrase))
		{
			return;
		}
		openPassphrase = passphrase;

		// a different group means a different party, which is only joined by pressing Connect
		groupSync.leaveGroupParty();
		closeGroup();

		if (passphrase.isEmpty())
		{
			groupSync.setGroup(null, null, null);
			requestService.setEngine(null);
			updateTracker.setGroup(null, null);
			clientThread.invokeLater(() -> collector.setEngine(null));
			groupIndex.setView(null, this::belongsToGroup);
			goalService.setGroup(null, null, null);
			bountyService.setEngine(null);
			returnTags.setEngine(null);
			panel.setView(null);
			return;
		}

		String id = Ids.group(passphrase);
		OpenGroup group = new OpenGroup(groupFile("group-" + id + ".json"), groupFile("updates-" + id + ".json"));
		HomebasePanel panel = this.panel;
		group.listener = (previous, record) ->
		{
			group.dirty.set(true);
			groupIndex.invalidate();
			bountyService.invalidate();
			requestService.invalidate();
			scheduleGoalCheck();
			panel.scheduleRebuild();
			if (group.loaded)
			{
				announce(record);
			}
			updateTracker.onRecordChanged(previous, record);
		};
		group.store.addListener(group.listener);
		currentGroup = group;

		groupSync.setGroup(passphrase, group.engine, gim);
		requestService.setEngine(group.engine);
		clientThread.invokeLater(() -> collector.setEngine(group.engine));
		GroupView view = new GroupView(group.store, gson);
		groupIndex.setView(view, this::belongsToGroup);
		goalService.setGroup(group.engine, view, this::belongsToGroup);
		bountyService.setEngine(group.engine);
		returnTags.setEngine(group.engine);
		panel.setView(view);

		executor.execute(() ->
		{
			load(group);
			group.loaded = true;
			// the first look at goals after loading only notes who has already reached them
			goalService.checkCompletions();
		});
	}

	/**
	 * Look for newly reached goals a little after data changes, so a burst of updates is checked once.
	 */
	private void scheduleGoalCheck()
	{
		if (!goalCheckScheduled.compareAndSet(false, true))
		{
			return;
		}
		executor.schedule(() ->
		{
			goalCheckScheduled.set(false);
			List<String> reached = goalService.checkCompletions();
			if (reached.isEmpty() || !config.goalMessages())
			{
				return;
			}
			clientThread.invokeLater(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					reached.forEach(this::addChatLine);
				}
			});
		}, GOAL_CHECK_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Tell the player in chat when someone else changes a request or pins a spot. Only recent changes
	 * count, so catching up on old ones after logging in stays quiet.
	 */
	private void announce(SyncRecord record)
	{
		String me = collector.getMemberId();
		long now = System.currentTimeMillis();
		if (me == null || me.equals(record.getAuthor()) || record.getVersion() < now - ANNOUNCE_WINDOW_MS)
		{
			return;
		}
		String request = config.requestMessages() ? requestService.describeChange(record) : null;
		String message = request == null && config.pinMessages() ? mapPins.describeChange(record) : request;
		if (message == null || !takeAnnouncementSlot(now))
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				addChatLine(message);
			}
		});
	}

	/**
	 * Runs on the client thread.
	 */
	private void addChatLine(String line)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", CHAT_PREFIX + line, null);
	}

	/**
	 * @return true if another request message may go to chat now: a few at a time at most, so a burst of
	 * changes can't fill the chat box
	 */
	private synchronized boolean takeAnnouncementSlot(long now)
	{
		while (!recentAnnouncements.isEmpty() && now - recentAnnouncements.peekFirst() > ANNOUNCE_BURST_MS)
		{
			recentAnnouncements.pollFirst();
		}
		if (recentAnnouncements.size() >= ANNOUNCE_BURST)
		{
			return false;
		}
		recentAnnouncements.addLast(now);
		return true;
	}

	/**
	 * Share position, run energy, special attack, hitpoints and prayer through RuneLite's party service
	 * only while connected to the group with nobody else in the party. Runs on the client thread.
	 */
	private void updateLiveSharing()
	{
		boolean share = groupSync.isGroupParty() && groupSync.isShareSafe();
		boolean sharePosition = share && config.sharePosition();
		if (sharePosition != positionShared)
		{
			positionShared = sharePosition;
			if (sharePosition)
			{
				partyDataService.subscribe(this, PartyDataType.COORD);
			}
			else
			{
				partyDataService.unsubscribe(this, PartyDataType.COORD);
			}
		}
		if (share == liveShared)
		{
			return;
		}
		liveShared = share;
		if (share)
		{
			partyDataService.subscribe(this, PartyDataType.ENERGY, PartyDataType.SPEC);
			partyDataService.subscribe(this, Skill.HITPOINTS, Skill.PRAYER);
			// the party only sends stats when they change, so ask everyone, us included, to send theirs now
			PartyMember local = partyService.getLocalMember();
			if (local != null)
			{
				UserSync sync = new UserSync();
				sync.setMemberId(local.getMemberId());
				partyService.send(sync);
			}
		}
		else
		{
			partyDataService.unsubscribe(this, PartyDataType.ENERGY, PartyDataType.SPEC);
			partyDataService.unsubscribe(this, Skill.HITPOINTS, Skill.PRAYER);
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		// whoever arrived isn't checked yet, so stop sharing straight away rather than on the next flush
		clientThread.invokeLater(this::updateLiveSharing);
	}

	private void closeGroup()
	{
		OpenGroup group = currentGroup;
		if (group == null)
		{
			return;
		}
		currentGroup = null;
		group.store.removeListener(group.listener);
		// copy the summaries now: shutting down clears them before the save runs
		List<UpdateEntry> updates = new ArrayList<>(updateTracker.getEntries());
		// RuneLite's executor runs one task at a time, so this save always comes after the group's load
		// and can't overwrite the saved copy with a half-loaded one
		executor.execute(() -> save(group, updates));
	}

	private Filepath groupFile(String name)
	{
		try
		{
			return getPluginDirectory().joinSegment(name);
		}
		catch (IOException e)
		{
			log.warn("No plugin data directory, group data won't be saved", e);
			return null;
		}
	}

	private void load(OpenGroup group)
	{
		updateTracker.setGroup(group.store, loadUpdates(group));
		if (group.file == null || !group.file.exists())
		{
			return;
		}
		try (Reader reader = group.file.openBufferedReader())
		{
			List<SyncRecord> records = WireCodec.readSave(gson, reader);
			if (records != null)
			{
				group.engine.load(records);
				group.store.purgeDeleted(System.currentTimeMillis() - TOMBSTONE_LIFETIME_MS);
				log.debug("Loaded {} group records", records.size());
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Couldn't read saved group data, starting fresh", e);
		}
	}

	private List<UpdateEntry> loadUpdates(OpenGroup group)
	{
		if (group.updatesFile == null || !group.updatesFile.exists())
		{
			return null;
		}
		try (Reader reader = group.updatesFile.openBufferedReader())
		{
			List<UpdateEntry> updates = gson.fromJson(reader, UPDATES_TYPE);
			if (updates != null)
			{
				// a damaged file shouldn't leave the Updates tab blank
				updates.removeIf(entry -> entry == null || entry.getChanges() == null);
			}
			return updates;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Couldn't read saved updates", e);
			return null;
		}
	}

	private void saveCurrent()
	{
		OpenGroup group = currentGroup;
		if (group != null)
		{
			save(group, new ArrayList<>(updateTracker.getEntries()));
		}
	}

	private void save(OpenGroup group, List<UpdateEntry> updates)
	{
		if (group.dirty.getAndSet(false) && !write(group.file, writer -> WireCodec.writeSave(gson, writer, group.store.all())))
		{
			group.dirty.set(true);
		}
		if (group.updatesDirty.getAndSet(false) && !write(group.updatesFile, writer -> gson.toJson(updates, UPDATES_TYPE, writer)))
		{
			group.updatesDirty.set(true);
		}
	}

	/**
	 * Write a file through a temporary file, so a crash mid-save can't leave it half written.
	 *
	 * @return true if written
	 */
	private boolean write(Filepath file, Consumer<Writer> content)
	{
		if (file == null)
		{
			return true;
		}
		Filepath tempFile = null;
		try
		{
			Filepath dir = file.getParent();
			dir.createDirectories();
			tempFile = dir.createTempFile("homebase-", ".tmp");
			try (Writer writer = tempFile.openBufferedWriter())
			{
				content.accept(writer);
			}
			tempFile.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			// moved into place, so there's nothing left to delete
			tempFile = null;
			return true;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Couldn't save {}", file.getFileName(), e);
			return false;
		}
		finally
		{
			if (tempFile != null)
			{
				try
				{
					tempFile.deleteIfExists();
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	@Provides
	HomebaseConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HomebaseConfig.class);
	}
}
