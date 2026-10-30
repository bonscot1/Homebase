package com.homebase;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(HomebaseConfig.GROUP)
public interface HomebaseConfig extends Config
{
	String GROUP = "groupironmanhomebase";
	String PASSPHRASE_KEY = "groupPassphrase";

	@ConfigItem(
		keyName = PASSPHRASE_KEY,
		name = "Group passphrase",
		description = "Everyone in your group enters the same passphrase. Anyone who has it can see your group's data, so only share it with your group.",
		secret = true,
		position = 0
	)
	default String groupPassphrase()
	{
		return "";
	}

	@ConfigItem(
		keyName = "autoJoin",
		name = "Join group automatically",
		description = "Join your group's party when RuneLite starts, and rejoin it after leaving another party",
		position = 1
	)
	default boolean autoJoin()
	{
		return true;
	}

	@ConfigItem(
		keyName = "requestMessages",
		name = "Request chat messages",
		description = "Show a chat message when someone in your group makes, takes on or finishes a request",
		position = 2
	)
	default boolean requestMessages()
	{
		return true;
	}
}
