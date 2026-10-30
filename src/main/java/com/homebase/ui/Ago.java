package com.homebase.ui;

import java.util.concurrent.TimeUnit;

final class Ago
{
	private Ago()
	{
	}

	static String format(long timestamp)
	{
		if (timestamp <= 0)
		{
			return "never";
		}
		long seconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - timestamp);
		if (seconds < 60)
		{
			return "just now";
		}
		long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		long hours = minutes / 60;
		if (hours < 48)
		{
			return hours + "h ago";
		}
		return hours / 24 + "d ago";
	}
}
