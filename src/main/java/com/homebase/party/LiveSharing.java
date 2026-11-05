package com.homebase.party;

import com.homebase.HomebaseConfig;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Skill;
import net.runelite.client.party.PartyDataService;
import net.runelite.client.party.PartyDataType;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.UserSync;
import net.runelite.client.plugins.Plugin;

/**
 * Shares live details through RuneLite's party service; our own sync handles everything that persists.
 * Our name goes out once the game has shown this account is in the group, even while sharing is paused,
 * so the others can tell we belong. Position, run energy, special attack, hitpoints and prayer also wait
 * until nobody else is in the party. Runs on the client thread.
 */
@Singleton
public class LiveSharing
{
	private final GroupSync groupSync;
	private final HomebaseConfig config;
	private final PartyService partyService;
	private final PartyDataService partyDataService;

	private Plugin plugin;
	private boolean nameShared;
	private boolean positionShared;
	private boolean statsShared;

	@Inject
	private LiveSharing(GroupSync groupSync, HomebaseConfig config, PartyService partyService, PartyDataService partyDataService)
	{
		this.groupSync = groupSync;
		this.config = config;
		this.partyService = partyService;
		this.partyDataService = partyDataService;
	}

	/**
	 * @param plugin who the party service keeps these subscriptions for
	 */
	public void start(Plugin plugin)
	{
		this.plugin = plugin;
	}

	public void stop()
	{
		partyDataService.unsubscribe(plugin, PartyDataType.values());
		partyDataService.unsubscribe(plugin, Skill.values());
		nameShared = false;
		positionShared = false;
		statsShared = false;
	}

	public void update()
	{
		boolean shareName = groupSync.isGroupParty() && groupSync.isVerifiedAccount();
		boolean shareStats = shareName && groupSync.isShareSafe();
		boolean sharePosition = shareStats && config.sharePosition();
		boolean started = false;
		if (shareName != nameShared)
		{
			nameShared = shareName;
			started = shareName;
			if (shareName)
			{
				partyDataService.subscribe(plugin, PartyDataType.CHARACTER_NAME);
			}
			else
			{
				partyDataService.unsubscribe(plugin, PartyDataType.CHARACTER_NAME);
			}
		}
		if (sharePosition != positionShared)
		{
			positionShared = sharePosition;
			if (sharePosition)
			{
				partyDataService.subscribe(plugin, PartyDataType.COORD);
			}
			else
			{
				partyDataService.unsubscribe(plugin, PartyDataType.COORD);
			}
		}
		if (shareStats != statsShared)
		{
			statsShared = shareStats;
			started |= shareStats;
			if (shareStats)
			{
				partyDataService.subscribe(plugin, PartyDataType.ENERGY, PartyDataType.SPEC);
				partyDataService.subscribe(plugin, Skill.HITPOINTS, Skill.PRAYER);
			}
			else
			{
				partyDataService.unsubscribe(plugin, PartyDataType.ENERGY, PartyDataType.SPEC);
				partyDataService.unsubscribe(plugin, Skill.HITPOINTS, Skill.PRAYER);
			}
		}
		PartyMember local = partyService.getLocalMember();
		if (started && local != null)
		{
			// the party only sends these when they change, so ask everyone, us included, to send theirs now
			UserSync sync = new UserSync();
			sync.setMemberId(local.getMemberId());
			partyService.send(sync);
		}
	}
}
