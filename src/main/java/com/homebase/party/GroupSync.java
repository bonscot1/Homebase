package com.homebase.party;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import com.homebase.sync.WireCodec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.util.Text;

/**
 * Moves group data over RuneLite's party service, so nobody has to host a server.
 * <p>
 * The group passphrase is used as the party passphrase. When anyone joins, everyone online sends a
 * summary of what they hold and fills in whatever the others are missing.
 * <p>
 * The party is only ever joined when the player presses Connect: RuneLite doesn't let plugins join
 * parties by themselves. A Group Ironman group's party is named after the group, which anyone can look
 * up, so nothing is shared while anyone in the party is missing from the group's member list as the
 * game reports it, and nothing they send is used.
 */
@Slf4j
@Singleton
public class GroupSync
{
	public enum Status
	{
		/**
		 * No group yet.
		 */
		NO_GROUP,
		/**
		 * A Group Ironman group: log in on an account in it to connect.
		 */
		LOG_IN,
		/**
		 * Logged in on an account that isn't in the Group Ironman group, so it can't connect.
		 */
		NOT_IN_GROUP,
		/**
		 * Ready to connect.
		 */
		DISCONNECTED,
		/**
		 * In someone else's party, so syncing is paused.
		 */
		OTHER_PARTY,
		/**
		 * In the group's party, but someone in it isn't in the group, so nothing is shared.
		 */
		PAUSED,
		CONNECTED,
	}

	/**
	 * How long at the login screen before leaving the group's party, as RuneLite's Party plugin does.
	 */
	static final long IDLE_LEAVE_MS = 30 * 60_000L;

	/**
	 * Ticks after logging in to wait for the game to say which group the account is in.
	 */
	private static final int ROSTER_WAIT_TICKS = 10;

	static final int PROTOCOL = 1;

	/**
	 * Rough cap on the uncompressed size of one batch of records.
	 */
	private static final int RECORD_BUDGET = 48_000;
	private static final long SUMMARY_INTERVAL_MS = 3_000;

	private final Client client;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final ClientThread clientThread;
	private final Gson gson;
	private final Presence presence;

	private final List<Runnable> statusListeners = new CopyOnWriteArrayList<>();

	private volatile String passphrase;
	private volatile SyncEngine engine;
	private volatile boolean summaryWanted;
	private long lastSummaryAt;

	/**
	 * The Group Ironman group's name, or null for a passphrase group.
	 */
	private volatile String gimGroup;

	/**
	 * The group's members as the game last reported them, and whether the account logged in now is in
	 * the group. Read on the client thread, used from any thread.
	 */
	private volatile Set<String> roster = Collections.emptySet();
	private volatile boolean localInGroup;
	private long loginScreenSince;

	/**
	 * Whether the game has had time to say which group this account is in since logging in.
	 */
	private volatile boolean rosterChecked;
	private int loggedInTicks;

	@Inject
	private GroupSync(Client client, PartyService partyService, WSClient wsClient, ClientThread clientThread, Gson gson,
		Presence presence)
	{
		this.client = client;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.clientThread = clientThread;
		this.gson = gson;
		this.presence = presence;
	}

	public void start()
	{
		wsClient.registerMessage(HomebaseSyncMessage.class);
		presence.setTrusted(this::isTrusted);
	}

	public void stop()
	{
		wsClient.unregisterMessage(HomebaseSyncMessage.class);
		passphrase = null;
		engine = null;
		statusListeners.clear();
	}

	public void addStatusListener(Runnable listener)
	{
		statusListeners.add(listener);
	}

	/**
	 * Switch to a group. Pass nulls when no passphrase is set.
	 *
	 * @param gimGroup the Group Ironman group's name, or null for a passphrase group
	 */
	public void setGroup(String passphrase, SyncEngine engine, String gimGroup)
	{
		this.passphrase = Strings.emptyToNull(passphrase);
		this.engine = engine;
		if (!Objects.equals(this.gimGroup, gimGroup))
		{
			this.gimGroup = gimGroup;
			roster = Collections.emptySet();
			localInGroup = false;
		}
		summaryWanted = isGroupParty();
		notifyStatus();
	}

