package com.homebase.collect;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.data.FeedData;
import com.homebase.data.Section;
import com.homebase.map.AreaNames;
import com.homebase.party.Presence;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Tells the group about the player's valuable drops, pets and deaths, for their Updates tab and chat.
 * Runs on the client thread.
 */
@Singleton
public class FeedCollector
{
	/**
	 * Drops worth less than this are never sent. Each member then chooses how much a drop must be worth
	 * for them to see it, from this upwards.
	 */
	public static final int MIN_SHARED_VALUE = 10_000;

	private static final int MAX_TEXT = 60;

	/**
	 * What the game says when a pet arrives, whether it follows you or lands in your backpack.
	 */
	private static final List<String> PET_MESSAGES = Arrays.asList(
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack");

	private final Client client;
	private final HomebaseConfig config;
	private final ItemManager itemManager;
	private final LocalCollector collector;
	private final Presence presence;

	/**
	 * The member we've made sure has a feed, so a first drop has something to be compared against.
	 */
	private String startedFor;

	@Inject
	private FeedCollector(Client client, HomebaseConfig config, ItemManager itemManager, LocalCollector collector, Presence presence)
	{
		this.client = client;
		this.config = config;
		this.itemManager = itemManager;
		this.collector = collector;
		this.presence = presence;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		String memberId = collector.getMemberId();
		if (memberId != null && !memberId.equals(startedFor))
		{
			startedFor = memberId;
			if (collector.shared(Section.FEED, FeedData.class) == null)
			{
				collector.share(Section.FEED, new FeedData());
			}
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		recordDrops(event.getNpc().getName(), event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		// the player's name stays out of it: this only shares what's our own
		recordDrops(null, event.getItems());
	}

	/**
	 * Chests, clue caskets and the like, as RuneLite's Loot Tracker plugin reports them. It reports
	 * monsters and players too, which are already covered above.
	 */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() == LootRecordType.EVENT)
		{
			recordDrops(event.getName(), event.getItems());
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() != client.getLocalPlayer() || !config.shareDeaths())
		{
			return;
		}
		// someone hiding their position still has their death reported, without saying where
		WorldPoint where = config.sharePosition() ? presence.localPosition() : null;
		add(new FeedData.Entry(System.currentTimeMillis(), FeedData.DEATH,
			where == null ? null : AreaNames.describe(where), 0, 0, null));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if ((event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
			&& config.shareDrops() && isPetMessage(Text.removeTags(event.getMessage())))
		{
			add(new FeedData.Entry(System.currentTimeMillis(), FeedData.PET, null, 0, 0, null));
		}
	}

	static boolean isPetMessage(String message)
	{
		return PET_MESSAGES.stream().anyMatch(message::startsWith);
	}

	private void recordDrops(String source, Collection<ItemStack> items)
	{
		if (!config.shareDrops() || items == null)
		{
			return;
		}
		// a stackable item can arrive as several piles
		Map<Integer, Long> totals = new LinkedHashMap<>();
		for (ItemStack item : items)
		{
			totals.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
		}
		long now = System.currentTimeMillis();
		String from = Clean.text(source, MAX_TEXT);
		totals.forEach((itemId, quantity) ->
		{
			long value = itemManager.getItemPrice(itemId) * quantity;
			if (value >= MIN_SHARED_VALUE)
			{
				String name = Clean.text(itemManager.getItemComposition(itemId).getName(), MAX_TEXT);
				add(new FeedData.Entry(now, FeedData.DROP, name, (int) Math.min(quantity, Integer.MAX_VALUE), value, from));
			}
		});
	}

	private void add(FeedData.Entry entry)
	{
		if (collector.getMemberId() == null)
		{
			return;
		}
		FeedData feed = collector.shared(Section.FEED, FeedData.class);
		if (feed == null)
		{
			feed = new FeedData();
		}
		feed.add(entry);
		collector.share(Section.FEED, feed);
	}
}
