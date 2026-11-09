package com.homebase;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.homebase.bounties.BountyService;
import com.homebase.collect.LocalCollector;
import com.homebase.data.GroupIndex;
import com.homebase.data.GroupView;
import com.homebase.data.Ids;
import com.homebase.goals.GoalService;
import com.homebase.party.GroupSync;
import com.homebase.party.LiveSharing;
import com.homebase.party.Presence;
import com.homebase.requests.RequestService;
import com.homebase.sync.GroupStore;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import com.homebase.sync.WireCodec;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.HttpUrl;
import okhttp3.WebSocket;

/**
 * One member's RuneLite: the plugin's real sync, services and collector together with RuneLite's real party
 * service, websocket client, party data and event bus. Only the game and the disk are faked. It's wired up
 * the way {@link HomebasePlugin} does it, leaving out the panel and overlays, which need a game to draw on.
 * <p>
 * The sync reads the wall clock for its own short waits, such as the gap between summaries. Those are moved
 * back as simulated time passes, so they run out as they would in play.
 */
final class SimulatedClient
{
	private static final long TICK_MS = 600;
	private static final long FLUSH_MS = 1_000;
	private static final long SAVE_MS = 30_000;
	private static final long TOMBSTONE_LIFETIME_MS = TimeUnit.DAYS.toMillis(60);

	private final SimulatedParty party;
	private final Random random;
	private final Map<Skill, Integer> xp = new HashMap<>();
	private final List<Consumer<SimulatedClient>> onOpen = new ArrayList<>();

	private final Gson gson = RuneLiteAPI.GSON;

	private String name;
	private long accountHash;
	private String savedCopy;
	private boolean running;
	private GameState gameState = GameState.LOGGED_IN;
	private long clockAheadMs;
	private String secret;
	private String passphrase;
	private int tickCount;
	private long tickTimer;
	private long flushTimer;
	private long saveTimer;

	private Injector injector;
	private EventBus bus;
	private WSClient wsClient;
	private PartyService partyService;
	private ClientThread clientThread;
	private GroupSync groupSync;
	private LiveSharing liveSharing;
	private Presence presence;
	private LocalCollector collector;
	private GroupIndex groupIndex;
	private GoalService goals;
	private BountyService bounties;
	private RequestService requests;
	private GroupStore store;
	private SyncEngine engine;
	private GroupView view;
	private boolean dirty;

	SimulatedClient(SimulatedParty party, String name, long accountHash, long seed)
	{
		this.party = party;
		this.name = name;
		this.accountHash = accountHash;
		this.random = new Random(seed);
		tickTimer = random.nextInt((int) TICK_MS);
		flushTimer = random.nextInt((int) FLUSH_MS);
		start();
	}

	/**
	 * RuneLite opens, with the group remembered from last time.
	 */
	private void start()
	{
		Client client = fakeClient();
		bus = new EventBus(party::noteError);
		HomebaseConfig config = fakeConfig();
		wsClient = construct(WSClient.class, bus, party.http(), HttpUrl.get("https://party.invalid/ws"), gson);
		partyService = construct(PartyService.class, client, wsClient, bus, null);
		injector = Guice.createInjector(binder ->
		{
			binder.bind(Client.class).toInstance(client);
			binder.bind(Gson.class).toInstance(gson);
			binder.bind(HomebaseConfig.class).toInstance(config);
			binder.bind(EventBus.class).toInstance(bus);
			binder.bind(WSClient.class).toInstance(wsClient);
			binder.bind(PartyService.class).toInstance(partyService);
			binder.bind(ScheduledExecutorService.class).toInstance(fake(ScheduledExecutorService.class, Collections.emptyMap()));
		});
		clientThread = injector.getInstance(ClientThread.class);
		groupSync = injector.getInstance(GroupSync.class);
		presence = injector.getInstance(Presence.class);
		collector = injector.getInstance(LocalCollector.class);
		groupIndex = injector.getInstance(GroupIndex.class);
		goals = injector.getInstance(GoalService.class);
		bounties = injector.getInstance(BountyService.class);
		requests = injector.getInstance(RequestService.class);
		liveSharing = injector.getInstance(LiveSharing.class);

		// the order HomebasePlugin starts them in
		groupSync.start();
		presence.start();
		bus.register(groupSync);
		bus.register(presence);
		bus.register(collector);
		HomebasePlugin plugin = new HomebasePlugin();
		String gim = passphrase == null ? party.groupName() : null;
		setGimGroup(plugin, gim);
		collector.setSharePolicy(plugin::belongsToGroup);
		collector.start();
		liveSharing.start(plugin);
		running = true;
		openGroup(plugin, gim);
	}

