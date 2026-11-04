package com.homebase.data;

import com.google.gson.Gson;
import com.homebase.sync.GroupStore;
import com.homebase.sync.SyncEngine;
import java.awt.Color;
import java.util.Arrays;
import java.util.Random;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class GroupIndexTest
{
	private final Gson gson = new Gson();
	private final GroupStore store = new GroupStore();
	private final SyncEngine engine = new SyncEngine(store, System::currentTimeMillis, new Random());
	private final GroupIndex index = new GroupIndex();

	@Before
	public void setUp()
	{
		index.setView(new GroupView(store, gson), p -> true);
	}

	private void publish(String id, Section section, Object data)
	{
		engine.publish(section.keyFor(id), id, gson.toJsonTree(data));
		index.invalidate();
	}

	@Test
	public void aMembersChosenColourDoesntNeedAMarkedTile()
	{
		publish("a", Section.PROFILE, new ProfileData("Mundell", 301, null, 0x20C040));
		publish("b", Section.PROFILE, new ProfileData("Bonscott", 302, null));

		assertEquals(new Color(0x20C040), index.color("Mundell"));
		// someone on an older version keeps the default until they mark something
		assertEquals(GroupIndex.DEFAULT_COLOR, index.color("Bonscott"));
	}

	@Test
	public void knowsMembersNamesAndWorlds()
	{
		publish("a", Section.PROFILE, new ProfileData("Mundell", 301, null));

		assertEquals("Mundell", index.nameOf("a"));
		assertEquals(301, index.world("Mundell"));
		assertEquals(0, index.world("Nobody"));
	}

	@Test
	public void knowsWhetherAnyoneElseIsInTheGroup()
	{
		assertFalse(index.hasOtherMembers("a"));
		publish("a", Section.PROFILE, new ProfileData("Mundell", 301, null));
		assertFalse(index.hasOtherMembers("a"));
		assertTrue(index.hasOtherMembers("b"));
		publish("b", Section.PROFILE, new ProfileData("Bonscott", 301, null));
		assertTrue(index.hasOtherMembers("a"));
	}

	@Test
	public void listsPlacedPinsOnly()
	{
		publish("a", Section.PROFILE, new ProfileData("Mundell", 301, null));
		publish("b", Section.PROFILE, new ProfileData("Bonscott", 301, null));
		publish("a", Section.PIN, new PinData(true, 3222, 3218, 0, 0x20C040, 1000));
		publish("b", Section.PIN, new PinData(false, 3100, 3100, 0, 0xFF0000, 1000));

		assertEquals(1, index.pins().size());
		GroupIndex.Pin pin = index.pins().get(0);
		assertEquals("Mundell", pin.getName());
		assertEquals(3222, pin.getPoint().getX());
		assertEquals(new Color(0x20C040), pin.getColor());
	}

	@Test
	public void findsWhoHasACollectionLogItem()
	{
		publish("a", Section.PROFILE, new ProfileData("Mundell", 301, null, 0x20C040));
		publish("b", Section.PROFILE, new ProfileData("Bonscott", 301, null));
		CollectionLogData mine = new CollectionLogData();
		mine.getPages().put("Vorkath", new CollectionLogData.Page(5, Arrays.asList(100, 101), 0));
		// the same item on another page still only counts once
		mine.getPages().put("Other", new CollectionLogData.Page(5, Arrays.asList(100), 0));
		publish("a", Section.COLLECTION_LOG, mine);
		CollectionLogData theirs = new CollectionLogData();
		theirs.getPages().put("Vorkath", new CollectionLogData.Page(5, Arrays.asList(100), 0));
		publish("b", Section.COLLECTION_LOG, theirs);

		assertEquals(2, index.logOwners(100).size());
		assertEquals(1, index.logOwners(101).size());
		assertEquals("Mundell", index.logOwners(101).get(0).getName());
		assertEquals(new Color(0x20C040), index.logOwners(101).get(0).getColor());
		assertTrue(index.logOwners(102).isEmpty());
	}

	@Test
	public void newDataReplacesWhoHasAnItem()
	{
		publish("a", Section.PROFILE, new ProfileData("Mundell", 301, null));
		assertTrue(index.logOwners(100).isEmpty());

		CollectionLogData log = new CollectionLogData();
		log.getPages().put("Vorkath", new CollectionLogData.Page(5, Arrays.asList(100), 0));
		publish("a", Section.COLLECTION_LOG, log);
		assertEquals(1, index.logOwners(100).size());
	}
}
