package com.homebase;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(HomebaseConfig.GROUP)
public interface HomebaseConfig extends Config
{
	String GROUP = "groupironmanhomebase";
	String PASSPHRASE_KEY = "groupPassphrase";
	String GIM_SECRET_KEY = "gimSecret";
	String GIM_GROUP_KEY = "gimGroup";

	@ConfigItem(
		keyName = GIM_SECRET_KEY,
		name = "Group Ironman secret",
		description = "Optional. Group Ironman members are connected automatically using the group's name, which is public. "
			+ "To keep out anyone else who knows the name, everyone in your group enters the same secret word here.",
		secret = true,
		position = 0
	)
	default String gimSecret()
	{
		return "";
	}

	@ConfigItem(
		keyName = PASSPHRASE_KEY,
		name = "Group passphrase",
		description = "Only for groups that aren't Group Ironman groups. Everyone in the group enters the same passphrase, "
			+ "and anyone who has it can see the group's data. Setting this overrides the automatic Group Ironman group.",
		secret = true,
		position = 1
	)
	default String groupPassphrase()
	{
		return "";
	}

	@ConfigItem(
		keyName = "autoJoin",
		name = "Join group automatically",
		description = "Join your group's party when RuneLite starts, and rejoin it after leaving another party",
		position = 2
	)
	default boolean autoJoin()
	{
		return true;
	}

	@ConfigItem(
		keyName = "requestMessages",
		name = "Request chat messages",
		description = "Show a chat message when someone in your group makes, takes on or finishes a request",
		position = 3
	)
	default boolean requestMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "updateMessages",
		name = "Update chat messages",
		description = "Show a chat message summarising what someone in your group did since your last update, "
			+ "such as levels gained, quests finished and diaries completed",
		position = 4
	)
	default boolean updateMessages()
	{
		return true;
	}

	/**
	 * The Group Ironman group last seen on this computer, so the plugin can reconnect before login.
	 */
	@ConfigItem(
		keyName = GIM_GROUP_KEY,
		name = "",
		description = "",
		hidden = true
	)
	default String gimGroup()
	{
		return "";
	}
}
