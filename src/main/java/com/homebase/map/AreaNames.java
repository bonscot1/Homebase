package com.homebase.map;

import java.util.Arrays;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Names for where a member is, such as "Varrock", "near Catherby" or "Wilderness (level 11)".
 * <p>
 * The game has no list of area names, so this is a table of well known places. A member near one of
 * them gets its name, a little further away gets "near" it, and dungeons are named after the place
 * above them.
 */
public final class AreaNames
{
	public static final String UNKNOWN = "Unknown area";

	/**
	 * Dungeons sit this far north of the land above them on the game's map.
	 */
	private static final int UNDERGROUND_OFFSET = 6400;

	/**
	 * The band of the game's map that most dungeons are in.
	 */
	private static final int UNDERGROUND_SOUTH = 8600;
	private static final int UNDERGROUND_NORTH = 10500;

	/**
	 * The main map's land is all south of this.
	 */
	private static final int SURFACE_NORTH = 4160;

	/**
	 * The Wilderness on the surface. Its dungeons cover the same ground {@link #UNDERGROUND_OFFSET} north.
	 */
	private static final int WILDERNESS_WEST = 2944;
	private static final int WILDERNESS_EAST = 3392;
	private static final int WILDERNESS_SOUTH = 3520;
	private static final int WILDERNESS_NORTH = 3968;

	/**
	 * Rows of tiles to each Wilderness level, going north.
	 */
	private static final int ROWS_PER_LEVEL = 8;

	/**
	 * How far past a place's own radius still counts as "near" it.
	 */
	private static final int NEAR_DISTANCE = 120;

	private static final class Place
	{
		final String name;
		final int x;
		final int y;
		final int radius;

		Place(String name, int x, int y, int radius)
		{
			this.name = name;
			this.x = x;
			this.y = y;
			this.radius = radius;
		}

		int distance(int px, int py)
		{
			return Math.max(Math.abs(px - x), Math.abs(py - y));
		}
	}

