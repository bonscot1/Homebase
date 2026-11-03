package com.homebase.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a member has in their collection log. The game only reveals a page's items while it's open, so
 * pages are captured as the member browses them, and new slots are caught from the chat message.
 */
@Data
public class CollectionLogData implements Checkable
{
	private static final int MAX_PAGE_ITEMS = 500;

	/**
	 * Most recent new slots kept, newest first.
	 */
	public static final int MAX_RECENT = 20;

	/**
	 * Most pages kept, so a bad client can't flood everyone with junk.
	 */
	public static final int MAX_PAGES = 400;

	/**
	 * Unique slots filled, or 0 if the game hasn't told us yet.
	 */
	private int obtained;

	/**
	 * Unique slots in the whole log, or 0 if the game hasn't told us yet.
	 */
	private int total;

	/**
	 * Page name, such as "Vorkath", to what's on it.
	 */
	private Map<String, Page> pages = new HashMap<>();

	/**
	 * Names of the newest slots filled, newest first.
	 */
	private List<String> recent = new ArrayList<>();

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Page
	{
		/**
		 * How many items the page has.
		 */
		private int size;

		/**
		 * Ids of the items obtained on it.
		 */
		private List<Integer> items = new ArrayList<>();

		/**
		 * When the member last looked at the page.
		 */
		private long seen;
	}

	public void addRecent(String item)
	{
		recent.remove(item);
		recent.add(0, item);
		while (recent.size() > MAX_RECENT)
		{
			recent.remove(recent.size() - 1);
		}
	}

	@Override
	public void check()
	{
		pages = Checkable.map(pages);
		pages.keySet().retainAll(new ArrayList<>(pages.keySet()).subList(0, Math.min(MAX_PAGES, pages.size())));
		for (Page page : pages.values())
		{
			page.setItems(Checkable.list(page.getItems(), MAX_PAGE_ITEMS));
		}
		recent = Checkable.list(recent, MAX_RECENT);
	}
}
