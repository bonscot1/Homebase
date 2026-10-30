package com.homebase.sync;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Encoding for party messages and the save file.
 * <p>
 * Party messages are compressed, since banks and collection logs are large and the party service is
 * shared with every other RuneLite user.
 */
public final class WireCodec
{
	public static final Type SUMMARY_TYPE = new TypeToken<Map<String, Long>>()
	{
	}.getType();

	public static final Type RECORDS_TYPE = new TypeToken<List<SyncRecord>>()
	{
	}.getType();

	/**
	 * Refuse to inflate messages beyond this, so a malformed message can't exhaust memory.
	 */
	static final int MAX_INFLATED_BYTES = 4 << 20;

	private static final int SAVE_FORMAT = 1;

	private WireCodec()
	{
	}

	public static String pack(String json)
	{
		byte[] input = json.getBytes(StandardCharsets.UTF_8);
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
		try
		{
			deflater.setInput(input);
			deflater.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 4));
			byte[] buf = new byte[4096];
			while (!deflater.finished())
			{
				int n = deflater.deflate(buf);
				out.write(buf, 0, n);
			}
			return Base64.getEncoder().encodeToString(out.toByteArray());
		}
		finally
		{
			deflater.end();
		}
	}

	public static String unpack(String packed) throws IOException
	{
		if (packed == null)
		{
			throw new IOException("missing payload");
		}
		byte[] input;
		try
		{
			input = Base64.getDecoder().decode(packed);
		}
		catch (IllegalArgumentException e)
		{
			throw new IOException("bad base64", e);
		}
		Inflater inflater = new Inflater();
		try
		{
			inflater.setInput(input);
			ByteArrayOutputStream out = new ByteArrayOutputStream(input.length * 4);
			byte[] buf = new byte[4096];
			while (!inflater.finished())
			{
				int n = inflater.inflate(buf);
				if (n == 0 && (inflater.needsInput() || inflater.needsDictionary()))
				{
					throw new IOException("truncated payload");
				}
				out.write(buf, 0, n);
				if (out.size() > MAX_INFLATED_BYTES)
				{
					throw new IOException("payload too large");
				}
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
		catch (DataFormatException e)
		{
			throw new IOException("bad payload", e);
		}
		finally
		{
			inflater.end();
		}
	}

	private static final class SaveFile
	{
		int format;
		List<SyncRecord> records;
	}

	public static void writeSave(Gson gson, Writer writer, List<SyncRecord> records)
	{
		SaveFile save = new SaveFile();
		save.format = SAVE_FORMAT;
		save.records = records;
		gson.toJson(save, writer);
	}

	/**
	 * @return records from a save file, or null if the file is from an unknown format
	 */
	public static List<SyncRecord> readSave(Gson gson, Reader reader)
	{
		SaveFile save = gson.fromJson(reader, SaveFile.class);
		if (save == null || save.format != SAVE_FORMAT || save.records == null)
		{
			return null;
		}
		return save.records;
	}
}
