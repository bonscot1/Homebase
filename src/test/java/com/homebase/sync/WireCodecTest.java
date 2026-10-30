package com.homebase.sync;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class WireCodecTest
{
	private final Gson gson = new Gson();

	@Test
	public void roundTrips() throws IOException
	{
		String json = "{\"hello\":\"world\",\"items\":[1,2,3]}";
		assertEquals(json, WireCodec.unpack(WireCodec.pack(json)));
	}

	@Test
	public void compressesRepetitiveData()
	{
		StringBuilder bank = new StringBuilder("[");
		for (int i = 0; i < 800; i++)
		{
			bank.append(i * 7).append(',').append(1).append(',');
		}
		bank.append("0]");
		String packed = WireCodec.pack(bank.toString());
		assertTrue(packed.length() < bank.length() / 2);
	}

	@Test(expected = IOException.class)
	public void refusesOversizedPayloads() throws IOException
	{
		char[] zeros = new char[WireCodec.MAX_INFLATED_BYTES + 1];
		Arrays.fill(zeros, '0');
		WireCodec.unpack(WireCodec.pack(new String(zeros)));
	}

	@Test(expected = IOException.class)
	public void refusesGarbage() throws IOException
	{
		WireCodec.unpack("not a real payload!");
	}

	@Test
	public void saveFileRoundTrips()
	{
		List<SyncRecord> records = Arrays.asList(
			new SyncRecord(Keys.member("alice", "bank"), 5, "alice", false, new JsonPrimitive("bank")),
			new SyncRecord(Keys.shared("goal", "1"), 6, "bob", true, null));
		StringWriter writer = new StringWriter();
		WireCodec.writeSave(gson, writer, records);
		assertEquals(records, WireCodec.readSave(gson, new StringReader(writer.toString())));
	}

	@Test
	public void unknownSaveFormatIsIgnored()
	{
		assertNull(WireCodec.readSave(gson, new StringReader("{\"format\":99,\"records\":[]}")));
	}
}