	/**
	 * What {@link HomebasePlugin} does when it opens a group, loading the saved copy straight away rather
	 * than on its executor.
	 */
	private void openGroup(HomebasePlugin plugin, String gim)
	{
		String partyPassphrase = gim == null ? passphrase : HomebasePlugin.gimPassphrase(gim, secret);
		store = new GroupStore();
		engine = new SyncEngine(store, () -> party.now() + clockAheadMs, new Random(random.nextLong()));
		dirty = false;
		store.addListener((previous, record) ->
		{
			dirty = true;
			groupIndex.invalidate();
			bounties.invalidate();
			requests.invalidate();
		});
		groupSync.setGroup(partyPassphrase, engine, gim);
		requests.setEngine(engine);
		collector.setEngine(engine);
		view = new GroupView(store, gson);
		groupIndex.setView(view, plugin::belongsToGroup);
		goals.setGroup(engine, view, plugin::belongsToGroup);
		bounties.setEngine(engine);
		onOpen.forEach(hook -> hook.accept(this));
		load();
	}

	private void load()
	{
		if (savedCopy == null)
		{
			return;
		}
		List<SyncRecord> records = WireCodec.readSave(gson, new StringReader(savedCopy));
		if (records != null)
		{
			engine.load(records);
			store.purgeDeleted(party.now() + clockAheadMs - TOMBSTONE_LIFETIME_MS);
		}
	}

	private void save()
	{
		if (dirty)
		{
			dirty = false;
			StringWriter writer = new StringWriter();
			WireCodec.writeSave(gson, writer, store.all());
			savedCopy = writer.toString();
		}
	}

	/**
	 * Run something now and each time the group opens again, such as giving a service its engine.
	 */
	void onGroupOpened(Consumer<SimulatedClient> hook)
	{
		onOpen.add(hook);
		hook.accept(this);
	}

	/**
	 * This much time passes. Runs from {@link SimulatedParty#run}.
	 */
	void step(long millis)
	{
		if (!running)
		{
			return;
		}
		ageTimestamps(millis);
		tickTimer += millis;
		if (tickTimer >= TICK_MS)
		{
			tickTimer -= TICK_MS;
			if (gameState == GameState.LOGGED_IN)
			{
				tickCount++;
				bus.post(new GameTick());
			}
		}
		flushTimer += millis;
		if (flushTimer >= FLUSH_MS)
		{
			flushTimer -= FLUSH_MS;
			clientThread.invokeLater(() ->
			{
				liveSharing.update();
				groupSync.flush();
			});
		}
		saveTimer += millis;
		if (saveTimer >= SAVE_MS)
		{
			saveTimer -= SAVE_MS;
			save();
		}
		runClientThread();
	}

