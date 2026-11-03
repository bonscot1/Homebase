package com.homebase.party;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongPredicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;

/**
 * Who in the party is actually playing. RuneLite's party server doesn't reliably tell everyone when a
 * member leaves, so a member who logged out could stay "online" for ages. Instead each member says
 * when they're going offline, and sends a small "still here" every so often while they play; anyone
 * not heard from for a while counts as offline, which also covers crashes and closed windows.
 */
@Singleton
public class Presence
{
	static final long HEARTBEAT_MS = 30_000;

	/**
	 * A little over two heartbeats, so one lost message doesn't drop anyone.
	 */
	static final long TIMEOUT_MS = 75_000;

	private final Client client;
	private final PartyService party;
	private final WSClient wsClient;

	private final Map<Long, Long> lastHeard = new ConcurrentHashMap<>();

	/**
	 * Members who said they were going offline, until they're heard from again.
	 */
	private final Set<Long> gone = ConcurrentHashMap.newKeySet();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private long lastSent;
	private Set<Long> lastOnline = new HashSet<>();

	/**
	 * Which party members count as part of the group; anyone else is never shown as online.
	 */
	private volatile LongPredicate trusted = memberId -> true;

	@Inject
	Presence(Client client, PartyService party, WSClient wsClient)
	{
		this.client = client;
		this.party = party;
		this.wsClient = wsClient;
	}

	public void start()
	{
		wsClient.registerMessage(HomebasePresence.class);
	}

	public void stop()
	{
		wsClient.unregisterMessage(HomebasePresence.class);
		lastHeard.clear();
		gone.clear();
		listeners.clear();
		lastSent = 0;
	}

	/**
	 * Called when who's online may have changed, from any thread.
	 */
	public void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public void setTrusted(LongPredicate trusted)
	{
		this.trusted = trusted;
	}

	/**
	 * A member was just heard from, by any message.
	 */
	public void heard(long memberId)
	{
		lastHeard.put(memberId, System.currentTimeMillis());
		gone.remove(memberId);
	}

	@Subscribe
	public void onHomebasePresence(HomebasePresence message)
	{
		if (message.isOnline())
		{
			heard(message.getMemberId());
		}
		else
		{
			gone.add(message.getMemberId());
			lastHeard.remove(message.getMemberId());
			notifyListeners();
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		// give them until their first "still here" arrives
		heard(event.getMemberId());
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		lastHeard.remove(event.getMemberId());
		gone.remove(event.getMemberId());
	}

	/**
	 * Send "still here" when it's due, and tell listeners when someone has gone quiet. Runs about once
	 * a second on the client thread.
	 *
	 * @param sharing whether we're in the group's party and it's safe to share with everyone in it
	 */
	public void tick(boolean sharing)
	{
		long now = System.currentTimeMillis();
		if (sharing && client.getGameState() == GameState.LOGGED_IN && now - lastSent >= HEARTBEAT_MS)
		{
			send(true);
		}
		Set<Long> online = new HashSet<>();
		if (sharing)
		{
			for (PartyMember member : party.getMembers())
			{
				if (isOnline(member))
				{
					online.add(member.getMemberId());
				}
			}
		}
		if (!online.equals(lastOnline))
		{
			lastOnline = online;
			notifyListeners();
		}
	}

	/**
	 * Tell the group we're going, just before leaving the party.
	 */
	public void sendOffline()
	{
		send(false);
		// so the next login says "still here" straight away
		lastSent = 0;
	}

	/**
	 * @return true if the member is playing: RuneLite has them logged in, they haven't said they're
	 * going, and they've been heard from recently
	 */
	public boolean isOnline(PartyMember member)
	{
		if (member == null || !member.isLoggedIn())
		{
			return false;
		}
		PartyMember local = party.getLocalMember();
		if (local != null && local.getMemberId() == member.getMemberId())
		{
			return client.getGameState() == GameState.LOGGED_IN;
		}
		return trusted.test(member.getMemberId()) && isOnline(member.getMemberId(), System.currentTimeMillis());
	}

	boolean isOnline(long memberId, long now)
	{
		if (gone.contains(memberId))
		{
			return false;
		}
		Long heard = lastHeard.get(memberId);
		return heard != null && now - heard < TIMEOUT_MS;
	}

	private void send(boolean online)
	{
		PartyMember local = party.getLocalMember();
		if (local == null)
		{
			return;
		}
		HomebasePresence message = new HomebasePresence(online);
		message.setMemberId(local.getMemberId());
		party.send(message);
		lastSent = System.currentTimeMillis();
	}

	private void notifyListeners()
	{
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}
}