	public Status getStatus()
	{
		if (passphrase == null)
		{
			return Status.NO_GROUP;
		}
		if (isGroupParty())
		{
			return isShareSafe() ? Status.CONNECTED : Status.PAUSED;
		}
		if (partyService.isInParty())
		{
			return Status.OTHER_PARTY;
		}
		if (gimGroup != null && !localInGroup)
		{
			return client.getGameState() == GameState.LOGGED_IN && rosterChecked ? Status.NOT_IN_GROUP : Status.LOG_IN;
		}
		return Status.DISCONNECTED;
	}

	/**
	 * @return true if it's safe to share: a passphrase group, whose members all know the passphrase, or
	 * a Group Ironman group whose party holds only its members
	 */
	public boolean isShareSafe()
	{
		return gimGroup == null || strangers().isEmpty();
	}

	/**
	 * @return anyone in the group's party who isn't on the Group Ironman group's member list, by name
	 * ("someone" for a member whose name hasn't arrived yet); always empty for a passphrase group
	 */
	public List<String> strangers()
	{
		List<String> out = new ArrayList<>();
		if (gimGroup == null || !isGroupParty())
		{
			return out;
		}
		PartyMember local = partyService.getLocalMember();
		for (PartyMember member : partyService.getMembers())
		{
			if (local != null && local.getMemberId() == member.getMemberId())
			{
				continue;
			}
			if (!isVerified(member))
			{
				out.add(Strings.isNullOrEmpty(member.getDisplayName()) ? "someone" : member.getDisplayName());
			}
		}
		return out;
	}

	/**
	 * @return true if this party member's messages can be used: anyone in a passphrase group, or a
	 * member of the Group Ironman group
	 */
	public boolean isTrusted(long memberId)
	{
		if (gimGroup == null)
		{
			return true;
		}
		PartyMember member = partyService.getMemberById(memberId);
		return member != null && isVerified(member);
	}

	private boolean isVerified(PartyMember member)
	{
		String name = member.getDisplayName();
		return !Strings.isNullOrEmpty(name) && roster.contains(rosterKey(name));
	}

	/**
	 * Read the group's member list from the game. Runs on the client thread every tick.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		String group = gimGroup;
		if (group == null)
		{
			return;
		}
		ClanSettings settings = client.getClanSettings(ClanID.GROUP_IRONMAN);
		boolean inGroup = settings != null && settings.getName() != null
			&& rosterKey(settings.getName()).equals(rosterKey(group));
		if (inGroup)
		{
			Set<String> names = new HashSet<>();
			for (ClanMember member : settings.getMembers())
			{
				if (member != null && member.getName() != null)
				{
					names.add(rosterKey(member.getName()));
				}
			}
			if (!names.equals(roster))
			{
				roster = names;
				notifyStatus();
			}
		}
		boolean checked = settings != null || ++loggedInTicks > ROSTER_WAIT_TICKS;
		if (inGroup != localInGroup || checked != rosterChecked)
		{
			localInGroup = inGroup;
			rosterChecked = checked;
			notifyStatus();
		}
	}

	static String rosterKey(String name)
	{
		return Text.toJagexName(name).toLowerCase(Locale.ROOT);
	}

	public boolean isGroupParty()
	{
		String p = passphrase;
		return p != null && p.equals(partyService.getPartyPassphrase());
	}

	/**
	 * @return true if Connect can be pressed now: not already connected, and for a Group Ironman group,
	 * logged in on an account in it
	 */
	public boolean canConnect()
	{
		return passphrase != null && !isGroupParty() && (gimGroup == null || localInGroup);
	}

	/**
	 * Join the group's party. Only ever called when the player presses Connect.
	 */
	public void joinGroupParty()
	{
		String p = passphrase;
		if (p != null && canConnect())
		{
			loginScreenSince = 0;
			clientThread.invokeLater(() -> partyService.changeParty(p));
		}
	}

