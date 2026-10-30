package com.homebase;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import com.homebase.collect.LocalCollector;
import com.homebase.data.GroupView;
import com.homebase.data.Ids;
import com.homebase.data.ProfileData;
import com.homebase.party.GroupSync;
import com.homebase.requests.RequestService;
import com.homebase.sync.GroupStore;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import com.homebase.sync.WireCodec;
import com.homebase.ui.HomebasePanel;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.StandardCopyOption;
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
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyData;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "The Group Ironman Homebase",
	description = "One home for your group: synced inventories, banks, stats and more, with no server to host",
	tags = {"group", "ironman", "gim", "party", "bank", "storage", "sync"},
	internalName = "group-ironman-homebase"
)
public class HomebasePlugin extends Plugin
{
	private static final long SAVE_INTERVAL_SECONDS = 30;

	/**
	 * How long deleted items are remembered, so an old copy relayed by someone who was away can't
	 * bring them back.
	 */
	private static final long TOMBSTONE_LIFETIME_MS = TimeUnit.DAYS.toMillis(60);

	/**
	 * Request changes older than this aren't announced in chat.
	 */
	private static final long ANNOUNCE_WINDOW_MS = TimeUnit.MINUTES.toMillis(2);

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
	private LocalCollector collector;

	@Inject
	private RequestService requestService;

	@Inject
	private Client client;

	/**
	 * The Group Ironman group being synced automatically, or null when using a passphrase or no group.
	 */
	@Getter
	private volatile String gimGroup;

	private HomebasePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> flushTask;
	private ScheduledFuture<?> saveTask;
	private OpenGroup group;
	private String openPassphrase;

	/**
	 * The group currently loaded, and where it is saved.
	 */
	private static final class OpenGroup
	{
		final GroupStore store = new GroupStore();
		final SyncEngine engine = new SyncEngine(store, System::currentTimeMillis, new Random());
		final AtomicBoolean dirty = new AtomicBoolean();
		final Filepath file;
		Consumer<SyncRecord> listener;

		OpenGroup(Filepath file)
		{
			this.file = file;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		groupSync.start();
		eventBus.register(groupSync);
		eventBus.register(collector);
		collector.setGroupListener(this::onGroupNameRead);
		collector.setSharePolicy(this::belongsToGroup);
		collector.start();

		// RuneLite's party service shares live stats; our own sync handles everything that persists
		partyDataService.subscribe(this, PartyDataType.CHARACTER_NAME, PartyDataType.COORD, PartyDataType.ENERGY);
		partyDataService.subscribe(this, Skill.HITPOINTS, Skill.PRAYER);

		panel = injector.getInstance(HomebasePanel.class);
		groupSync.addStatusListener(panel::scheduleLiveRefresh);
		navButton = NavigationButton.builder()
			.tooltip("Group Homebase")
			.icon(ImageUtil.loadImageResource(HomebasePlugin.class, "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		selectGroup();

		flushTask = executor.scheduleWithFixedDelay(() -> clientThread.invokeLater(groupSync::flush), 1, 1, TimeUnit.SECONDS);
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
		collector.stop();
		eventBus.unregister(collector);
		eventBus.unregister(groupSync);
		groupSync.stop();

		partyDataService.unsubscribe(this, PartyDataType.values());
		partyDataService.unsubscribe(this, Skill.values());

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
		}
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
		String group = gimGroup;
		return group == null || (profile.getGroupName() != null && normalize(profile.getGroupName()).equals(normalize(group)));
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
	}

	/**
	 * The party passphrase for a Group Ironman group, so every member arrives at the same party
	 * without sharing anything.
	 */
	static String gimPassphrase(String groupName, String secret)
	{
		String s = Strings.nullToEmpty(secret).trim();
		return "homebase-gim:" + normalize(groupName) + (s.isEmpty() ? "" : ":" + s);
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

	private void openGroup(String passphrase, String gim)
	{
		passphrase = Strings.nullToEmpty(passphrase).trim();
		gimGroup = gim;
		if (group != null && passphrase.equals(openPassphrase))
		{
			return;
		}
		openPassphrase = passphrase;

		boolean wasInGroupParty = groupSync.isGroupParty();
		closeGroup();

		if (passphrase.isEmpty())
		{
			groupSync.setGroup(null, null);
			requestService.setEngine(null);
			clientThread.invokeLater(() -> collector.setEngine(null));
			panel.setView(null);
			if (wasInGroupParty)
			{
				partyService.changeParty(null);
			}
			return;
		}

		OpenGroup g = new OpenGroup(groupFile(passphrase));
		HomebasePanel panel = this.panel;
		g.listener = record ->
		{
			g.dirty.set(true);
			panel.scheduleRebuild();
			announce(record);
		};
		g.store.addListener(g.listener);
		group = g;
		executor.execute(() -> load(g));

		groupSync.setGroup(passphrase, g.engine);
		requestService.setEngine(g.engine);
		clientThread.invokeLater(() -> collector.setEngine(g.engine));
		panel.setView(new GroupView(g.store, gson));

		if (wasInGroupParty || (config.autoJoin() && !partyService.isInParty()))
		{
			groupSync.joinGroupParty();
		}
	}

	/**
	 * Tell the player in chat when someone else changes a request. Only recent changes count, so
	 * catching up on old requests after logging in stays quiet.
	 */
	private void announce(SyncRecord record)
	{
		String me = collector.getMemberId();
		if (!config.requestMessages() || me == null || me.equals(record.getAuthor())
			|| record.getVersion() < System.currentTimeMillis() - ANNOUNCE_WINDOW_MS)
		{
			return;
		}
		String message = requestService.describeChange(record);
		if (message == null)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff9800>[Homebase]</col> " + message, null);
			}
		});
	}

	private void closeGroup()
	{
		OpenGroup g = group;
		if (g == null)
		{
			return;
		}
		group = null;
		g.store.removeListener(g.listener);
		executor.execute(() -> save(g));
	}

	private Filepath groupFile(String passphrase)
	{
		try
		{
			return getPluginDirectory().joinSegment("group-" + Ids.group(passphrase) + ".json");
		}
		catch (IOException e)
		{
			log.warn("No plugin data directory, group data won't be saved", e);
			return null;
		}
	}

	private void load(OpenGroup g)
	{
		if (g.file == null || !g.file.exists())
		{
			return;
		}
		try (Reader reader = g.file.openBufferedReader())
		{
			List<SyncRecord> records = WireCodec.readSave(gson, reader);
			if (records != null)
			{
				g.engine.load(records);
				g.store.purgeDeleted(System.currentTimeMillis() - TOMBSTONE_LIFETIME_MS);
				log.debug("Loaded {} group records", records.size());
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Couldn't read saved group data, starting fresh", e);
		}
	}

	private void saveCurrent()
	{
		OpenGroup g = group;
		if (g != null)
		{
			save(g);
		}
	}

	private void save(OpenGroup g)
	{
		if (g.file == null || !g.dirty.getAndSet(false))
		{
			return;
		}
		Filepath tmp = null;
		try
		{
			Filepath dir = g.file.getParent();
			dir.createDirectories();
			tmp = dir.createTempFile("group-", ".tmp");
			try (Writer writer = tmp.openBufferedWriter())
			{
				WireCodec.writeSave(gson, writer, g.store.all());
			}
			tmp.moveTo(g.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			tmp = null;
		}
		catch (IOException e)
		{
			g.dirty.set(true);
			log.warn("Couldn't save group data", e);
		}
		finally
		{
			if (tmp != null)
			{
				try
				{
					tmp.deleteIfExists();
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
