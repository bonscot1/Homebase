package com.homebase.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.homebase.HomebaseConfig;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class LocalCollectorTest
{
	private static final int HARDCORE_GROUP_IRONMAN = 5;
	private static final int REGULAR_ACCOUNT = 0;

	private int accountType = HARDCORE_GROUP_IRONMAN;
	private ClanSettings settings;

	@Test
	public void aGroupWhoseDetailsArriveLateIsStillFound()
	{
		List<String> found = new ArrayList<>();
		LocalCollector collector = loggedIn(found);

		// logged in, with no group chat channel and no group details yet
		tick(collector, 10);
		assertTrue(found.isEmpty());
		assertTrue(collector.isLookingForGroup());

		// the details turn up without the chat channel changing
		settings = clanSettings("Hungry\u00A0Horse");
		tick(collector, 1);
		assertEquals(Collections.singletonList("Hungry\u00A0Horse"), found);
		assertFalse(collector.isLookingForGroup());

		// and once found, it isn't reported again every tick
		tick(collector, 10);
		assertEquals(1, found.size());
	}

	@Test
	public void anAccountThatIsntAGroupIronmanIsntLookedUp()
	{
		accountType = REGULAR_ACCOUNT;
		List<String> found = new ArrayList<>();
		LocalCollector collector = loggedIn(found);

		tick(collector, 10);

		assertFalse(collector.isLookingForGroup());
		assertTrue(found.isEmpty());
	}

	@Test
	public void aGroupIronmanWithNoGroupIsOnlyToldItsComingForAWhile()
	{
		List<String> found = new ArrayList<>();
		LocalCollector collector = loggedIn(found);

		tick(collector, 20);
		assertTrue(collector.isLookingForGroup());

		// after half a minute they can make a group of their own
		tick(collector, 40);
		assertFalse(collector.isLookingForGroup());

		// it's still found if it turns up later
		settings = clanSettings("HungryHorse");
		tick(collector, 1);
		assertEquals(Collections.singletonList("HungryHorse"), found);
	}

	private LocalCollector loggedIn(List<String> found)
	{
		LocalCollector collector = new LocalCollector(fakeClient(), new Gson(), fakeConfig());
		collector.setGroupListener(found::add);
		collector.start();
		return collector;
	}

	private static void tick(LocalCollector collector, int ticks)
	{
		for (int i = 0; i < ticks; i++)
		{
			collector.onGameTick(new GameTick());
		}
	}

	private Client fakeClient()
	{
		Map<String, Function<Object[], Object>> player = new HashMap<>();
		player.put("getName", args -> "Alice");
		Player local = fake(Player.class, player);

		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getGameState", args -> GameState.LOGGED_IN);
		answers.put("getAccountHash", args -> 42L);
		answers.put("getLocalPlayer", args -> local);
		answers.put("getWorld", args -> 301);
		// what reading the quest list needs
		answers.put("getIntStack", args -> new int[]{1});
		answers.put("getVarbitValue", args -> (int) args[0] == VarbitID.IRONMAN ? accountType : 0);
		answers.put("getClanSettings", args -> settings);
		return fake(Client.class, answers);
	}

	private static ClanSettings clanSettings(String name)
	{
		Map<String, Function<Object[], Object>> answers = new HashMap<>();
		answers.put("getName", args -> name);
		answers.put("getMembers", args -> Collections.emptyList());
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
		return null;
	}
}
