package com.homebase.pings;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.party.GroupSync;
import com.homebase.party.HomebasePing;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.SoundEffectID;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.util.HotkeyListener;

/**
 * Hold the ping key and left-click the ground or a monster, and everyone in the group sees it marked
 * in your colour for a few seconds. The click itself is swallowed, so nothing happens in game, the
 * same way RuneLite's Party plugin handles its pings.
 */
@Singleton
public class PingService
{
	/**
	 * Clicks closer together than this send one ping.
	 */
	private static final long SEND_GAP_MS = 400;

	/**
	 * Pings kept per member, so nobody can fill the screen.
	 */
	private static final int PER_MEMBER = 3;

	private final Client client;
	private final ClientThread clientThread;
	private final HomebaseConfig config;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final KeyManager keyManager;
	private final GroupSync groupSync;
	private final GroupIndex index;

	private final List<Ping> pings = new ArrayList<>();
	private final HotkeyListener hotkey;
	private volatile boolean keyHeld;
	private long lastSent;

	@Inject
	private PingService(Client client, ClientThread clientThread, HomebaseConfig config, PartyService partyService,
		WSClient wsClient, KeyManager keyManager, GroupSync groupSync, GroupIndex index)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.keyManager = keyManager;
		this.groupSync = groupSync;
		this.index = index;
		hotkey = new HotkeyListener(config::pingKey)
		{
			@Override
			public void hotkeyPressed()
			{
				keyHeld = true;
			}

			@Override
			public void hotkeyReleased()
			{
				keyHeld = false;
			}
		};
	}

	public void start()
	{
		wsClient.registerMessage(HomebasePing.class);
		keyManager.registerKeyListener(hotkey);
	}

	public void stop()
	{
		keyManager.unregisterKeyListener(hotkey);
		wsClient.unregisterMessage(HomebasePing.class);
		keyHeld = false;
		synchronized (pings)
		{
			pings.clear();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!keyHeld || !config.pings() || client.isMenuOpen() || !groupSync.isGroupParty())
		{
			return;
		}
		PartyMember local = partyService.getLocalMember();
		WorldView view = client.getTopLevelWorldView();
		if (local == null || view == null)
		{
			return;
		}

		NPC npc = event.getMenuEntry().getNpc();
		LocalPoint where;
		if (npc != null)
		{
			where = npc.getLocalLocation();
		}
		else
		{
			// only clicks on the game world, not on the inventory or other interfaces
			if (!onScene())
			{
				return;
			}
			Tile tile = view.getSelectedSceneTile();
			where = tile == null ? null : tile.getLocalLocation();
		}
		if (where == null)
		{
			return;
		}
		event.consume();

		long now = System.currentTimeMillis();
		if (now - lastSent < SEND_GAP_MS)
		{
			return;
		}
		lastSent = now;
		WorldPoint point = WorldPoint.fromLocalInstance(client, where);
		HomebasePing ping = new HomebasePing(client.getWorld(), point.getX(), point.getY(), point.getPlane(),
			npc == null ? -1 : npc.getIndex(), npc == null ? -1 : npc.getId(), config.tileColor().getRGB() & 0xFFFFFF);
		ping.setMemberId(local.getMemberId());
		// the party sends it back to us too, which is how our own ping shows up
		partyService.send(ping);
	}

	private boolean onScene()
	{
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if ("Walk here".equals(entry.getOption()))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onHomebasePing(HomebasePing message)
	{
		if (!config.pings() || !groupSync.isGroupParty())
		{
			return;
		}
		PartyMember member = partyService.getMemberById(message.getMemberId());
		String name = member == null ? null : Clean.text(member.getDisplayName(), 20);
		// only people whose account is in the group, not anyone who learns the party's name
		if (name == null || index.memberId(name) == null)
		{
			return;
		}
		Ping ping = new Ping(message.getMemberId(), name, new Color(message.getColor() & 0xFFFFFF), message.getWorld(),
			new WorldPoint(message.getX(), message.getY(), message.getPlane()), message.getNpcIndex(), message.getNpcId(),
			System.currentTimeMillis());
		synchronized (pings)
		{
			int mine = 0;
			for (int i = pings.size() - 1; i >= 0; i--)
			{
				if (pings.get(i).getMemberId() == ping.getMemberId() && ++mine >= PER_MEMBER)
				{
					pings.remove(i);
				}
			}
			pings.add(ping);
		}
		if (config.pingSound())
		{
			clientThread.invokeLater(() -> client.playSoundEffect(SoundEffectID.SMITH_ANVIL_TINK));
		}
	}

	/**
	 * @return the pings still showing, oldest first
	 */
	public List<Ping> active()
	{
		long now = System.currentTimeMillis();
		long lifetime = lifetimeMs();
		synchronized (pings)
		{
			for (Iterator<Ping> it = pings.iterator(); it.hasNext(); )
			{
				if (now - it.next().getCreatedAt() >= lifetime)
				{
					it.remove();
				}
			}
			return new ArrayList<>(pings);
		}
	}

	public long lifetimeMs()
	{
		return Math.max(1, config.pingSeconds()) * 1000L;
	}
}
