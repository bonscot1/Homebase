package com.homebase.hiscores;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Where the group is on Jagex's Group Ironman hiscores, and the group one place above it. The hiscores
 * have no data feed for groups, so this reads the same pages a player would see in their browser.
 * <p>
 * Each board lists groups of one size by total level, then contributed XP, 20 to a page. A group's own
 * page doesn't say its rank, so the board is searched page by page for it.
 */
@Slf4j
@Singleton
public class GroupHiscores
{
	/**
	 * The boards a group can be on. Hardcore groups are listed on both, so the hardcore one is tried first.
	 */
	@Getter
	@RequiredArgsConstructor
	public enum Board
	{
		HARDCORE("Hardcore Group Ironman", "hiscore_oldschool_hardcore_ironman"),
		REGULAR("Group Ironman", "hiscore_oldschool_ironman");

		private final String title;
		private final String site;
	}

	public enum State
	{
		LOOKING,
		FOUND,
		/**
		 * There's no group by that name on the hiscores.
		 */
		NOT_FOUND,
		FAILED
	}

	/**
	 * A group as its own page shows it: the totals and each skill's total across its members.
	 */
	@Value
	public static class Group
	{
		String name;
		int size;
		int totalLevel;
		long totalXp;
		Map<String, Integer> levels;
		Map<String, Long> xp;
	}

	/**
	 * One line of a board.
	 */
	@Value
	public static class Row
	{
		int rank;
		String name;
		int level;
		long xp;

		/**
		 * The group's own page. Groups without a name are linked by number, so this finds them too.
		 */
		String link;

		/**
		 * True for a hardcore group that has lost a member, which the hardcore board marks with a skull.
		 */
		boolean perished;
	}

	/**
	 * One page of a board, and how many pages it has.
	 */
	@Value
	static class Page
	{
		List<Row> rows;
		int lastPage;
	}

	/**
	 * The group's line on a board, and the line above it.
	 */
	@Value
	static class Place
	{
		Row row;
		Row above;
	}

	@Value
	public static class Standing
	{
		static final Standing LOOKING = new Standing(State.LOOKING, null, null, null, null, null);
		static final Standing NOT_FOUND = new Standing(State.NOT_FOUND, null, null, null, null, null);
		static final Standing FAILED = new Standing(State.FAILED, null, null, null, null, null);

		State state;
		Board board;
		Group group;

		/**
		 * The group's line on its board, or null if the board doesn't list it.
		 */
		Row row;

		/**
		 * The group one place above, or null when the group is top of its board or wasn't asked about.
		 * Its skills are only known if its page could be read.
		 */
		Row above;
		Group aboveGroup;
	}

	private static final String HOST = "secure.runescape.com";
	private static final String SITE = "https://" + HOST + "/m=";

	/**
	 * How long a lookup is kept, and how soon one that couldn't reach the hiscores is tried again.
	 */
	private static final long KEEP_MS = 30 * 60_000L;
	private static final long RETRY_MS = 5 * 60_000L;

	/**
	 * How many times one page is tried, and how long to wait before trying it again, which grows each time.
	 */
	private static final int TRIES = 3;
	private static final long RETRY_DELAY_MS = 1_000L;

	/**
	 * Most pages read while searching one board. Boards have a few thousand pages at most, so a search
	 * needs around a dozen.
	 */
	static final int MAX_PAGES = 20;

	private static final Pattern SIZE = Pattern.compile("<span class=''>Size</span>\\s*</dt>\\s*<dd[^>]*>(\\d+)</dd>");
	private static final Pattern TOTAL = Pattern.compile(
		"<span class=''>Total (level|contribution XP)</span>.*?</dt>\\s*<dd[^>]*>([\\d,]+)</dd>", Pattern.DOTALL);
	private static final Pattern SKILL = Pattern.compile("<span class='ua-skill-icon ua-skill-icon--[a-z_]+'>([^<]+)</span>\\s*</dt>"
		+ "\\s*<dd class='um-detail-list__value um-detail-list__value--type-(level|xp)'[^>]*>([\\d,]+)</dd>");
	private static final Pattern TITLE = Pattern.compile("<title>[^<]*- Hiscores - ([^<]*)</title>");

