package com.homebase.map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FindOnMapTest
{
	@Test
	public void aClickIsOnlyRememberedForAWhile()
	{
		long clicked = 1_000_000;
		assertTrue(FindOnMap.isStillWanted(true, clicked, clicked + 1_000));
		assertFalse(FindOnMap.isStillWanted(true, clicked, clicked + FindOnMap.REMEMBER_MS));
		assertFalse(FindOnMap.isStillWanted(false, clicked, clicked + 1_000));
	}
}
