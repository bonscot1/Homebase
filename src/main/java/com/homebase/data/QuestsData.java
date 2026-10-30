package com.homebase.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class QuestsData
{
	public static final String FINISHED = "F";
	public static final String STARTED = "S";

	/**
	 * Quest name to {@link #FINISHED} or {@link #STARTED}. Quests not started are left out. Keyed by
	 * name rather than RuneLite's quest list so members on different RuneLite versions still agree.
	 */
	private Map<String, String> quests = new HashMap<>();

	public int count(String state)
	{
		int n = 0;
		for (String s : quests.values())
		{
			if (state.equals(s))
			{
				n++;
			}
		}
		return n;
	}
}
