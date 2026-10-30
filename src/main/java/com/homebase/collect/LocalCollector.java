package com.homebase.collect;

import com.google.gson.Gson;
import com.homebase.data.Ids;
import com.homebase.data.ItemsData;
import com.homebase.data.ProfileData;
import com.homebase.data.Section;
import com.homebase.data.SkillsData;
import com.homebase.sync.SyncEngine;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Captures the local player's data and publishes it to the group store.
 * <p>
 * Changes are captured as they happen but published at most once per section interval, so a burst of
 * changes, like reorganising the bank, becomes one update.
 */
@Slf4j
@Singleton
public class LocalCollector
{
	/**
	 * Ticks to wait after login before reading the account, so names and containers are loaded.
	 */
	private static final int LOGIN_DELAY_TICKS = 2;

	private final Client client;
	private final Gson gson;

	private volatile SyncEngine engine;

	/**
	 * Hashed account id of the logged in player, or null when logged out.
	 */
	@Getter
	private volatile String memberId;

	private ProfileData profile;
	private int loginTicks = -1;
	private final Map<Section, Object> pending = new EnumMap<>(Section.class);
	private final Map<Section, Long> lastPublished = new EnumMap<>(Section.class);

	@Inject
	private LocalCollector(Client client, Gson gson)
	{
		this.client = client;
		this.gson = gson;
	}

	public void setEngine(SyncEngine engine)
	{
		this.engine = engine;
		lastPublished.clear();
		if (engine != null && memberId != null)
		{
			// switched group while logged in, so give the new group everything
			captureAll();
		}
	}

	public void start()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loginTicks = 0;
		}
	}

	public void stop()
	{
		engine = null;
		memberId = null;
		profile = null;
		loginTicks = -1;
		pending.clear();
		lastPublished.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				if (memberId == null)
				{
					loginTicks = 0;
				}
				else
				{
					// back from a world hop or loading screen
					capture(Section.PROFILE, readProfile());
				}
				break;
			case HOPPING:
				flush(true);
				break;
			case LOGIN_SCREEN:
				if (memberId != null && profile != null)
				{
					// world 0 marks them logged out, and the record's version records when
					capture(Section.PROFILE, new ProfileData(profile.getName(), 0, profile.getGroupName()));
				}
				flush(true);
				memberId = null;
				profile = null;
				loginTicks = -1;
				pending.clear();
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (loginTicks >= 0 && ++loginTicks >= LOGIN_DELAY_TICKS)
		{
			onLoggedIn();
		}
		if (memberId != null)
		{
			flush(false);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (memberId != null)
		{
			capture(Section.SKILLS, readSkills());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		Section section = sectionFor(event.getContainerId());
		if (section != null)
		{
			capture(section, ItemsData.of(event.getItemContainer()));
		}
	}

	private void onLoggedIn()
	{
		long accountHash = client.getAccountHash();
		Player player = client.getLocalPlayer();
		if (accountHash == -1 || player == null || player.getName() == null)
		{
			// not ready yet, try again next tick
			loginTicks = 0;
			return;
		}
		loginTicks = -1;
		memberId = Ids.member(accountHash);
		log.debug("Logged in as member {}", memberId);
		captureAll();
	}

	private void captureAll()
	{
		capture(Section.PROFILE, readProfile());
		capture(Section.SKILLS, readSkills());
		capture(Section.INVENTORY, ItemsData.of(client.getItemContainer(InventoryID.INV)));
		capture(Section.EQUIPMENT, ItemsData.of(client.getItemContainer(InventoryID.WORN)));
		flush(true);
	}

	private void capture(Section section, Object data)
	{
		if (data != null)
		{
			pending.put(section, data);
		}
	}

	private void flush(boolean force)
	{
		SyncEngine engine = this.engine;
		String memberId = this.memberId;
		if (engine == null || memberId == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Section, Object>> it = pending.entrySet().iterator(); it.hasNext(); )
		{
			Map.Entry<Section, Object> entry = it.next();
			Section section = entry.getKey();
			Long last = lastPublished.get(section);
			if (!force && last != null && now - last < section.getMinIntervalMs())
			{
				continue;
			}
			engine.publish(section.keyFor(memberId), memberId, gson.toJsonTree(entry.getValue()));
			lastPublished.put(section, now);
			it.remove();
		}
	}

	private ProfileData readProfile()
	{
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null)
		{
			return null;
		}
		ClanChannel gim = client.getClanChannel(ClanID.GROUP_IRONMAN);
		profile = new ProfileData(player.getName(), client.getWorld(), gim == null ? null : gim.getName());
		return profile;
	}

	private SkillsData readSkills()
	{
		SkillsData skills = new SkillsData();
		for (Skill skill : Skill.values())
		{
			skills.getXp().put(skill.name(), client.getSkillExperience(skill));
		}
		return skills;
	}

	private static Section sectionFor(int containerId)
	{
		switch (containerId)
		{
			case InventoryID.INV:
				return Section.INVENTORY;
			case InventoryID.WORN:
				return Section.EQUIPMENT;
			case InventoryID.BANK:
				return Section.BANK;
			case InventoryID.SEED_VAULT:
				return Section.SEED_VAULT;
			case InventoryID.LOOTING_BAG:
				return Section.LOOTING_BAG;
			case InventoryID.INV_GROUP_TEMP:
				return Section.GROUP_STORAGE;
			default:
				return null;
		}
	}
}
