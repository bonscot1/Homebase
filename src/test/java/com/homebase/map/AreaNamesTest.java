package com.homebase.map;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class AreaNamesTest
{
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
	public void nearAndUnknown()
	{
		assertEquals("near Lumbridge", AreaNames.describe(3290, 3270));
		assertEquals(AreaNames.UNKNOWN, AreaNames.describe(100, 100));
	}
}
