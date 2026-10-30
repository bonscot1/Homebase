package com.homebase.sync;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import lombok.Value;

/**
 * One versioned piece of group data, such as a member's bank or the group storage.
 * <p>
 * The version is the time the data was captured, taken from the clock of whoever wrote it. When two
 * copies of the same key meet, the higher version wins, so data can be relayed between members in any
 * order and everyone ends up with the newest copy.
 */
@Value
public class SyncRecord
{
	@SerializedName("k")
	String key;

	@SerializedName("v")
	long version;

	/**
	 * Member id of whoever wrote this version.
	 */
	@SerializedName("a")
	String author;

	/**
	 * Deleted records are kept as tombstones so that an older copy relayed later can't bring them back.
	 */
	@SerializedName("x")
	boolean deleted;

	@SerializedName("d")
	JsonElement data;

	public boolean supersedes(SyncRecord other)
	{
		if (other == null)
		{
			return true;
		}
		if (version != other.version)
		{
			return version > other.version;
		}
		// same timestamp from two writers: pick one consistently so every client agrees
		return author.compareTo(other.author) > 0;
	}
}