	private static final List<Place> PLACES = Arrays.asList(
		// Misthalin
		new Place("Lumbridge", 3222, 3218, 40),
		new Place("Lumbridge Swamp", 3197, 3169, 25),
		new Place("Draynor Village", 3093, 3250, 35),
		new Place("Draynor Manor", 3108, 3350, 25),
		new Place("Wizards' Tower", 3109, 3162, 20),
		new Place("Varrock", 3213, 3424, 55),
		new Place("Grand Exchange", 3165, 3487, 25),
		new Place("Barbarian Village", 3082, 3420, 25),
		new Place("Edgeville", 3094, 3491, 25),
		new Place("Champions' Guild", 3191, 3366, 12),
		new Place("Lumber Yard", 3305, 3490, 20),
		new Place("Digsite", 3355, 3410, 35),
		new Place("Paterdomus", 3405, 3488, 20),
		new Place("Tutorial Island", 3094, 3107, 40),
		// Asgarnia
		new Place("Falador", 2965, 3380, 45),
		new Place("Port Sarim", 3023, 3208, 30),
		new Place("Rimmington", 2957, 3214, 25),
		new Place("Crafting Guild", 2933, 3285, 15),
		new Place("Taverley", 2895, 3443, 30),
		new Place("Burthorpe", 2899, 3544, 30),
		new Place("Warriors' Guild", 2855, 3545, 12),
		new Place("Trollheim", 2890, 3680, 35),
		new Place("God Wars Dungeon", 2917, 3745, 20),
		new Place("Goblin Village", 2956, 3505, 20),
		new Place("Ice Mountain", 3013, 3480, 25),
		new Place("Black Knights' Fortress", 3016, 3514, 15),
		new Place("Entrana", 2834, 3335, 40),
		new Place("Mudskipper Point", 2995, 3115, 20),
		new Place("Crandor", 2850, 3260, 30),
		// Kandarin
		new Place("Catherby", 2813, 3447, 30),
		new Place("Camelot", 2757, 3477, 25),
		new Place("Seers' Village", 2726, 3485, 30),
		new Place("Sinclair Mansion", 2741, 3556, 20),
		new Place("Fishing Guild", 2611, 3393, 20),
		new Place("Ranging Guild", 2655, 3440, 20),
		new Place("East Ardougne", 2662, 3305, 45),
		new Place("West Ardougne", 2535, 3305, 40),
		new Place("Witchaven", 2720, 3285, 20),
		new Place("Port Khazard", 2660, 3160, 25),
		new Place("Fight Arena", 2585, 3160, 25),
		new Place("Yanille", 2606, 3093, 35),
		new Place("Castle Wars", 2442, 3088, 30),
		new Place("Observatory", 2440, 3160, 25),
		new Place("Tree Gnome Stronghold", 2461, 3444, 55),
		new Place("Tree Gnome Village", 2525, 3170, 30),
		new Place("Barbarian Outpost", 2519, 3571, 30),
		new Place("Baxtorian Falls", 2512, 3480, 20),
		new Place("Feldip Hills", 2550, 2970, 50),
		new Place("Corsair Cove", 2570, 2860, 35),
		new Place("Piscatoris", 2340, 3690, 40),
		// Fremennik
		new Place("Rellekka", 2658, 3657, 40),
		new Place("Waterbirth Island", 2530, 3740, 25),
		new Place("Miscellania", 2535, 3860, 40),
		new Place("Neitiznot", 2330, 3800, 30),
		new Place("Jatizso", 2410, 3800, 30),
		new Place("Lunar Isle", 2100, 3914, 50),
		new Place("Weiss", 2850, 3940, 40),
		// Karamja
		new Place("Musa Point", 2918, 3176, 25),
		new Place("Brimhaven", 2760, 3178, 35),
		new Place("Tai Bwo Wannai", 2790, 3065, 25),
		new Place("Shilo Village", 2852, 2955, 30),
		new Place("Ape Atoll", 2755, 2784, 60),
		// Kharidian Desert
		new Place("Al Kharid", 3293, 3174, 40),
		new Place("Shantay Pass", 3304, 3117, 15),
		new Place("Kalphite Lair", 3227, 3108, 15),
		new Place("Bedabin Camp", 3170, 3035, 20),
		new Place("Pollnivneach", 3359, 2967, 30),
		new Place("Nardah", 3424, 2917, 30),
		new Place("Uzer", 3484, 3090, 20),
		new Place("Sophanem", 3305, 2785, 30),
		new Place("Menaphos", 3233, 2779, 30),
		// Morytania
		new Place("Canifis", 3496, 3488, 35),
		new Place("Mort Myre Swamp", 3440, 3400, 40),
		new Place("Port Phasmatys", 3687, 3502, 30),
		new Place("Mort'ton", 3489, 3288, 25),
		new Place("Barrows", 3565, 3289, 25),
		new Place("Burgh de Rott", 3496, 3211, 30),
		new Place("Ver Sinhaza", 3650, 3215, 30),
		new Place("Mos Le'Harmless", 3686, 2967, 40),
		new Place("Fossil Island", 3732, 3803, 60),
		// Tirannwn
		new Place("Lletya", 2341, 3171, 25),
		new Place("Zul-Andra", 2200, 3056, 25),
		new Place("Prifddinas", 3265, 6070, 90),
		// Great Kourend and Kebos
		new Place("Kourend Castle", 1639, 3673, 30),
		new Place("Hosidius", 1748, 3594, 50),
		new Place("Arceuus", 1700, 3770, 50),
		new Place("Lovakengj", 1500, 3810, 50),
		new Place("Shayzien", 1504, 3615, 45),
		new Place("Port Piscarilius", 1803, 3748, 40),
		new Place("Wintertodt", 1630, 3944, 40),
		new Place("Lake Molch", 1365, 3632, 20),
		new Place("Farming Guild", 1248, 3720, 30),
		new Place("Mount Karuulm", 1311, 3807, 30),
		// Varlamore
		new Place("Civitas illa Fortis", 1700, 3100, 70),
		// Sailing's islands and ports, at the coordinates the OSRS Wiki gives them
		new Place("Shipyard", 2078, 2720, 25),
		new Place("Deepfin Point", 1948, 2782, 30),
		new Place("Port Roberts", 1888, 3298, 30),
		new Place("The Great Conch", 3200, 2435, 90),
		new Place("The Summer Shore", 3155, 2415, 20),
		new Place("The Sacred Grove", 3200, 2485, 12),
		new Place("Abalone Cliffs", 3105, 2518, 15),
		new Place("Cape Conch", 3279, 2336, 20),
		new Place("Buccaneers' Haven", 2080, 3685, 35),
		new Place("Minotaurs' Rest", 1953, 3103, 30),
		new Place("Charred Island", 2648, 2406, 35),
		new Place("Wintumber Island", 2068, 2603, 30),
		new Place("Isle of Souls", 2210, 2900, 70),
		new Place("Tear of the Soul", 2333, 2768, 20),
		new Place("Limbo", 2023, 2666, 15),
		new Place("Isle of Bones", 2533, 2533, 25),
		new Place("Isle of Serpents", 1855, 2425, 25),
		new Place("Sunbleak Island", 2209, 2330, 30),
		new Place("Rainbow's End", 2335, 2270, 20),
		new Place("Wyrmscraig", 2571, 2240, 45),
		new Place("Auchrie", 2576, 2258, 12),
		new Place("Ardeaglais", 2532, 2216, 10),
		new Place("Last Light", 2850, 2330, 25),
		new Place("Red Rock", 2799, 2517, 15),
		new Place("Remote Island", 2961, 2610, 20),
		new Place("Dognose Island", 3048, 2648, 25),
		new Place("The Onyx Crest", 2975, 2273, 25),
		new Place("The Little Pearl", 3356, 2205, 20),
		new Place("The Pandemonium", 3040, 2980, 25),
		new Place("Tempoross Cove", 3035, 2850, 20),
		new Place("Rock Island Prison", 2898, 2837, 15),
		new Place("Crash Island", 2914, 2720, 25),
		new Place("Void Knights' Outpost", 2656, 2656, 30),
		new Place("Pest Control Island", 2656, 2592, 20),
		new Place("Anglers' Retreat", 2478, 2715, 20),
		new Place("Myths' Guild", 2457, 2846, 15),
		new Place("Cairn Isle", 2765, 2976, 12),
		new Place("Shimmering Atoll", 1569, 2786, 25),
		new Place("The Crown Jewel", 1761, 2664, 25),
		new Place("Laguna Aurorae", 1195, 2772, 30),
		new Place("Vatrachos Island", 1887, 2984, 25),
		new Place("Lledrith Island", 2091, 3179, 20),
		new Place("Port Tyras", 2153, 3122, 20),
		new Place("Ynysdail", 2220, 3475, 30),
		new Place("Gwenith", 2200, 3410, 25),
		new Place("Drumstick Isle", 2146, 3545, 20),
		new Place("Chinchompa Island", 1884, 3434, 20),
		new Place("Crabclaw Isle", 1767, 3420, 25),
		new Place("Pirates' Cove", 2205, 3812, 25),
		new Place("Ungael", 2272, 4064, 20),
		new Place("Brittle Isle", 1947, 4069, 25),
		new Place("Etceteria", 2604, 3873, 25),
		new Place("Island of Stone", 2463, 4008, 20),
		new Place("Iceberg", 2652, 4029, 30),
		new Place("The Graveyard", 2786, 3996, 25),
		new Place("Grimstone", 2913, 4072, 25),
		new Place("Lithkren", 3566, 4003, 30),
		new Place("Harmony Island", 3805, 2844, 20),
		new Place("Braindeath Island", 3680, 3535, 25),
		new Place("Dragontooth Island", 3806, 3549, 15),
		new Place("Karamja Shipyard", 2987, 3050, 20),
		new Place("Kharazi Jungle", 2865, 2907, 50),
		// The rest of Varlamore
		new Place("Sunset Coast", 1530, 2983, 35),
		new Place("Aldarin", 1391, 2935, 35),
		new Place("Mistrock", 1387, 2862, 20),
		new Place("Darkmoon Ravine", 1310, 2915, 20),
		new Place("Kastori", 1373, 3042, 30),
		new Place("Tal Teklan", 1223, 3111, 30),
		new Place("Tlati Rainforest", 1327, 3090, 45),
		new Place("Auburn Valley", 1384, 3169, 35),
		new Place("Auburnvale", 1417, 3360, 35),
		new Place("Nemus Retreat", 1376, 3310, 15),
		new Place("Tempestus", 1285, 3256, 15),
		new Place("College of Bards", 1296, 3213, 15),
		new Place("Custodia Mountains", 1277, 3390, 40),
		new Place("The Stranglewood", 1162, 3379, 45),
		new Place("Ralos' Rise", 1450, 3140, 25),
		new Place("Quetzacalli Gorge", 1505, 3232, 25),
		new Place("The Proudspire", 1570, 3235, 15),
		new Place("Salvager Overlook", 1625, 3295, 25),
		new Place("Shipwreck Cove", 1575, 3315, 15),
		new Place("Hunter Guild", 1559, 3048, 20),
		new Place("Locus Oasis", 1685, 2986, 20),
		new Place("Colossal Wyrm Remains", 1640, 2921, 20),
		new Place("Twilight Temple", 1670, 3246, 15),
		new Place("Ortus Farm", 1569, 3126, 15),
		new Place("Gloomthorn Trail", 1381, 3226, 20),
		new Place("Mons Gratia", 1440, 3240, 20),
		// Smaller places around the regions above
		new Place("Land's End", 1510, 3430, 25),
		new Place("Woodcutting Guild", 1565, 3499, 25),
		new Place("Kourend Woodland", 1540, 3464, 20),
		new Place("Lizardman Settlement", 1324, 3556, 25),
		new Place("Mount Quidamortem", 1246, 3550, 30),
		new Place("Battlefront", 1368, 3716, 12),
		new Place("Isafdar", 2244, 3182, 35),
		new Place("Arandar", 2342, 3290, 15),
		new Place("Iorwerth Camp", 2198, 3253, 15),
		new Place("Tyras Camp", 2185, 3145, 15),
		new Place("Poison Waste", 2245, 3078, 20),
		new Place("Mynydd", 2158, 3423, 15),
		new Place("Darkmeyer", 3597, 3360, 30),
		new Place("Meiyerditch", 3615, 3250, 35),
		new Place("Slepe", 3724, 3335, 20),
		new Place("Icyene Graveyard", 3696, 3184, 15),
		new Place("Necropolis", 3340, 2724, 25),
		new Place("Ruins of Unkah", 3165, 2845, 20),
		new Place("Ruins of Ullek", 3412, 2811, 20),
		new Place("Citharede Abbey", 3411, 3177, 15),
		new Place("Troll Stronghold", 2836, 3680, 15),
		new Place("Trollweiss Mountain", 2782, 3790, 30),
		new Place("Karamja Volcano", 2845, 3174, 20),
		new Place("Gu'Tanoth", 2524, 3035, 20),
		// Wilderness safe zone, checked before the Wilderness itself
		new Place("Ferox Enclave", 3130, 3631, 20),
		// Places off the main map
		new Place("Zanaris", 2412, 4434, 60),
		new Place("Motherlode Mine", 3760, 5670, 40),
		new Place("Keldagrim", 2885, 10200, 70),
		new Place("Guardians of the Rift", 3615, 9490, 40),
		new Place("Dorgesh-Kaan", 2723, 5314, 40),
		new Place("Lassar Undercity", 2663, 6367, 40),
		new Place("Puro-Puro", 2593, 4320, 25),
		new Place("Vampyrium", 2750, 7860, 230),
		new Place("Sangvesti", 2604, 7838, 40),
		new Place("Ruined Village", 2710, 7842, 20),
		new Place("Sotfa Forest", 2740, 7850, 25),
		new Place("Sugadinti's Hideout", 2973, 7678, 20),
		new Place("Maggot King's lair", 2911, 8036, 20),
		// houses are instances built from room templates kept in this block
		new Place("Player-owned house", 1952, 7104, 96)
	);

