package com.homebase.requests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.homebase.data.RequestData;
import org.junit.Test;

public class RequestDeliveryTest
{
	@Test
	public void requestsMatchItemsByName()
	{
		assertTrue(RequestDelivery.sameItem("bronze dagger", "Bronze dagger"));
		assertTrue(RequestDelivery.sameItem("  Bronze   dagger ", "Bronze dagger"));
		assertFalse(RequestDelivery.sameItem("Bronze dagger", "Bronze dagger(p)"));
		assertFalse(RequestDelivery.sameItem(null, "Bronze dagger"));
	}

	@Test
	public void reservedOnlyUntilDone()
	{
		RequestData data = new RequestData();
		assertFalse(data.isReserved());
		data.keep(1205, 1);
		assertTrue(data.isReserved());
		data.setDone(true);
		assertFalse(data.isReserved());
	}

	@Test
	public void amountsAddUpUntilEverythingAskedForArrives()
	{
		RequestData data = new RequestData();
		data.setQuantity(500);

		// 200 put in, then 1000 more: only what's still needed is kept back
		data.keep(995, 200);
		assertEquals(200, data.getReservedQuantity());
		data.keep(995, 1000);
		assertEquals(500, data.getReservedQuantity());

		// taking out more than was kept only counts what was kept
		data.receive(300, true);
		assertEquals(200, data.getReservedQuantity());
		assertEquals(300, data.getDelivered());
		assertFalse(data.isDone());

		// the rest by trade finishes it
		data.receive(200, false);
		assertTrue(data.isDone());
		assertFalse(data.isReserved());
	}

	@Test
	public void partOfARequestIsStillOpen()
	{
		RequestData data = new RequestData();
		data.setQuantity(10);
		data.keep(453, 4);
		data.receive(4, true);
		assertEquals(4, data.getDelivered());
		assertFalse(data.isReserved());
		assertFalse(data.isDone());
		// what's left can be put in again
		data.keep(453, 10);
		assertEquals(6, data.getReservedQuantity());
	}

	@Test
	public void tradePartnerFromTheTradeScreens()
	{
		assertEquals("Bonscott Kid", RequestDelivery.partnerName("Trading With: Bonscott Kid"));
		assertEquals("Bonscott Kid", RequestDelivery.partnerName("<col=ffffff>Trading with:<br>Bonscott Kid</col>"));
		assertNull(RequestDelivery.partnerName("Trading With: "));
	}

	@Test
	public void playerNamesMatchHoweverSpacesAreWritten()
	{
		assertTrue(RequestDelivery.sameName("Bonscott Kid", "Bonscott Kid"));
		assertTrue(RequestDelivery.sameName("bonscott kid", "Bonscott Kid"));
		assertFalse(RequestDelivery.sameName("Bonscott Kid", "Goulashnicov"));
	}
}
