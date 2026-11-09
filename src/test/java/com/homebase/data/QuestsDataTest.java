package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestsDataTest
{
	@Test
	public void questPointsFromAnotherClientStayInRange()
	{
		QuestsData quests = new QuestsData();
		quests.setPoints(-4);
		quests.check();
		assertEquals(0, quests.getPoints());
		quests.setPoints(1_000_000);
		quests.check();
		assertEquals(QuestsData.MAX_POINTS, quests.getPoints());
	}

	@Test
	public void miniquestsAreCountedApart()
	{
		QuestsData data = new QuestsData();
		data.getQuests().put("Cook's Assistant", QuestsData.FINISHED);
		data.getQuests().put("Daddy's Home", QuestsData.FINISHED);
		data.getQuests().put("Enter the Abyss", QuestsData.FINISHED);
		data.getQuests().put("Alfred Grimhand's Barcrawl", QuestsData.STARTED);
		data.getQuests().put("Dwarf Cannon", QuestsData.STARTED);

		assertEquals(1, data.count(QuestsData.FINISHED));
		assertEquals(2, data.countMiniquests(QuestsData.FINISHED));
		assertEquals(1, data.count(QuestsData.STARTED));
		assertEquals(1, data.countMiniquests(QuestsData.STARTED));
		assertTrue(QuestsData.isMiniquest("Mage Arena I"));
		assertFalse(QuestsData.isMiniquest("Dragon Slayer I"));
	}
}