	private AreaNames()
	{
	}

	public static String describe(WorldPoint point)
	{
		return point == null ? null : describe(point.getX(), point.getY());
	}

	public static String describe(int x, int y)
	{
		Place closest = closest(x, y);
		if (closest != null && closest.distance(x, y) <= closest.radius)
		{
			return closest.name;
		}

		int wildy = wildernessLevel(x, y);
		if (wildy > 0)
		{
			return (isUnderground(y) ? "Wilderness dungeon" : "Wilderness") + " (level " + wildy + ")";
		}

		if (isUnderground(y))
		{
			String above = describe(x, y - UNDERGROUND_OFFSET);
			if (UNKNOWN.equals(above))
			{
				return "Underground";
			}
			return (above.startsWith("near ") ? above.substring(5) : above) + " (underground)";
		}

		if (closest != null && closest.distance(x, y) <= closest.radius + NEAR_DISTANCE)
		{
			return "near " + closest.name;
		}
		return UNKNOWN;
	}

	/**
	 * @return the Wilderness level at a point, or 0 outside the Wilderness
	 */
	private static int wildernessLevel(int x, int y)
	{
		int surfaceY = y >= WILDERNESS_SOUTH + UNDERGROUND_OFFSET ? y - UNDERGROUND_OFFSET : y;
		if (x < WILDERNESS_WEST || x >= WILDERNESS_EAST || surfaceY < WILDERNESS_SOUTH || surfaceY >= WILDERNESS_NORTH)
		{
			return 0;
		}
		return (surfaceY - WILDERNESS_SOUTH) / ROWS_PER_LEVEL + 1;
	}

