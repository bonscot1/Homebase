package com.homebase.map;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.overlay.GroupHudOverlay;
import com.homebase.party.Presence;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;

/**
 * Click a member in the group HUD to find them on the world map. A plugin isn't allowed to open the
 * map itself, so the click is remembered and the map goes to them when it's next opened.
 */
@Singleton
public class FindOnMap
{
	/**
	 * How long a click is remembered, so opening the map much later doesn't jump somewhere unexpected.
	 */
	static final long REMEMBER_MS = 60_000;

	private static final int MAX_NAME = 20;

	private final Client client;
	private final ClientThread clientThread;
	private final HomebaseConfig config;
	private final MouseManager mouseManager;
	private final PartyService partyService;
	private final Presence presence;
	private final GroupHudOverlay hud;
	private final GroupWorldMap groupWorldMap;

	private final MouseAdapter clicks = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			swallowing = false;
			// Alt is how overlays are moved, so it's left alone
			if (!SwingUtilities.isLeftMouseButton(e) || e.isAltDown() || !config.hudClickToMap())
			{
				return e;
			}
			Long memberId = hud.memberAt(e.getPoint());
			if (memberId == null)
			{
				return e;
			}
			swallowing = true;
			e.consume();
			clientThread.invoke(() -> clicked(memberId));
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e)
		{
			if (swallowing)
			{
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mouseClicked(MouseEvent e)
		{
			if (swallowing)
			{
				swallowing = false;
				e.consume();
			}
			return e;
		}
	};

	private Consumer<String> messages;
	private volatile boolean swallowing;
	private long wantedMember;
	private long wantedAt;
	private boolean wanted;

	@Inject
	private FindOnMap(Client client, ClientThread clientThread, HomebaseConfig config, MouseManager mouseManager,
		PartyService partyService, Presence presence, GroupHudOverlay hud, GroupWorldMap groupWorldMap)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.mouseManager = mouseManager;
		this.partyService = partyService;
		this.presence = presence;
		this.hud = hud;
		this.groupWorldMap = groupWorldMap;
	}

	/**
	 * @param messages shows a line in the chat box, called on the client thread
	 */
	public void start(Consumer<String> messages)
	{
		this.messages = messages;
		mouseManager.registerMouseListener(clicks);
	}

	public void stop()
	{
		mouseManager.unregisterMouseListener(clicks);
		wanted = false;
	}

	private void clicked(long memberId)
	{
		PartyMember member = partyService.getMemberById(memberId);
		String name = member == null ? null : Clean.text(member.getDisplayName(), MAX_NAME);
		if (name == null)
		{
			return;
		}
		if (presence.positionOf(member) == null)
		{
			wanted = false;
			messages.accept(name + " isn't sharing where they are.");
			return;
		}
		wanted = true;
		wantedMember = memberId;
		wantedAt = System.currentTimeMillis();
		messages.accept("Open your world map to see where " + name + " is.");
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.WORLDMAP && wanted)
		{
			// the map centres on the player as it opens, so this waits for that to finish
			clientThread.invokeLater(this::goToMember);
		}
	}

	private void goToMember()
	{
		boolean due = isStillWanted(wanted, wantedAt, System.currentTimeMillis());
		wanted = false;
		PartyMember member = due ? partyService.getMemberById(wantedMember) : null;
		WorldPoint where = member == null ? null : presence.positionOf(member);
		WorldPoint point = where == null ? null : groupWorldMap.pointOnMap(wantedMember, where);
		WorldMap map = client.getWorldMap();
		if (point != null && map != null)
		{
			map.setWorldMapPositionTarget(point);
		}
	}

	static boolean isStillWanted(boolean wanted, long wantedAt, long now)
	{
		return wanted && now - wantedAt < REMEMBER_MS;
	}
}