	/**
	 * @return true if there was anything to run
	 */
	boolean runClientThread()
	{
		if (!running)
		{
			return false;
		}
		try
		{
			Field waiting = ClientThread.class.getDeclaredField("invokes");
			waiting.setAccessible(true);
			boolean any = !((Collection<?>) waiting.get(clientThread)).isEmpty();
			Method invoke = ClientThread.class.getDeclaredMethod("invoke");
			invoke.setAccessible(true);
			invoke.invoke(clientThread);
			return any;
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	/**
	 * The player presses Connect.
	 */
	void connect()
	{
		groupSync.joinGroupParty();
		party.settle();
	}

	/**
	 * The player presses Disconnect.
	 */
	void disconnect()
	{
		groupSync.leaveGroupParty();
		party.settle();
	}

	/**
	 * RuneLite's own Party plugin is turned on. It has the panel for typing in a party's name, and sends the
	 * character's name to whichever party they're in.
	 */
	void turnOnPartyPlugin()
	{
		injector.getInstance(PartyDataService.class).subscribe(new HomebasePlugin(), PartyDataType.CHARACTER_NAME);
	}

	/**
	 * The player joins a party some other way, such as through RuneLite's Party panel.
	 */
	void joinPartyDirectly(String partyPassphrase)
	{
		partyService.changeParty(partyPassphrase);
		party.settle();
	}

	void logOut()
	{
		setGameState(GameState.LOGIN_SCREEN);
		party.settle();
	}

	void logIn()
	{
		setGameState(GameState.LOGGED_IN);
		party.settle();
	}

	/**
	 * Log out, and back in on a different account. The game's member list decides whether it's in the group.
	 */
	void logInOnAnotherAccount(String accountName)
	{
		logOut();
		// nobody gets through the login screen instantly
		party.run(5_000);
		name = accountName;
		accountHash = accountName.hashCode();
		logIn();
	}

	private void setGameState(GameState state)
	{
		gameState = state;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		bus.post(event);
	}

	/**
	 * The player closes RuneLite's window, which stays closed until {@link #openRuneLite}. Plugins aren't
	 * stopped when that happens, so anything not yet saved is lost unless the plugin saves on
	 * {@link ClientShutdown}. The saving is done here, so it only happens if the plugin would do it.
	 */
	void closeRuneLite()
	{
		if (pluginActsOnClosing())
		{
			save();
		}
		wsClient.close();
		running = false;
		party.settle();
	}

	private static boolean pluginActsOnClosing()
	{
		for (Method method : HomebasePlugin.class.getDeclaredMethods())
		{
			if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1
				&& method.getParameterTypes()[0] == ClientShutdown.class)
			{
				return true;
			}
		}
		return false;
	}

	void openRuneLite()
	{
		gameState = GameState.LOGGED_IN;
		start();
		party.settle();
	}

	/**
	 * The plugin is turned off and on again, which saves on the way out and leaves the party.
	 */
	void restartPlugin()
	{
		groupSync.leaveGroupParty();
		save();
		running = false;
		party.settle();
		start();
		party.settle();
	}

	/**
	 * The save file is lost, as on a new computer. RuneLite has to be closed for it to matter.
	 */
	void loseSavedCopy()
	{
		savedCopy = null;
	}

	void setClockAhead(long millis)
	{
		clockAheadMs = millis;
	}

	/**
	 * Set the Group Ironman secret, which takes effect the next time RuneLite opens.
	 */
	void setSecret(String secret)
	{
		this.secret = secret;
	}

	/**
	 * Set a group passphrase, which takes effect the next time RuneLite opens.
	 */
	void setPassphrase(String passphrase)
	{
		this.passphrase = passphrase;
	}

	/**
	 * The player's level changes, which the game reports with the new experience.
	 */
	void setXp(Skill skill, int amount)
	{
		xp.put(skill, amount);
		bus.post(new StatChanged(skill, amount, 1, 1));
	}

	/**
	 * Lose the connection without RuneLite knowing it's coming.
	 */
	void loseConnection()
	{
		try
		{
			Field socket = WSClient.class.getDeclaredField("webSocket");
			socket.setAccessible(true);
			party.loseConnection((WebSocket) socket.get(wsClient));
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	String name()
	{
		return name;
	}

	String id()
	{
		return Ids.member(accountHash);
	}

	GroupSync.Status status()
	{
		return groupSync.getStatus();
	}

	GroupSync groupSync()
	{
		return groupSync;
	}

	LocalCollector collector()
	{
		return collector;
	}

	GroupIndex groupIndex()
	{
		return groupIndex;
	}

	GoalService goals()
	{
		return goals;
	}

	BountyService bounties()
	{
		return bounties;
	}

	RequestService requests()
	{
		return requests;
	}

	GroupStore store()
	{
		return store;
	}

	SyncEngine engine()
	{
		return engine;
	}

	GroupView view()
	{
		return view;
	}

	Gson gson()
	{
		return gson;
	}

	/**
	 * @return one of the plugin's own objects, as this client's injector makes it
	 */
	<T> T get(Class<T> type)
	{
		return injector.getInstance(type);
	}

	/**
	 * @return everything this client holds, deleted records included, in a form that can be compared
	 */
	List<String> recordsAsText()
	{
		List<String> out = new ArrayList<>();
		for (SyncRecord record : store.all())
		{
			out.add(record.getKey() + " v" + record.getVersion() + " by " + record.getAuthor()
				+ (record.isDeleted() ? " deleted" : " " + record.getData()));
		}
		Collections.sort(out);
		return out;
	}

	private void ageTimestamps(long millis)
	{
		age(groupSync, "lastSummaryAt", millis);
		age(groupSync, "loginScreenSince", millis);
		ageAll(groupSync, "listedAt", millis);
		ageAll(groupSync, "heardAt", millis);
		age(presence, "lastSent", millis);
		ageAll(presence, "lastHeard", millis);
		ageAll(collector, "lastPublished", millis);
	}

	private static void age(Object owner, String fieldName, long millis)
	{
		try
		{
			Field field = owner.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			long value = field.getLong(owner);
			if (value > 0)
			{
				field.setLong(owner, value - millis);
			}
		}
		catch (NoSuchFieldException e)
		{
			// older versions of the plugin don't have every one of these
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <K> void ageAll(Object owner, String fieldName, long millis)
	{
		try
		{
			Field field = owner.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			Map<K, Long> times = (Map<K, Long>) field.get(owner);
			times.replaceAll((key, value) -> value - millis);
		}
		catch (NoSuchFieldException e)
		{
			// older versions of the plugin don't have every one of these
		}
		catch (IllegalAccessException e)
		{
			throw new AssertionError(e);
		}
	}

	private static void setGimGroup(HomebasePlugin plugin, String gim)
	{
		try
		{
			Field field = HomebasePlugin.class.getDeclaredField("gimGroup");
			field.setAccessible(true);
			field.set(plugin, gim);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T construct(Class<T> type, Object... arguments)
	{
		try
		{
			Constructor<?> constructor = type.getDeclaredConstructors()[0];
			constructor.setAccessible(true);
			return (T) constructor.newInstance(arguments);
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	private Client fakeClient()
	{
		Map<String, Function<Object[], Object>> player = new HashMap<>();
		player.put("getName", args -> name);
		Player local = fake(Player.class, player);

		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getGameState", args -> gameState);
		answers.put("getAccountHash", args -> accountHash);
		answers.put("getLocalPlayer", args -> gameState == GameState.LOGGED_IN ? local : null);
		answers.put("getWorld", args -> 301);
		answers.put("getTickCount", args -> tickCount);
		answers.put("isClientThread", args -> true);
		answers.put("getIntStack", args -> new int[]{1});
		answers.put("getSkillExperience", args -> args[0] instanceof Skill ? xp.getOrDefault(args[0], 0) : 0);
		answers.put("getClanChannel", args -> loggedInToGroup() ? clanChannel() : null);
		answers.put("getClanSettings", args -> loggedInToGroup() ? clanSettings() : null);
		return fake(Client.class, answers);
	}

	private boolean loggedInToGroup()
	{
		return gameState == GameState.LOGGED_IN && party.isOnRoster(name);
	}

	private ClanChannel clanChannel()
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getName", args -> party.groupName());
		return fake(ClanChannel.class, answers);
	}

	private ClanSettings clanSettings()
	{
		List<ClanMember> members = new ArrayList<>();
		for (String member : party.roster())
		{
			Map<String, Function<Object[], Object>> answers = new HashMap<>();
			answers.put("getName", args -> member);
			members.add(fake(ClanMember.class, answers));
		}
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getName", args -> party.groupName());
		answers.put("getMembers", args -> members);
		return fake(ClanSettings.class, answers);
	}

	/**
	 * @return settings that answer as they do for a player who has never changed them
	 */
	private static HomebaseConfig fakeConfig()
	{
		return (HomebaseConfig) Proxy.newProxyInstance(HomebaseConfig.class.getClassLoader(), new Class<?>[]{HomebaseConfig.class},
			(proxy, method, args) ->
			{
				if (method.isDefault())
				{
					return MethodHandles.privateLookupIn(HomebaseConfig.class, MethodHandles.lookup())
						.unreflectSpecial(method, HomebaseConfig.class)
						.bindTo(proxy)
						.invokeWithArguments(args == null ? new Object[0] : args);
				}
				return nothing(method.getReturnType());
			});
	}

	/**
	 * @return a stand-in that answers the named methods, and gives 0, false or null for the rest
	 */
	@SuppressWarnings("unchecked")
	private static <T> T fake(Class<T> type, Map<String, Function<Object[], Object>> answers)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) ->
		{
			Function<Object[], Object> answer = answers.get(method.getName());
			if (answer != null)
			{
				return answer.apply(args);
			}
			if (method.getName().equals("hashCode"))
			{
				return System.identityHashCode(proxy);
			}
			if (method.getName().equals("equals"))
			{
				return proxy == args[0];
			}
			if (method.getName().equals("toString"))
			{
				return type.getSimpleName();
			}
			return nothing(method.getReturnType());
		});
	}

	private static Object nothing(Class<?> returns)
	{
		if (returns == boolean.class)
		{
			return false;
		}
		if (returns == long.class)
		{
			return 0L;
		}
		if (returns == int.class)
		{
			return 0;
		}
		if (returns == double.class)
		{
			return 0D;
		}
		if (returns == float.class)
		{
			return 0F;
		}
		if (returns == short.class)
		{
			return (short) 0;
		}
		if (returns == byte.class)
		{
			return (byte) 0;
		}
		if (returns == char.class)
		{
			return (char) 0;
		}
		return null;
	}
}
