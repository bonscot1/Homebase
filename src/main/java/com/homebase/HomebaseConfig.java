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
	String TILE_COLOR_KEY = "tileColor";
	String SHARE_KEY_PREFIX = "share";

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
		position = 101
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
		position = 102
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
		position = 103
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
		position = 104
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
		position = 105
	)
	default boolean hudRunEnergy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudSpecEnergy",
		name = "Special attack",
		description = "Show special attack energy in the group HUD",
		section = hudSection,
		position = 106
	)
	default boolean hudSpecEnergy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hudLevel",
		name = "Combat level",
		description = "Show combat level in the group HUD",
		section = hudSection,
		position = 107
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
		position = 108
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
		position = 111
	)
	default boolean hudCompact()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hudSameWorld",
		name = "Only your world",
		description = "Only show group members on the same world as you",
		section = hudSection,
		position = 109
	)
	default boolean hudSameWorld()
	{
		return false;
	}

	enum HudOrder
	{
		NAME("By name"),
		LOWEST_HITPOINTS("Lowest hitpoints first");

		private final String label;

		HudOrder(String label)
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
		keyName = "hudOrder",
		name = "Order",
		description = "The order group members are listed in",
		section = hudSection,
		position = 110
	)
	default HudOrder hudOrder()
	{
		return HudOrder.NAME;
	}

	@ConfigItem(
		keyName = "hudClickToMap",
		name = "Click to find on map",
		description = "Click a member in the group HUD, then open your world map, and the map goes straight to them. "
			+ "Clicks on the HUD don't reach the game behind it while this is on.",
		section = hudSection,
		position = 113
	)
	default boolean hudClickToMap()
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
		keyName = "hudSize",
		name = "Size",
		description = "How big the group HUD is",
		section = hudSection,
		position = 112
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
		position = 201
	)
	default boolean worldMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mapNames",
		name = "Names on map markers",
		description = "Show each member's name above their arrow and pin on the world map",
		section = mapSection,
		position = 205
	)
	default boolean mapNames()
	{
		return true;
	}

	enum MarkerSize
	{
		SMALL(0.75),
		MEDIUM(1),
		LARGE(1.5);

		private final double scale;

		MarkerSize(double scale)
		{
			this.scale = scale;
		}

		public double getScale()
		{
			return scale;
		}
	}

	@ConfigItem(
		keyName = "mapMarkerSize",
		name = "Map marker size",
		description = "How big arrows and pins are on the world map",
		section = mapSection,
		position = 206
	)
	default MarkerSize mapMarkerSize()
	{
		return MarkerSize.MEDIUM;
	}

	@ConfigItem(
		keyName = "mapEdge",
		name = "Keep off-screen members at the edge",
		description = "When someone is off the part of the map you're looking at, keep their marker at the edge, pointing towards them",
		section = mapSection,
		position = 207
	)
	default boolean mapEdge()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mapPins",
		name = "Map pins",
		description = "Right-click the world map and choose Pin for group to show everyone a spot. You have one pin at a time.",
		section = mapSection,
		position = 208
	)
	default boolean mapPins()
	{
		return true;
	}

	@Range(min = 1, max = 168)
	@ConfigItem(
		keyName = "mapPinHours",
		name = "Pins last (hours)",
		description = "How long a pin stays on the map, unless the person who placed it removes it sooner",
		section = mapSection,
		position = 209
	)
	default int mapPinHours()
	{
		return 24;
	}

	@ConfigItem(
		keyName = "groupTiles",
		name = "Group tile markers",
		description = "Shift + right-click a tile and choose Group mark to mark it for your whole group",
		section = mapSection,
		position = 202
	)
	default boolean groupTiles()
	{
		return true;
	}

	@ConfigItem(
		keyName = TILE_COLOR_KEY,
		name = "Your colour",
		description = "The colour of your marked tiles, pings and highlights. Everyone in your group sees them in this colour.",
		section = mapSection,
		position = 203
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
		position = 204
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
		position = 301
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
		position = 302
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
		position = 303
	)
	default Keybind pingKey()
	{
		return Keybind.NOT_SET;
	}

	@Range(min = 1, max = 10)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "pingSeconds",
		name = "Ping length",
		description = "How long a ping stays on screen",
		section = pingSection,
		position = 304
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
		position = 305
	)
	default boolean pingSound()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pingMinimap",
		name = "Pings on the minimap",
		description = "Show pings on the minimap too, so one behind the camera still gets noticed",
		section = pingSection,
		position = 308
	)
	default boolean pingMinimap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcHighlights",
		name = "Group highlights",
		description = "Shift + right-click a monster and choose Group highlight to outline it for the whole group until it dies",
		section = pingSection,
		position = 306
	)
	default boolean npcHighlights()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepHighlights",
		name = "Keep highlights after death",
		description = "Keep your group highlights when the monster dies, so it's highlighted again when it respawns. "
			+ "Shift + right-click it and choose Group unhighlight to remove one.",
		section = pingSection,
		position = 307
	)
	default boolean keepHighlights()
	{
		return false;
	}

	@ConfigSection(
		name = "Sidebar panel",
		description = "What the sidebar panel shows",
		position = 40
	)
	String panelSection = "panel";

	@ConfigItem(
		keyName = "itemSearch",
		name = "Item search box",
		description = "Show a box above the tabs that searches everyone's inventory, equipment, bank, seed vault and looting bag, and the group storage",
		section = panelSection,
		position = 401
	)
	default boolean itemSearch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOfflineMembers",
		name = "Show offline members",
		description = "List members who aren't online on the Members tab",
		section = panelSection,
		position = 402
	)
	default boolean showOfflineMembers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onlineFirst",
		name = "Online members first",
		description = "List online members above offline ones on the Members tab, rather than only by name",
		section = panelSection,
		position = 403
	)
	default boolean onlineFirst()
	{
		return false;
	}

	@ConfigItem(
		keyName = "cardBars",
		name = "Live bars on cards",
		description = "Show hitpoints, prayer, run energy and special attack bars on online members' cards",
		section = panelSection,
		position = 404
	)
	default boolean cardBars()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logOwners",
		name = "Who has collection log items",
		description = "In the collection log, mark items with a dot in the colour of each group member who has them. Hover over an item for their names.",
		section = panelSection,
		position = 405
	)
	default boolean logOwners()
	{
		return true;
	}

	@ConfigSection(
		name = "Updates and chat",
		description = "Which of your group's news shows on the Updates tab, and which of it reaches the chat box",
		position = 50,
		closedByDefault = true
	)
	String updatesSection = "updates";

	@ConfigItem(
		keyName = "requestMessages",
		name = "Request chat messages",
		description = "Show a chat message when someone in your group makes, takes on or finishes a request",
		section = updatesSection,
		position = 501
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
		section = updatesSection,
		position = 502
	)
	default boolean updateMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "goalMessages",
		name = "Goal chat messages",
		description = "Show a chat message when someone in your group reaches a group goal",
		section = updatesSection,
		position = 503
	)
	default boolean goalMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pinMessages",
		name = "Pin chat messages",
		description = "Show a chat message when someone in your group pins a spot on the world map",
		section = updatesSection,
		position = 504
	)
	default boolean pinMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "updateLevels",
		name = "Levels",
		description = "Include levels gained in your group's updates",
		section = updatesSection,
		position = 505
	)
	default boolean updateLevels()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(
		keyName = "updateMinLevel",
		name = "Levels from",
		description = "Only include levels gained at or above this level, to leave out the early ones",
		section = updatesSection,
		position = 506
	)
	default int updateMinLevel()
	{
		return 1;
	}

	@ConfigItem(
		keyName = "updateQuests",
		name = "Quests and achievements",
		description = "Include quests, achievement diaries and combat achievements in your group's updates",
		section = updatesSection,
		position = 507
	)
	default boolean updateQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "updateCollectionLog",
		name = "Collection log",
		description = "Include new collection log slots in your group's updates",
		section = updatesSection,
		position = 508
	)
	default boolean updateCollectionLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "updateKillCounts",
		name = "Kill counts",
		description = "Include boss kill counts in your group's updates",
		section = updatesSection,
		position = 509
	)
	default boolean updateKillCounts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "updateDrops",
		name = "Drops and pets",
		description = "Include valuable drops and pets in your group's updates",
		section = updatesSection,
		position = 510
	)
	default boolean updateDrops()
	{
		return true;
	}

	@Range(min = 10_000, max = Integer.MAX_VALUE)
	@ConfigItem(
		keyName = "dropMinValue",
		name = "Drops worth at least",
		description = "Only include drops worth at least this many coins at Grand Exchange prices",
		section = updatesSection,
		position = 511
	)
	default int dropMinValue()
	{
		return 100_000;
	}

	@ConfigItem(
		keyName = "updateDeaths",
		name = "Deaths",
		description = "Include deaths in your group's updates",
		section = updatesSection,
		position = 512
	)
	default boolean updateDeaths()
	{
		return true;
	}

	@ConfigSection(
		name = "What you share",
		description = "What your group can see about you. Turning off your position, inventory, equipment, bank, seed vault or looting bag "
			+ "also removes what you've already shared of it.",
		position = 60,
		closedByDefault = true
	)
	String shareSection = "share";

	@ConfigItem(
		keyName = "sharePosition",
		name = "Your position",
		description = "Let your group see where you are, on the world map and in the group HUD",
		section = shareSection,
		position = 601
	)
	default boolean sharePosition()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareInventory",
		name = "Your inventory",
		description = "Let your group see your inventory",
		section = shareSection,
		position = 602
	)
	default boolean shareInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareEquipment",
		name = "Your equipment",
		description = "Let your group see what you're wearing",
		section = shareSection,
		position = 603
	)
	default boolean shareEquipment()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareBank",
		name = "Your bank",
		description = "Let your group see your bank",
		section = shareSection,
		position = 604
	)
	default boolean shareBank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareSeedVault",
		name = "Your seed vault",
		description = "Let your group see your seed vault",
		section = shareSection,
		position = 605
	)
	default boolean shareSeedVault()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareLootingBag",
		name = "Your looting bag",
		description = "Let your group see your looting bag",
		section = shareSection,
		position = 606
	)
	default boolean shareLootingBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareDrops",
		name = "Your drops and pets",
		description = "Tell your group about your valuable drops and pets",
		section = shareSection,
		position = 607
	)
	default boolean shareDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareDeaths",
		name = "Your deaths",
		description = "Tell your group when you die, and roughly where",
		section = shareSection,
		position = 608
	)
	default boolean shareDeaths()
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
