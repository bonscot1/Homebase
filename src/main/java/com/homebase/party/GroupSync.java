package com.homebase.party;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.homebase.HomebaseConfig;
import com.homebase.sync.SyncEngine;
import com.homebase.sync.SyncRecord;
import com.homebase.sync.WireCodec;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;

/**
 * Moves group data over RuneLite's party service, so nobody has to host a server.
 * <p>
 * The group passphrase is used as the party passphrase. When anyone joins, everyone online sends a
 * summary of what they hold and fills in whatever the others are missing.
 */
@Slf4j
@Singleton
public class GroupSync
{
	public enum Status
	{
		/**
		 * No passphrase set yet.
		 */
		NO_GROUP,
		/**
		 * Not in any party.
		 */
		DISCONNECTED,
		/**
		 * In someone else's party, so syncing is paused.
		 */
		OTHER_PARTY,
		CONNECTED,
	}

	static final int PROTOCOL = 1;

	/**
	 * Rough cap on the uncompressed size of one batch of records.
	 */
	private static final int RECORD_BUDGET = 48_000;
	private static final long SUMMARY_INTERVAL_MS = 3_000;

	private final PartyService partyService;
	private final WSClient wsClient;
	private final ClientThread clientThread;
	private final HomebaseConfig config;
	private final Gson gson;

	private final List<Runnable> statusListeners = new CopyOnWriteArrayList<>();

	private volatile String passphrase;
	private volatile SyncEngine engine;
	private volatile boolean summaryWanted;
	private long lastSummaryAt;
	private boolean inOtherParty;

	@Inject
	private GroupSync(PartyService partyService, WSClient wsClient, ClientThread clientThread, HomebaseConfig config, Gson gson)
	{
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.clientThread = clientThread;
		this.config = config;
		this.gson = gson;
	}

	public void start()
	{
		wsClient.registerMessage(HomebaseSyncMessage.class);
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
	 */
	public void setGroup(String passphrase, SyncEngine engine)
	{
		this.passphrase = Strings.emptyToNull(passphrase);
		this.engine = engine;
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
			return Status.CONNECTED;
		}
		return partyService.isInParty() ? Status.OTHER_PARTY : Status.DISCONNECTED;
	}

	public boolean isGroupParty()
	{
		String p = passphrase;
		return p != null && p.equals(partyService.getPartyPassphrase());
	}

	public void joinGroupParty()
	{
		String p = passphrase;
		if (p != null && !isGroupParty())
		{
			clientThread.invokeLater(() -> partyService.changeParty(p));
		}
	}

	public void leaveGroupParty()
	{
		if (isGroupParty())
		{
			partyService.changeParty(null);
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		String joined = event.getPassphrase();
		if (joined == null && inOtherParty && config.autoJoin())
		{
			// they finished with someone else's party, so come home
			joinGroupParty();
		}
		inOtherParty = joined != null && !joined.equals(passphrase);
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
		SyncEngine engine = this.engine;
		if (engine == null || !isGroupParty())
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