	public void leaveGroupParty()
	{
		if (isGroupParty())
		{
			// the party server doesn't reliably tell the others we left, so say so ourselves
			if (isShareSafe())
			{
				presence.sendOffline();
			}
			loginScreenSince = 0;
			partyService.changeParty(null);
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		notifyStatus();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (isGroupParty())
			{
				// stay connected, but send whatever is waiting and tell the group we've gone
				flush();
				if (isShareSafe())
				{
					presence.sendOffline();
				}
				loginScreenSince = System.currentTimeMillis();
			}
			// the next login may be a different account; the member list is kept to check the party with
			localInGroup = false;
			rosterChecked = false;
			loggedInTicks = 0;
		}
		notifyStatus();
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		if (isGroupParty())
		{
			// someone arrived, possibly us: swap summaries so everyone catches up
			summaryWanted = true;
		}
		notifyStatus();
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		notifyStatus();
	}

	@Subscribe
	public void onHomebaseSyncMessage(HomebaseSyncMessage message)
	{
		SyncEngine engine = this.engine;
		if (engine == null || !isGroupParty())
		{
			return;
		}

		if (!isTrusted(message.getMemberId()))
		{
			// someone outside the group can't change it
			return;
		}
		presence.heard(message.getMemberId());
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == message.getMemberId())
		{
			return;
		}

		if (message.getProtocol() != PROTOCOL)
		{
			log.debug("Ignoring sync message with protocol {}", message.getProtocol());
			return;
		}

		try
		{
			String json = WireCodec.unpack(message.getPayload());
			if (HomebaseSyncMessage.SUMMARY.equals(message.getType()))
			{
				Map<String, Long> summary = gson.fromJson(json, WireCodec.SUMMARY_TYPE);
				if (summary != null && engine.onSummary(summary))
				{
					summaryWanted = true;
				}
			}
			else if (HomebaseSyncMessage.RECORDS.equals(message.getType()))
			{
				List<SyncRecord> records = gson.fromJson(json, WireCodec.RECORDS_TYPE);
				if (records != null)
				{
					int accepted = engine.onRecords(records);
					log.debug("Received {} records, {} newer than ours", records.size(), accepted);
				}
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.debug("Dropping unreadable sync message", e);
		}
	}

	/**
	 * Send whatever is due. Runs on the client thread about once a second.
	 */
	public void flush()
	{
		boolean connected = isGroupParty();
		if (connected && loginScreenSince > 0 && client.getGameState() == GameState.LOGIN_SCREEN
			&& System.currentTimeMillis() - loginScreenSince >= IDLE_LEAVE_MS)
		{
			// logged out for a while: leave, so the party servers only carry people who are playing
			leaveGroupParty();
			return;
		}
		boolean safe = connected && isShareSafe();
		presence.tick(safe);
		SyncEngine engine = this.engine;
		if (engine == null || !safe)
		{
			return;
		}

		long now = System.currentTimeMillis();
		if (summaryWanted && now - lastSummaryAt >= SUMMARY_INTERVAL_MS)
		{
			summaryWanted = false;
			lastSummaryAt = now;
			send(HomebaseSyncMessage.SUMMARY, gson.toJson(engine.summary()));
		}

		List<SyncRecord> due = engine.takeDue(RECORD_BUDGET, GroupSync::estimateSize);
		if (!due.isEmpty())
		{
			send(HomebaseSyncMessage.RECORDS, gson.toJson(due));
		}
	}

	private void send(String type, String json)
	{
		partyService.send(new HomebaseSyncMessage(PROTOCOL, type, WireCodec.pack(json)));
	}

	private static int estimateSize(SyncRecord record)
	{
		return 64 + record.getKey().length() + (record.getData() == null ? 0 : record.getData().toString().length());
	}

	private void notifyStatus()
	{
		for (Runnable listener : statusListeners)
		{
			listener.run();
		}
	}
}
