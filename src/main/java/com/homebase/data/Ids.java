package com.homebase.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stable identifiers derived by hashing, so raw account hashes and the group passphrase are never sent
 * to other players or written into file names.
 */
public final class Ids
{
	private Ids()
	{
	}

	public static String member(long accountHash)
	{
		return hash("homebase-member:" + accountHash);
	}

	public static String group(String passphrase)
	{
		return hash("homebase-group:" + passphrase);
	}

	private static String hash(String input)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(16);
			for (int i = 0; i < 8; i++)
			{
				sb.append(String.format("%02x", digest[i]));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
