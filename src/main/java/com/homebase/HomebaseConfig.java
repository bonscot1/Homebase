package com.homebase;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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
		description = "Join your group's party when RuneLite starts, rejoin it after leaving another party, and rejoin at login if RuneLite left it while you were logged out",
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

	@ConfigItem(
		keyName = "goalMessages",
		name = "Goal chat messages",
		description = "Show a chat message when someone in your group reaches a group goal",
		position = 5
	)
	default boolean goalMessages()
	{
		return true;
	}

	enum HudSize
	{
		SMALL,
		MEDIUM,
		LARGE
	}

	@ConfigSection(
		name = "Group HUD",
		description = "An on-screen box showing each online group member's stats and where they are",
		position = 10
	)
	String hudSection = "hud";

	@ConfigItem(
		keyName = "hudEnabled",
		name = "Show group HUD",
		description = "Show each online group member's hitpoints, prayer, run energy, level and position on screen",
		section = hudSection,
		position = 11
	)
	default boolean hudEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudIncludeSelf",
		name = "Include yourself",
		description = "Show your own line in the group HUD too",
		section = hudSection,
		position = 12
	)
	default boolean hudIncludeSelf()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudHitpoints",
		name = "Hitpoints",
		description = "Show hitpoints in the group HUD",
		section = hudSection,
		position = 13
	)
	default boolean hudHitpoints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudPrayer",
		name = "Prayer",
		description = "Show prayer points in the group HUD",
		section = hudSection,
		position = 14
	)
	default boolean hudPrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudRunEnergy",
		name = "Run energy",
		description = "Show run energy in the group HUD",
		section = hudSection,
		position = 15
	)
	default boolean hudRunEnergy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudLevel",
		name = "Combat level",
		description = "Show combat level in the group HUD",
		section = hudSection,
		position = 16
	)
	default boolean hudLevel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudPosition",
		name = "Position",
		description = "Show which area each member is in, such as Varrock",
		section = hudSection,
		position = 17
	)
	default boolean hudPosition()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudCompact",
		name = "Compact mode",
		description = "One line per member instead of a block each",
		section = hudSection,
		position = 18
	)
	default boolean hudCompact()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hudSize",
		name = "Size",
		description = "How big the group HUD is",
		section = hudSection,
		position = 19
	)
	default HudSize hudSize()
	{
		return HudSize.MEDIUM;
	}

	@ConfigSection(
		name = "Map and tiles",
		description = "Group members on the world map, and tiles marked for the whole group",
		position = 20
	)
	String mapSection = "map";

	@ConfigItem(
		keyName = "worldMap",
		name = "Show group on world map",
		description = "Show where online group members are on the world map",
		section = mapSection,
		position = 21
	)
	default boolean worldMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "groupTiles",
		name = "Group tile markers",
		description = "Shift + right-click a tile and choose Group mark to mark it for your whole group",
		section = mapSection,
		position = 22
	)
	default boolean groupTiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tileColor",
		name = "Your tile colour",
		description = "The colour of the tiles you mark. Everyone in your group sees your tiles in this colour.",
		section = mapSection,
		position = 23
	)
	default Color tileColor()
	{
		return new Color(0xFF9800);
	}

	@ConfigItem(
		keyName = "tileNames",
		name = "Names on tiles",
		description = "Show who marked each tile",
		section = mapSection,
		position = 24
	)
	default boolean tileNames()
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
