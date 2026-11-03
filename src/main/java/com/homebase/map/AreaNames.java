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
	static final int UNDERGROUND_OFFSET = 6400;

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
		// Wilderness safe zone, checked before the Wilderness itself
		new Place("Ferox Enclave", 3130, 3631, 20),
		// Places off the main map
		new Place("Zanaris", 2412, 4434, 60),
		new Place("Motherlode Mine", 3760, 5670, 40),
		new Place("Keldagrim", 2885, 10200, 70),
		new Place("Guardians of the Rift", 3615, 9490, 40)
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
			return (y >= 9920 ? "Wilderness dungeon" : "Wilderness") + " (level " + wildy + ")";
		}

		if (y >= 8700 && y < 10500)
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
	static int wildernessLevel(int x, int y)
	{
		if (x < 2944 || x >= 3392)
		{
			return 0;
		}
		if (y >= 3520 && y < 3968)
		{
			return (y - 3520) / 8 + 1;
		}
		if (y >= 9920 && y < 10368)
		{
			return (y - 9920) / 8 + 1;
		}
		return 0;
	}

	private static Place closest(int x, int y)
	{
		Place best = null;
		int bestScore = Integer.MAX_VALUE;
		for (Place place : PLACES)
		{
			// compare by how far outside each place's radius the point is, so a small place next to a
			// big one still wins when you're standing in it
			int score = place.distance(x, y) - place.radius;
			if (score < bestScore)
			{
				bestScore = score;
				best = place;
			}
		}
		return best;
	}
}
