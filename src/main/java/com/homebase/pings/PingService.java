package com.homebase.pings;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.party.GroupSync;
import com.homebase.party.HomebasePing;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
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
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.util.HotkeyListener;

/**
 * Middle-click the ground or a monster, or hold the ping key and left-click it, and everyone in the
 * group sees it marked in your colour for a few seconds. A middle-click does nothing in game; a ping
 * key click is swallowed so nothing happens, the same way RuneLite's Party plugin handles its pings.
 */
@Singleton
public class PingService
{
	/**
	 * Clicks closer together than this send one ping.
	 */
	private static final long SEND_GAP_MS = 400;

	/**
	 * A middle press that moves further than this, or is held longer, is turning the camera.
	 */
	private static final int CLICK_SLOP_PX = 5;
	private static final long CLICK_MAX_MS = 500;

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

	private final MouseManager mouseManager;
	private final List<Ping> pings = new ArrayList<>();
	private final HotkeyListener hotkey;
	private final MouseAdapter middleClick;
	private volatile boolean keyHeld;
	private java.awt.Point pressedAt;
	private long pressedTime;
	private long lastSent;

	@Inject
	private PingService(Client client, ClientThread clientThread, HomebaseConfig config, PartyService partyService,
		WSClient wsClient, KeyManager keyManager, MouseManager mouseManager, GroupSync groupSync, GroupIndex index)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.keyManager = keyManager;
		this.mouseManager = mouseManager;
		this.groupSync = groupSync;
		this.index = index;
		middleClick = new MouseAdapter()
		{
			@Override
			public MouseEvent mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isMiddleMouseButton(e))
				{
					pressedAt = e.getPoint();
					pressedTime = System.currentTimeMillis();
				}
				return e;
			}

			@Override
			public MouseEvent mouseReleased(MouseEvent e)
			{
				// the event is passed on untouched either way, so dragging still turns the camera
				if (SwingUtilities.isMiddleMouseButton(e) && pressedAt != null)
				{
					boolean click = pressedAt.distance(e.getPoint()) <= CLICK_SLOP_PX
						&& System.currentTimeMillis() - pressedTime <= CLICK_MAX_MS;
					pressedAt = null;
					if (click && config.pingButton() == HomebaseConfig.PingButton.MIDDLE_CLICK)
					{
						clientThread.invoke(PingService.this::pingHovered);
					}
				}
				return e;
			}
		};
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
		mouseManager.registerMouseListener(middleClick);
	}

	public void stop()
	{
		mouseManager.unregisterMouseListener(middleClick);
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
		if (!keyHeld || config.pingButton() != HomebaseConfig.PingButton.KEY_AND_CLICK || client.isMenuOpen())
		{
			return;
		}
		if (ping(event.getMenuEntry().getNpc()))
		{
			event.consume();
		}
	}

	/**
	 * Ping whatever the mouse is over: the monster whose option is on top of the menu, or else the
	 * tile under the cursor. Runs on the client thread.
	 */
	private void pingHovered()
	{
		if (client.isMenuOpen())
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		// the last entry is the one a left-click would choose, which belongs to what's on top
		ping(entries.length == 0 ? null : entries[entries.length - 1].getNpc());
	}

	/**
	 * @return true if the click was on the game world, so it was a ping (sent unless it came too soon
	 * after the last one)
	 */
	private boolean ping(NPC npc)
	{
		PartyMember local = partyService.getLocalMember();
		WorldView view = client.getTopLevelWorldView();
		if (!config.pings() || !groupSync.isGroupParty() || local == null || view == null)
		{
			return false;
		}

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
				return false;
			}
			Tile tile = view.getSelectedSceneTile();
			where = tile == null ? null : tile.getLocalLocation();
		}
		if (where == null)
		{
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - lastSent < SEND_GAP_MS)
		{
			return true;
		}
		lastSent = now;
		WorldPoint point = WorldPoint.fromLocalInstance(client, where);
		HomebasePing ping = new HomebasePing(client.getWorld(), point.getX(), point.getY(), point.getPlane(),
			npc == null ? -1 : npc.getIndex(), npc == null ? -1 : npc.getId(), config.tileColor().getRGB() & 0xFFFFFF);
		ping.setMemberId(local.getMemberId());
		// the party sends it back to us too, which is how our own ping shows up
		partyService.send(ping);
		return true;
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
