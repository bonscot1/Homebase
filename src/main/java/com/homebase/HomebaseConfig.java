package com.homebase;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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
		description = "Optional. Group Ironman groups find each other by the group's name, which is public. Only your group's members "
			+ "are let in, but a modified client could pretend to be one. To rule that out, everyone in your group enters the same word here.",
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

	@ConfigItem(
		keyName = "holdItems",
		name = "Hold items in group storage",
		description = "Hide Withdraw options that would take items held back for a bounty or for another member's request. "
			+ "Turn this off to only show the padlocks.",
		position = 6
	)
	default boolean holdItems()
	{
		return true;
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
		name = "Your colour",
		description = "The colour of your marked tiles, pings and highlights. Everyone in your group sees them in this colour.",
		section = mapSection,
		position = 23
	)
	default Color tileColor()
	{
		return new Color(0xFF9800);
	}

	@ConfigItem(
		keyName = "tileNames",
		name = "Names on tiles and highlights",
		description = "Show who marked each tile or highlighted each monster",
		section = mapSection,
		position = 24
	)
	default boolean tileNames()
	{
		return true;
	}

	@ConfigSection(
		name = "Pings and highlights",
		description = "Point things out to your group for a few seconds, or highlight a monster for everyone",
		position = 30
	)
	String pingSection = "pings";

	enum PingButton
	{
		MIDDLE_CLICK("Middle-click"),
		KEY_AND_CLICK("Ping key + left-click");

		private final String label;

		PingButton(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "pings",
		name = "Pings",
		description = "Middle-click the ground or a monster to point it out to your group for a few seconds",
		section = pingSection,
		position = 31
	)
	default boolean pings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pingButton",
		name = "Ping with",
		description = "Middle-click (dragging with the middle button still turns the camera), or hold the ping key and left-click",
		section = pingSection,
		position = 32
	)
	default PingButton pingButton()
	{
		return PingButton.MIDDLE_CLICK;
	}

	@ConfigItem(
		keyName = "pingKey",
		name = "Ping key",
		description = "The key to hold while left-clicking, when Ping with is set to use it. Alt moves RuneLite's overlays, "
			+ "Ctrl + click is the game's run toggle and Shift is used by RuneLite's Party plugin pings.",
		section = pingSection,
		position = 33
	)
	default Keybind pingKey()
	{
		return Keybind.CTRL;
	}

	@Range(min = 1, max = 10)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "pingSeconds",
		name = "Ping length",
		description = "How long a ping stays on screen",
		section = pingSection,
		position = 34
	)
	default int pingSeconds()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "pingSound",
		name = "Ping sound",
		description = "Play a sound when someone in your group pings",
		section = pingSection,
		position = 35
	)
	default boolean pingSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcHighlights",
		name = "Group highlights",
		description = "Shift + right-click a monster and choose Group highlight to outline it for the whole group until it dies",
		section = pingSection,
		position = 36
	)
	default boolean npcHighlights()
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
