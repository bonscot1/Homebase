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
		data.setReservedItemId(1205);
		assertTrue(data.isReserved());
		data.setDone(true);
		assertFalse(data.isReserved());
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