	/**
	 * A line of a board: its rank, link, name, level and XP. Prestiged groups have a star before their
	 * name, and perished hardcore groups a skull before their level.
	 */
	private static final Pattern ROW = Pattern.compile("<tr class='uc-scroll__table-row[^']*' data-test='group-\\d+'>"
		+ "\\s*<td[^>]*>([\\d,]+)</td>"
		+ "\\s*<td[^>]*>\\s*(?:<img[^>]*>\\s*)*<a[^>]*href='([^']*)'[^>]*>([^<]*)</a>\\s*</td>"
		+ "\\s*<td[^>]*>\\s*((?:<img[^>]*>\\s*)*)<span>([\\d,]+)</span>\\s*</td>"
		+ "\\s*<td[^>]*>([\\d,]+)</td>");
	private static final Pattern LAST_PAGE = Pattern.compile("max='(\\d+)'\\s+name='page'");

	/**
	 * Most groups whose lookups are kept, for when someone tries a few names to compare with.
	 */
	private static final int MAX_KEPT = 16;

	/**
	 * A lookup of one group, what it found and when.
	 */
	private static final class Lookup
	{
		Standing standing;
		long foundAt;
		boolean running;
	}

	private final OkHttpClient http;
	private final ScheduledExecutorService executor;

	/**
	 * Lookups by group name, oldest used first.
	 */
	private final Map<String, Lookup> lookups = new LinkedHashMap<String, Lookup>(MAX_KEPT, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Lookup> eldest)
		{
			return size() > MAX_KEPT;
		}
	};

	@Inject
	GroupHiscores(OkHttpClient http, ScheduledExecutorService executor)
	{
		this.http = http;
		this.executor = executor;
	}

	/**
	 * @return what's known about our own group, including the group one place above it, starting a
	 * lookup if there's nothing recent. When a lookup finishes, {@code onChange} runs on the Swing thread.
	 */
	public Standing standing(String groupName, Runnable onChange)
	{
		return get(groupName, true, onChange);
	}

	/**
	 * @return the same for any other group, without the group above it
	 */
	public Standing other(String groupName, Runnable onChange)
	{
		return get(groupName, false, onChange);
	}

	private synchronized Standing get(String groupName, boolean withAbove, Runnable onChange)
	{
		Lookup lookup = lookups.computeIfAbsent((withAbove ? "+" : "-") + normalize(groupName), key -> new Lookup());
		long age = System.currentTimeMillis() - lookup.foundAt;
		Standing standing = lookup.standing;
		boolean due = standing == null || age > (standing.getState() == State.FAILED ? RETRY_MS : KEEP_MS);
		if (due && !lookup.running)
		{
			lookup.running = true;
			lookUp(groupName, withAbove, 0, Standing.NOT_FOUND).whenComplete((found, error) ->
			{
				if (error != null)
				{
					log.debug("Couldn't read the group hiscores", error);
				}
				finish(lookup, error == null ? found : Standing.FAILED, onChange);
			});
		}
		return standing == null ? Standing.LOOKING : standing;
	}

	private void finish(Lookup lookup, Standing found, Runnable onChange)
	{
		synchronized (this)
		{
			lookup.running = false;
			lookup.standing = found;
			lookup.foundAt = System.currentTimeMillis();
		}
		SwingUtilities.invokeLater(onChange);
	}

	/**
	 * Look the group up on each board in turn, starting from {@code board}. A hardcore group that has lost a
	 * member counts as a regular group, so it goes on to the regular board, and is only shown on the
	 * hardcore one if the regular one doesn't have it.
	 */
	private CompletableFuture<Standing> lookUp(String groupName, boolean withAbove, int board, Standing fallback)
	{
		if (board >= Board.values().length)
		{
			return CompletableFuture.completedFuture(fallback);
		}
		Board on = Board.values()[board];
		return fetch(groupUrl(on, groupName)).thenCompose(html ->
		{
			Group group = parseGroup(html);
			if (group == null)
			{
				return lookUp(groupName, withAbove, board + 1, fallback);
			}
			return locate(group, page -> fetch(pageUrl(on, group.getSize(), page)).thenApply(GroupHiscores::parsePage))
				.thenCompose(place ->
				{
					Row above = place == null || !withAbove ? null : place.getAbove();
					return details(above).thenCompose(aboveGroup ->
					{
						// even without a rank, a group that's on the hiscores can be compared
						Standing standing = new Standing(State.FOUND, on, group, place == null ? null : place.getRow(), above, aboveGroup);
						if (place != null && !place.getRow().isPerished())
						{
							return CompletableFuture.completedFuture(standing);
						}
						return lookUp(groupName, withAbove, board + 1, fallback.getState() == State.NOT_FOUND ? standing : fallback);
					});
				});
		});
	}

	/**
	 * @return the group on a board line's own page, or null if there's no line or it doesn't link to the hiscores
	 */
	private CompletableFuture<Group> details(Row row)
	{
		HttpUrl url = row == null ? null : HttpUrl.parse(row.getLink());
		if (url == null || !url.host().equals(HOST))
		{
			return CompletableFuture.completedFuture(null);
		}
		return fetch(url).thenApply(GroupHiscores::parseGroup);
	}

	/**
	 * Read a page. The hiscores often time out on one page in a few, and a search reads around a dozen,
	 * so a page that fails is asked for again a little later.
	 */
	private CompletableFuture<String> fetch(HttpUrl url)
	{
		CompletableFuture<String> page = new CompletableFuture<>();
		fetch(url, 1, page);
		return page;
	}

	private void fetch(HttpUrl url, int attempt, CompletableFuture<String> page)
	{
		Request request = new Request.Builder().url(url).build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				tryAgain(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response answer = response)
				{
					ResponseBody body = answer.body();
					if (!answer.isSuccessful() || body == null)
					{
						tryAgain(new IOException("The hiscores answered " + answer.code()));
						return;
					}
					page.complete(body.string());
				}
				catch (IOException e)
				{
					tryAgain(e);
				}
			}

			private void tryAgain(IOException e)
			{
				if (attempt >= TRIES)
				{
					page.completeExceptionally(e);
					return;
				}
				executor.schedule(() -> fetch(url, attempt + 1, page), RETRY_DELAY_MS * attempt, TimeUnit.MILLISECONDS);
			}
		});
	}

	private static HttpUrl groupUrl(Board board, String name)
	{
		return HttpUrl.get(SITE + board.getSite() + "/group-ironman/view-group").newBuilder()
			.addQueryParameter("name", name)
			.build();
	}

	private static HttpUrl pageUrl(Board board, int size, int page)
	{
		return HttpUrl.get(SITE + board.getSite() + "/group-ironman/").newBuilder()
			.addQueryParameter("groupSize", String.valueOf(size))
			.addQueryParameter("page", String.valueOf(page))
			.build();
	}

	/**
	 * A board's pages by number, so the tests can make a board up.
	 */
	interface Pages
	{
		CompletableFuture<Page> get(int page);
	}

	/**
	 * Search a board for the group. The pages run from the highest total level down, so the group's level
	 * and XP say which way to look.
	 *
	 * @return where the group is, or null if it isn't on the pages where it should be
	 */
	static CompletableFuture<Place> locate(Group group, Pages pages)
	{
		return pages.get(1).thenCompose(first -> search(group, pages, 1, first, 1, first.getLastPage(), 1));
	}

	/**
	 * Look for the group on one page, then on the page halfway through the ones it could still be on.
	 */
	private static CompletableFuture<Place> search(Group group, Pages pages, int number, Page page, int low, int high, int read)
	{
		String name = normalize(group.getName());
		List<Row> rows = page.getRows();
		for (int i = 0; i < rows.size(); i++)
		{
			Row row = rows.get(i);
			if (!normalize(row.getName()).equals(name))
			{
				continue;
			}
			if (i > 0 || number == 1 || read >= MAX_PAGES)
			{
				return CompletableFuture.completedFuture(new Place(row, i > 0 ? rows.get(i - 1) : null));
			}
			// first on its page, so the group above is last on the page before
			return pages.get(number - 1).thenApply(previous -> new Place(row,
				previous.getRows().isEmpty() ? null : previous.getRows().get(previous.getRows().size() - 1)));
		}
		if (rows.isEmpty())
		{
			return CompletableFuture.completedFuture(null);
		}
		int nextLow = low;
		int nextHigh = high;
		if (compare(group, rows.get(0)) >= 0)
		{
			nextHigh = number - 1;
		}
		else if (compare(group, rows.get(rows.size() - 1)) <= 0)
		{
			nextLow = number + 1;
		}
		else
		{
			return CompletableFuture.completedFuture(null);
		}
		if (nextLow > nextHigh || read >= MAX_PAGES)
		{
			return CompletableFuture.completedFuture(null);
		}
		int next = (nextLow + nextHigh) >>> 1;
		int searchLow = nextLow;
		int searchHigh = nextHigh;
		return pages.get(next).thenCompose(nextPage -> search(group, pages, next, nextPage, searchLow, searchHigh, read + 1));
	}

	/**
	 * @return above 0 if the group would be listed above the row, below 0 if under it
	 */
	static int compare(Group group, Row row)
	{
		int byLevel = Integer.compare(group.getTotalLevel(), row.getLevel());
		return byLevel != 0 ? byLevel : Long.compare(group.getTotalXp(), row.getXp());
	}

	/**
	 * @return the group on a group's page, or null if the page says there's no such group
	 */
	static Group parseGroup(String html)
	{
		Matcher size = SIZE.matcher(html);
		Matcher title = TITLE.matcher(html);
		if (!size.find() || !title.find())
		{
			return null;
		}
		int level = 0;
		long xp = 0;
		Matcher total = TOTAL.matcher(html);
		while (total.find())
		{
			if (total.group(1).equals("level"))
			{
				level = (int) number(total.group(2));
			}
			else
			{
				xp = number(total.group(2));
			}
		}
		Map<String, Integer> levels = new LinkedHashMap<>();
		Map<String, Long> skillXp = new LinkedHashMap<>();
		Matcher skill = SKILL.matcher(html);
		while (skill.find())
		{
			String name = unescape(skill.group(1)).trim();
			if (skill.group(2).equals("level"))
			{
				levels.put(name, (int) number(skill.group(3)));
			}
			else
			{
				skillXp.put(name, number(skill.group(3)));
			}
		}
		return new Group(unescape(title.group(1)).trim(), Integer.parseInt(size.group(1)), level, xp,
			Collections.unmodifiableMap(levels), Collections.unmodifiableMap(skillXp));
	}

	static Page parsePage(String html)
	{
		List<Row> rows = new ArrayList<>();
		Matcher row = ROW.matcher(html);
		while (row.find())
		{
			rows.add(new Row((int) number(row.group(1)), unescape(row.group(3)).trim(), (int) number(row.group(5)), number(row.group(6)),
				unescape(row.group(2)), row.group(4).contains("death")));
		}
		Matcher last = LAST_PAGE.matcher(html);
		return new Page(rows, last.find() ? Integer.parseInt(last.group(1)) : 1);
	}

	/**
	 * @return in a few words, how our group stands against another with this total level and XP, or what
	 * it'd take to pass them. Groups are ranked by total level, then by XP, so with more XP than them,
	 * drawing level on total level is enough.
	 */
	public static String gap(Group us, int level, long xp)
	{
		int levels = level - us.getTotalLevel();
		long xpBehind = xp - us.getTotalXp();
		if (levels < 0)
		{
			return plural(-levels, "level") + " ahead";
		}
		if (levels == 0)
		{
			if (xpBehind == 0)
			{
				return "Tied";
			}
			return xpBehind < 0 ? "Ahead on XP" : formatted(xpBehind + 1) + " XP to pass";
		}
		return plural(xpBehind < 0 ? levels : levels + 1, "level") + " to pass";
	}

	private static String plural(int count, String what)
	{
		return formatted(count) + " " + what + (count == 1 ? "" : "s");
	}

	private static String formatted(long number)
	{
		return String.format(Locale.ROOT, "%,d", number);
	}

	private static long number(String text)
	{
		return Long.parseLong(text.replace(",", ""));
	}

	/**
	 * @return a group name the way the hiscores match it: lower case, with single plain spaces
	 */
	public static String normalize(String name)
	{
		return name.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	private static String unescape(String text)
	{
		return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
			.replace("&nbsp;", " ").replace("&amp;", "&");
	}
}
