package com.homebase.party;

import com.homebase.HomebaseConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongPredicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
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
 * <p>
 * The same message carries where the member is. RuneLite's party only sends a position after the
 * player has moved, and what it sends is where they were at its last update, so someone who logs in
 * and stands still has no position at all and someone who has stopped walking shows a little way back.
 */
@Singleton
public class Presence
{
	static final long HEARTBEAT_MS = 30_000;

	/**
	 * A little over two heartbeats, so one lost message doesn't drop anyone.
	 */
	static final long TIMEOUT_MS = 75_000;

	/**
	 * The least time between messages while a member is on the move.
	 */
	static final long POSITION_GAP_MS = 3_000;

	private final Client client;
	private final PartyService party;
	private final WSClient wsClient;
	private final HomebaseConfig config;

	private final Map<Long, Long> lastHeard = new ConcurrentHashMap<>();

	/**
	 * Members who said they were going offline, until they're heard from again.
	 */
	private final Set<Long> gone = ConcurrentHashMap.newKeySet();

	/**
	 * Where members last said they were.
	 */
	private final Map<Long, WorldPoint> positions = new ConcurrentHashMap<>();

	/**
	 * Members who have chosen not to share their position.
	 */
	private final Set<Long> hiddenPositions = ConcurrentHashMap.newKeySet();
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
	private volatile long lastSent;
	private volatile WorldPoint lastSentPosition;
	private Set<Long> lastOnline = new HashSet<>();

	/**
	 * Which party members count as part of the group; anyone else is never shown as online.
	 */
	private volatile LongPredicate trusted = memberId -> true;

	@Inject
	Presence(Client client, PartyService party, WSClient wsClient, HomebaseConfig config)
	{
		this.client = client;
		this.party = party;
		this.wsClient = wsClient;
		this.config = config;
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
		positions.clear();
		hiddenPositions.clear();
		listeners.clear();
		lastSent = 0;
		lastSentPosition = null;
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
			notePosition(message.getMemberId(), message.getCoord());
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
		// and send ours soon, so they can see where we are even if we're standing still
		lastSent = 0;
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		lastHeard.remove(event.getMemberId());
		gone.remove(event.getMemberId());
		positions.remove(event.getMemberId());
		hiddenPositions.remove(event.getMemberId());
	}

	private void notePosition(long memberId, Integer coord)
	{
		if (coord == null)
		{
			return;
		}
		if (coord == HomebasePresence.NO_POSITION)
		{
			hiddenPositions.add(memberId);
			positions.remove(memberId);
		}
		else
		{
			hiddenPositions.remove(memberId);
			positions.put(memberId, WorldPoint.fromCoord(coord));
		}
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
		if (sharing && client.getGameState() == GameState.LOGGED_IN)
		{
			WorldPoint here = config.sharePosition() ? localPosition() : null;
			if (isSendDue(now - lastSent, !Objects.equals(here, lastSentPosition)))
			{
				send(true, here == null ? Integer.valueOf(HomebasePresence.NO_POSITION) : Integer.valueOf(HomebasePresence.packCoord(here)));
				lastSentPosition = here;
			}
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
		send(false, null);
		lastSentPosition = null;
		// so the next login says "still here" straight away
		lastSent = 0;
	}

	/**
	 * @return where the member is: what they told us if they have, or failing that what RuneLite's
	 * party last sent, which is all a member on an older version of the plugin shares. Null if neither
	 * has said yet.
	 */
	public WorldPoint positionOf(PartyMember member)
	{
		return position(member.getMemberId(), member.getCoord());
	}

	WorldPoint position(long memberId, WorldPoint fromParty)
	{
		if (hiddenPositions.contains(memberId))
		{
			return null;
		}
		WorldPoint told = positions.get(memberId);
		return told != null ? told : fromParty;
	}

	/**
	 * @param sinceLastSent how long ago we last sent anything
	 * @param moved whether we've moved since then
	 */
	static boolean isSendDue(long sinceLastSent, boolean moved)
	{
		return sinceLastSent >= HEARTBEAT_MS || (moved && sinceLastSent >= POSITION_GAP_MS);
	}

	/**
	 * @return where we are, or null if the game doesn't have us yet. An instance such as a raid is given
	 * as the real place it's a copy of, and a boat's deck, which has coordinates of its own, as where
	 * the boat is. Call it on the client thread.
	 */
	public WorldPoint localPosition()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		LocalPoint where = player.getLocalLocation();
		WorldView view = player.getWorldView();
		if (view != null && !view.isTopLevel())
		{
			WorldEntity boat = client.getTopLevelWorldView().worldEntities().byIndex(view.getId());
			if (boat != null)
			{
				where = boat.getLocalLocation();
			}
		}
		return where == null ? null : WorldPoint.fromLocalInstance(client, where);
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

	private void send(boolean online, Integer coord)
	{
		PartyMember local = party.getLocalMember();
		if (local == null)
		{
			return;
		}
		HomebasePresence message = new HomebasePresence(online, coord);
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