	/**
	 * @return the spot on the surface above a point, or the point itself if it isn't in a dungeon
	 */
	public static WorldPoint aboveGround(WorldPoint point)
	{
		return isUnderground(point.getY()) ? new WorldPoint(point.getX(), point.getY() - UNDERGROUND_OFFSET, 0) : point;
	}

	/**
	 * @return true if a point is on the land of the main map, rather than in somewhere like a player-owned
	 * house or a raid, which are all further north. A few boss rooms are the exception.
	 */
	public static boolean isOnSurface(WorldPoint point)
	{
		return point.getY() < SURFACE_NORTH;
	}

	private static boolean isUnderground(int y)
	{
		return y >= UNDERGROUND_SOUTH && y < UNDERGROUND_NORTH;
	}

	private static Place closest(int x, int y)
	{
		Place best = null;
		Place smallestAround = null;
		int bestScore = Integer.MAX_VALUE;
		for (Place place : PLACES)
		{
			// compare by how far outside each place's radius the point is, so a small place next to a
			// big one still wins when you're standing in it
			int score = place.distance(x, y) - place.radius;
			if (score <= 0 && (smallestAround == null || place.radius < smallestAround.radius))
			{
				smallestAround = place;
			}
			if (score < bestScore)
			{
				bestScore = score;
				best = place;
			}
		}
		// a town on an island is named over the island it's on
		return smallestAround != null ? smallestAround : best;
	}
}
