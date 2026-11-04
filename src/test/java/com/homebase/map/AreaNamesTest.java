package com.homebase.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class AreaNamesTest
{
	@Test
	public void dungeonsAreUnderTheLandAboveThem()
	{
		// Varrock's sewers, under Varrock
		assertEquals(new WorldPoint(3237, 3499, 0), AreaNames.aboveGround(new WorldPoint(3237, 9899, 0)));
		// already on the surface, upstairs included
		WorldPoint upstairs = new WorldPoint(3222, 3218, 1);
		assertEquals(upstairs, AreaNames.aboveGround(upstairs));
	}

	@Test
	public void housesAndRaidsAreOffTheMainMap()
	{
		assertTrue(AreaNames.isOnSurface(new WorldPoint(3222, 3218, 0)));
		// the far north of the Wilderness
		assertTrue(AreaNames.isOnSurface(new WorldPoint(3100, 3960, 0)));
		// a player-owned house
		assertFalse(AreaNames.isOnSurface(new WorldPoint(1930, 5700, 0)));
	}

	@Test
	public void namesTownsMembersAreIn()
	{
		assertEquals("Lumbridge", AreaNames.describe(3222, 3218));
		assertEquals("Varrock", AreaNames.describe(3213, 3424));
		assertEquals("Grand Exchange", AreaNames.describe(3165, 3487));
		assertEquals("Falador", AreaNames.describe(2965, 3380));
		assertEquals("Hosidius", AreaNames.describe(1748, 3594));
	}

	@Test
	public void smallPlaceWinsInsideBigNeighbour()
	{
		// the Champions' Guild sits inside Varrock's radius
		assertEquals("Champions' Guild", AreaNames.describe(3191, 3366));
	}

	@Test
	public void wildernessGivesLevel()
	{
		assertEquals("Wilderness (level 11)", AreaNames.describe(3100, 3600));
		assertEquals("Wilderness (level 1)", AreaNames.describe(3000, 3521));
		assertEquals("Wilderness dungeon (level 3)", AreaNames.describe(3100, 9937));
	}

	@Test
	public void feroxEnclaveIsNotWilderness()
	{
		assertEquals("Ferox Enclave", AreaNames.describe(3130, 3631));
	}

	@Test
	public void dungeonsAreNamedAfterThePlaceAbove()
	{
		// Varrock sewers
		assertEquals("Varrock (underground)", AreaNames.describe(3237, 9858));
	}

	@Test
	public void housesAreNamed()
	{
		// a garden tile marked in a real house, as the game reports it inside the instance
		assertEquals("Player-owned house", AreaNames.describe(1859, 7050));
	}

	@Test
	public void nearAndUnknown()
	{
		assertEquals("near Lumbridge", AreaNames.describe(3290, 3270));
		assertEquals(AreaNames.UNKNOWN, AreaNames.describe(100, 100));
	}
}
