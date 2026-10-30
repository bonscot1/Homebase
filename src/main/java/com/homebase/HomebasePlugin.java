package com.homebase;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import com.homebase.collect.LocalCollector;
import com.homebase.data.GroupView;
import com.homebase.data.Ids;
import com.homebase.party.GroupSync;
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
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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

	private HomebasePanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> flushTask;
	private ScheduledFuture<?> saveTask;
	private OpenGroup group;

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

		openGroup(config.groupPassphrase());

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

		collector.stop();
		eventBus.unregister(collector);
		eventBus.unregister(groupSync);
		groupSync.stop();

		partyDataService.unsubscribe(this, PartyDataType.values());
		partyDataService.unsubscribe(this, Skill.values());

		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (HomebaseConfig.GROUP.equals(event.getGroup()) && HomebaseConfig.PASSPHRASE_KEY.equals(event.getKey()))
		{
			openGroup(config.groupPassphrase());
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

	public void copyPassphrase()
	{
		String passphrase = config.groupPassphrase();
		if (!Strings.isNullOrEmpty(passphrase))
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(passphrase), null);
		}
	}

	private void openGroup(String passphrase)
	{
		boolean wasInGroupParty = groupSync.isGroupParty();
		closeGroup();

		passphrase = Strings.nullToEmpty(passphrase).trim();
		if (passphrase.isEmpty())
		{
			groupSync.setGroup(null, null);
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
		};
		g.store.addListener(g.listener);
		group = g;
		executor.execute(() -> load(g));

		groupSync.setGroup(passphrase, g.engine);
		clientThread.invokeLater(() -> collector.setEngine(g.engine));
		panel.setView(new GroupView(g.store, gson));

		if (wasInGroupParty || (config.autoJoin() && !partyService.isInParty()))
		{
			groupSync.joinGroupParty();
		}
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
