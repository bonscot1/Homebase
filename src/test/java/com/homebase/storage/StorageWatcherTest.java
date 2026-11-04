package com.homebase.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.homebase.collect.LocalCollector;
import com.homebase.data.GroupView;
import com.homebase.data.Section;
import com.homebase.data.StorageLogData;
import com.homebase.sync.GroupStore;
import com.homebase.sync.SyncEngine;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import org.junit.Before;
import org.junit.Test;

/**
 * The real watcher, collector, event bus and store, with only the game itself faked: what goes on screen
 * and in the container is set by each test, and the same events the game sends are posted.
 */
public class StorageWatcherTest
{
	private static final int SHARK = 385;
	private static final int COAL = 453;

	private final Map<Integer, Widget> widgets = new HashMap<>();
	private final Map<Integer, ItemContainer> containers = new HashMap<>();
	private final GroupStore store = new GroupStore();
	private final EventBus bus = new EventBus();

	private LocalCollector collector;
	private StorageWatcher watcher;
	private String memberId;

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
			Class<?> returns = method.getReturnType();
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
				return method.getName().equals("hashCode") ? System.identityHashCode(proxy) : 0;
			}
			return returns == String.class ? type.getSimpleName() : null;
		});
	}

	private static Widget widget(String text)
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getText", args -> text);
		return fake(Widget.class, answers);
	}

	private static ItemContainer container(Item... items)
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getItems", args -> items);
		return fake(ItemContainer.class, answers);
	}

	@Before
	public void logIn()
	{
		Map<String, Function<Object[], Object>> player = new HashMap<>();
		player.put("getName", args -> "Mundell");
		Player local = fake(Player.class, player);

		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getGameState", args -> GameState.LOGGED_IN);
		answers.put("getAccountHash", args -> 12345L);
		answers.put("getLocalPlayer", args -> local);
		answers.put("getWidget", args -> args.length == 1 ? widgets.get(args[0]) : null);
		answers.put("getItemContainer", args -> containers.get(args[0]));
		Client client = fake(Client.class, answers);

		Injector injector = Guice.createInjector(binder ->
		{
			binder.bind(Client.class).toInstance(client);
			binder.bind(Gson.class).toInstance(new Gson());
		});
		collector = injector.getInstance(LocalCollector.class);
		watcher = injector.getInstance(StorageWatcher.class);
		bus.register(collector);
		bus.register(watcher);

		collector.setEngine(new SyncEngine(store, System::currentTimeMillis, new Random(1)));
		collector.start();
		tick();
		tick();
		memberId = collector.getMemberId();
		assertNotNull(memberId);
	}

	private void tick()
	{
		bus.post(new GameTick());
	}

	/**
	 * Group storage opens, drawn from what it holds.
	 */
	private void open(Item... items)
	{
		widgets.put(InterfaceID.SharedBank.ITEMS, widget(""));
		containers.put(InventoryID.INV_GROUP_TEMP, container(items));
		bus.post(new ScriptPostFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
		tick();
	}

	/**
	 * Something goes in or comes out, which the game reports and then draws.
	 */
	private void change(Item... items)
	{
		ItemContainer now = container(items);
		containers.put(InventoryID.INV_GROUP_TEMP, now);
		bus.post(new ItemContainerChanged(InventoryID.INV_GROUP_TEMP, now));
		bus.post(new ScriptPostFired(ScriptID.GROUP_IRONMAN_STORAGE_BUILD));
		tick();
	}

	private void close()
	{
		widgets.remove(InterfaceID.SharedBank.ITEMS);
		containers.remove(InventoryID.INV_GROUP_TEMP);
	}

	private void saveWhileMessageShows()
	{
		widgets.put(InterfaceID.LoadingIconModal.TEXT, widget("Saving..."));
		tick();
		widgets.remove(InterfaceID.LoadingIconModal.TEXT);
	}

	private void waitTicks(int ticks)
	{
		for (int i = 0; i < ticks; i++)
		{
			tick();
		}
	}

	private List<StorageLogData.Entry> log()
	{
		StorageLogData log = new GroupView(store, new Gson()).get(memberId, Section.STORAGE_LOG, StorageLogData.class);
		return log == null ? null : log.getEntries();
	}

	private static void assertEntries(List<StorageLogData.Entry> entries, int... itemThenAmount)
	{
		assertEquals(itemThenAmount.length / 2, entries.size());
		for (int i = 0; i < entries.size(); i++)
		{
			assertEquals(itemThenAmount[2 * i], entries.get(i).getItem());
			assertEquals(itemThenAmount[2 * i + 1], entries.get(i).getAmount());
			assertTrue(entries.get(i).getAt() > 0);
		}
	}

	@Test
	public void closingWithoutChangingAnythingAddsNoEntry()
	{
		open(new Item(SHARK, 5));
		close();
		saveWhileMessageShows();
		waitTicks(2);
		assertNull(log());
	}

	@Test
	public void closingWithoutSavingAddsNoEntry()
	{
		open(new Item(SHARK, 5));
		change(new Item(SHARK, 2));
		close();
		waitTicks(StorageSession.CLOSE_GRACE_TICKS + 3);
		assertNull(log());
	}

	@Test
	public void aSaveSharesWhatChangedUnderTheMembersOwnKey()
	{
		open(new Item(SHARK, 5));
		change(new Item(SHARK, 2));
		change(new Item(SHARK, 2), new Item(COAL, 30));
		close();
		saveWhileMessageShows();
		waitTicks(2);
		assertEntries(log(), SHARK, -3, COAL, 30);
		assertEquals(memberId, store.get(Section.STORAGE_LOG.keyFor(memberId)).getAuthor());
	}

	@Test
	public void theMessageClosingBetweenTicksStillCounts()
	{
		open(new Item(SHARK, 5));
		change(new Item(SHARK, 2));
		close();
		// up and gone again between two ticks: there's text to read, but it no longer shows
		widgets.put(InterfaceID.LoadingIconModal.TEXT, widget("Saving..."));
		bus.post(new WidgetClosed(InterfaceID.LOADING_ICON_MODAL, 0, true));
		widgets.remove(InterfaceID.LoadingIconModal.TEXT);
		waitTicks(2);
		assertEntries(log(), SHARK, -3);
	}

	@Test
	public void theLoadingMessageIsntASave()
	{
		open(new Item(SHARK, 5));
		change(new Item(SHARK, 2));
		widgets.put(InterfaceID.LoadingIconModal.TEXT, widget("Loading..."));
		tick();
		bus.post(new WidgetClosed(InterfaceID.LOADING_ICON_MODAL, 0, true));
		waitTicks(2);
		assertNull(log());
	}

	@Test
	public void savesAddUpEvenBeforeTheLastOneHasBeenSent()
	{
		open(new Item(SHARK, 5));
		change(new Item(SHARK, 2));
		saveWhileMessageShows();
		waitTicks(2);
		assertEntries(log(), SHARK, -3);

		// these come sooner than a log can be sent again, so they wait with the entries before them
		change(new Item(SHARK, 2), new Item(COAL, 1));
		saveWhileMessageShows();
		change(new Item(COAL, 1));
		saveWhileMessageShows();
		waitTicks(2);
		assertEntries(log(), SHARK, -3);
		assertEntries(collector.shared(Section.STORAGE_LOG, StorageLogData.class).getEntries(), SHARK, -3, COAL, 1, SHARK, -2);
	}

	@Test
	public void leavingTheGameDropsWhatWasntSaved()
	{
		open(new Item(SHARK, 5));
		change(new Item(SHARK, 2));
		GameStateChanged hopping = new GameStateChanged();
		hopping.setGameState(GameState.HOPPING);
		bus.post(hopping);
		close();
		saveWhileMessageShows();
		waitTicks(2);
		assertNull(log());
	}

	@Test
	public void resetWhenThePluginStopsDropsWhatWasntSaved()
	{
		open(new Item(SHARK, 5));
		change(new Item(SHARK, 2));
		watcher.reset();
		close();
		saveWhileMessageShows();
		waitTicks(2);
		assertNull(log());
	}
}
