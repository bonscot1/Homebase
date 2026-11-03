package com.homebase.pings;

import com.homebase.HomebaseConfig;
import com.homebase.collect.LocalCollector;
import com.homebase.data.HighlightsData;
import com.homebase.data.Section;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.Subscribe;

/**
 * Highlights monsters for the whole group. Shift + right-click a monster and choose Group highlight;
 * everyone on the same world sees it outlined in your colour until it dies or you remove it.
 */
@Singleton
public class GroupHighlights
{
	private static final String HIGHLIGHT = "Group highlight";
	private static final String UNHIGHLIGHT = "Group unhighlight";

	private final Client client;
	private final HomebaseConfig config;
	private final LocalCollector collector;

	@Inject
	private GroupHighlights(Client client, HomebaseConfig config, LocalCollector collector)
	{
		this.client = client;
		this.config = config;
		this.collector = collector;
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.npcHighlights() || event.getType() != MenuAction.EXAMINE_NPC.getId()
			|| !client.isKeyPressed(KeyCode.KC_SHIFT) || collector.getMemberId() == null)
		{
			return;
		}
		NPC npc = event.getMenuEntry().getNpc();
		if (npc == null)
		{
			return;
		}
		HighlightsData data = mine();
		boolean highlighted = data.getWorld() == client.getWorld() && data.contains(npc.getIndex(), npc.getId());
		client.getMenu().createMenuEntry(-1)
			.setOption(highlighted ? UNHIGHLIGHT : HIGHLIGHT)
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> toggle(npc));
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC) || collector.getMemberId() == null)
		{
			return;
		}
		NPC npc = (NPC) actor;
		HighlightsData data = collector.shared(Section.HIGHLIGHTS, HighlightsData.class);
		if (data != null && data.getNpcs() != null && data.remove(npc.getIndex(), npc.getId()))
		{
			collector.share(Section.HIGHLIGHTS, data);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN || collector.getMemberId() == null)
		{
			return;
		}
		// monsters on the world we left mean nothing here
		HighlightsData data = collector.shared(Section.HIGHLIGHTS, HighlightsData.class);
		if (data != null && data.getNpcs() != null && !data.getNpcs().isEmpty() && data.getWorld() != client.getWorld())
		{
			data.getNpcs().clear();
			data.setWorld(client.getWorld());
			collector.share(Section.HIGHLIGHTS, data);
		}
	}

	/**
	 * Share our highlights again with the new colour. Runs on the client thread.
	 */
	public void colorChanged()
	{
		if (collector.getMemberId() == null)
		{
			return;
		}
		HighlightsData data = collector.shared(Section.HIGHLIGHTS, HighlightsData.class);
		if (data != null)
		{
			data.setColor(config.tileColor().getRGB() & 0xFFFFFF);
			collector.share(Section.HIGHLIGHTS, data);
		}
	}

	private void toggle(NPC npc)
	{
		HighlightsData data = mine();
		if (data.getWorld() != client.getWorld())
		{
			data.getNpcs().clear();
			data.setWorld(client.getWorld());
		}
		data.setColor(config.tileColor().getRGB() & 0xFFFFFF);
		data.toggle(npc.getIndex(), npc.getId());
		collector.share(Section.HIGHLIGHTS, data);
	}

	private HighlightsData mine()
	{
		HighlightsData data = collector.shared(Section.HIGHLIGHTS, HighlightsData.class);
		if (data == null)
		{
			data = new HighlightsData();
		}
		if (data.getNpcs() == null)
		{
			data.setNpcs(new ArrayList<>());
		}
		return data;
	}
}
