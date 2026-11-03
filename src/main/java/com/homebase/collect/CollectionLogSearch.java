package com.homebase.collect;

import com.homebase.data.Clean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.eventbus.Subscribe;

/**
 * Reads the whole collection log at once. When the player clicks Search in the collection log, the
 * game runs a script once for every item they've obtained, so watching that script gives every page
 * without opening each one. Which items belong on which page comes from the game's cache.
 * <p>
 * The script and cache ids are the ones the WikiSync plugin uses for the same job. Runs on the client
 * thread.
 */
@Slf4j
@Singleton
public class CollectionLogSearch
{
	/**
	 * The script run for each obtained item while the search loads, with the item id and how many as
	 * its second and third arguments.
	 */
	static final int ITEM_SCRIPT = 4100;
	private static final int ITEM_ID_ARG = 1;
	private static final int QUANTITY_ARG = 2;

	/**
	 * How the cache lays out the collection log: an enum of tabs, each tab pointing to an enum of
	 * pages, and each page naming itself and pointing to an enum of its items.
	 */
	private static final int TABS_ENUM = 2102;
	private static final int TAB_PAGES_PARAM = 683;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_PARAM = 690;

	/**
	 * Items the cache lists under one id that the search reports under another.
	 */
	private static final int REPLACEMENTS_ENUM = 3721;

	/**
	 * Ticks without another item before the search counts as loaded.
	 */
	private static final int SETTLE_TICKS = 2;

	/**
	 * A page as the cache lays it out: its name and every item on it.
	 */
	@Value
	static class PageLayout
	{
		String name;
		int[] items;
	}

	/**
	 * A page after a search: how many items it has, and which of them the player has.
	 */
	@Value
	public static class PageResult
	{
		int size;
		List<Integer> obtained;
	}

	private final Client client;

	private List<PageLayout> layout;
	private Map<Integer, Integer> replacements = Collections.emptyMap();
	private final Set<Integer> found = new HashSet<>();
	private int lastFoundTick;

	@Inject
	private CollectionLogSearch(Client client)
	{
		this.client = client;
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != ITEM_SCRIPT || event.getScriptEvent() == null)
		{
			return;
		}
		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length <= QUANTITY_ARG || !(args[ITEM_ID_ARG] instanceof Integer)
			|| !(args[QUANTITY_ARG] instanceof Integer))
		{
			return;
		}
		// the search also lists items not obtained yet, with none of them
		if ((Integer) args[QUANTITY_ARG] > 0)
		{
			found.add((Integer) args[ITEM_ID_ARG]);
		}
		lastFoundTick = client.getTickCount();
	}

	/**
	 * @return once a search has finished loading, the obtained items on every page with any, by page
	 * name, along with each page's size; otherwise null
	 */
	public Map<String, PageResult> takeFinished()
	{
		if (found.isEmpty() || client.getTickCount() - lastFoundTick < SETTLE_TICKS)
		{
			return null;
		}
		Set<Integer> obtained = new HashSet<>(found);
		found.clear();
		List<PageLayout> pages = layout();
		if (pages.isEmpty())
		{
			return null;
		}
		return pagesWith(pages, obtained, replacements);
	}

	/**
	 * Sort the obtained items into pages. Pages with nothing obtained are left out, the same as pages
	 * never opened.
	 */
	static Map<String, PageResult> pagesWith(List<PageLayout> pages, Set<Integer> obtained, Map<Integer, Integer> replacements)
	{
		Map<String, PageResult> out = new LinkedHashMap<>();
		for (PageLayout page : pages)
		{
			List<Integer> got = new ArrayList<>();
			for (int itemId : page.getItems())
			{
				if (obtained.contains(itemId) || obtained.contains(replacements.getOrDefault(itemId, itemId)))
				{
					got.add(itemId);
				}
			}
			if (!got.isEmpty())
			{
				out.put(page.getName(), new PageResult(page.getItems().length, got));
			}
		}
		return out;
	}

	/**
	 * @return every page's name and items, read from the cache the first time it's needed
	 */
	private List<PageLayout> layout()
	{
		if (layout == null || layout.isEmpty())
		{
			layout = readLayout();
			replacements = readReplacements();
			log.debug("Collection log layout: {} pages, starting with {}", layout.size(),
				layout.isEmpty() ? "none" : layout.get(0).getName() + " (" + layout.get(0).getItems().length + " items)");
		}
		return layout;
	}

	private List<PageLayout> readLayout()
	{
		List<PageLayout> pages = new ArrayList<>();
		EnumComposition tabs = client.getEnum(TABS_ENUM);
		if (tabs == null)
		{
			return pages;
		}
		for (int tabStructId : tabs.getIntVals())
		{
			StructComposition tab = client.getStructComposition(tabStructId);
			EnumComposition tabPages = tab == null ? null : client.getEnum(tab.getIntValue(TAB_PAGES_PARAM));
			if (tabPages == null)
			{
				continue;
			}
			for (int pageStructId : tabPages.getIntVals())
			{
				StructComposition page = client.getStructComposition(pageStructId);
				EnumComposition items = page == null ? null : client.getEnum(page.getIntValue(PAGE_ITEMS_PARAM));
				String name = page == null ? null : Clean.text(page.getStringValue(PAGE_NAME_PARAM), 60);
				if (items != null && name != null)
				{
					pages.add(new PageLayout(name, items.getIntVals()));
				}
			}
		}
		return pages;
	}

	private Map<Integer, Integer> readReplacements()
	{
		Map<Integer, Integer> out = new HashMap<>();
		EnumComposition replaced = client.getEnum(REPLACEMENTS_ENUM);
		if (replaced != null)
		{
			int[] from = replaced.getKeys();
			int[] to = replaced.getIntVals();
			for (int i = 0; i < from.length && i < to.length; i++)
			{
				out.put(from[i], to[i]);
			}
		}
		return out;
	}

	/**
	 * Read the layout now, so a problem with it shows in the log straight away. Runs on the client
	 * thread.
	 */
	public void preload()
	{
		layout();
	}

	public void reset()
	{
		found.clear();
	}
}
