package com.homebase.hiscores;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
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
	 * The boards a group can be on. Hardcore groups are only listed on the hardcore one, so it's tried first.
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
		 * The group isn't on the hiscores, or isn't on a board's pages where it should be.
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
	 * The group's line on a board, and the line above it. Some ranks are never listed, such as those of
	 * hardcore groups on the regular board, so the group above isn't always one rank higher.
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
		Row row;

		/**
		 * The group one place above, or null when the group is top of its board. Its skills are only known
		 * when it has a name to look it up by.
		 */
		Row above;
		Group aboveGroup;
	}

	private static final String SITE = "https://secure.runescape.com/m=";
	private static final String UNNAMED = "Group name not set";

	/**
	 * How long a lookup is kept, and how soon one that couldn't reach the hiscores is tried again.
	 */
	private static final long KEEP_MS = 30 * 60_000L;
	private static final long RETRY_MS = 5 * 60_000L;

	/**
	 * How many times one page is tried, and the pause before trying again, which grows each time.
	 */
	private static final int TRIES = 3;
	private static final long RETRY_PAUSE_MS = 1_000L;

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
	private static final Pattern ROW = Pattern.compile("<tr class='uc-scroll__table-row' data-test='group-\\d+'>"
		+ "\\s*<td[^>]*>([\\d,]+)</td>\\s*<td[^>]*>\\s*<a[^>]*>([^<]*)</a>\\s*</td>"
		+ "\\s*<td[^>]*>\\s*<span>([\\d,]+)</span>\\s*</td>\\s*<td[^>]*>([\\d,]+)</td>");
	private static final Pattern LAST_PAGE = Pattern.compile("max='(\\d+)'\\s+name='page'");

	private final OkHttpClient http;

	/**
	 * The group last asked about, in lower case, what was found and when.
	 */
	private String asked;
	private Standing standing;
	private long foundAt;
	private boolean running;

	@Inject
	GroupHiscores(OkHttpClient http)
	{
		this.http = http;
	}

	/**
	 * @return what's known about the group, starting a lookup if there's nothing recent. When a lookup
	 * finishes, {@code onChange} runs on the Swing thread.
	 */
	public synchronized Standing standing(String groupName, Runnable onChange)
	{
		String key = normalize(groupName);
		if (!key.equals(asked))
		{
			asked = key;
			standing = null;
		}
		long age = System.currentTimeMillis() - foundAt;
		boolean due = standing == null || age > (standing.getState() == State.FAILED ? RETRY_MS : KEEP_MS);
		if (due && !running)
		{
			running = true;
			// OkHttp's own threads, so a slow hiscores site never holds up RuneLite's shared executor
			http.dispatcher().executorService().execute(() -> finish(key, lookUp(groupName), onChange));
		}
		return standing == null ? Standing.LOOKING : standing;
	}

	private void finish(String key, Standing found, Runnable onChange)
	{
		synchronized (this)
		{
			running = false;
			if (!key.equals(asked))
			{
				return;
			}
			standing = found;
			foundAt = System.currentTimeMillis();
		}
		SwingUtilities.invokeLater(onChange);
	}

	private Standing lookUp(String groupName)
	{
		try
		{
			for (Board board : Board.values())
			{
				Group group = parseGroup(fetch(groupUrl(board, groupName)));
				if (group == null)
				{
					continue;
				}
				Place place = locate(group, page -> parsePage(fetch(pageUrl(board, group.getSize(), page))));
				if (place == null)
				{
					// a hardcore group that has lost its status may only be listed on the regular board
					continue;
				}
				Row above = place.getAbove();
				Group aboveGroup = above == null || above.getName().equals(UNNAMED) ? null : parseGroup(fetch(groupUrl(board, above.getName())));
				return new Standing(State.FOUND, board, group, place.getRow(), above, aboveGroup);
			}
			return Standing.NOT_FOUND;
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Couldn't read the group hiscores", e);
			return Standing.FAILED;
		}
	}

	/**
	 * Read a page, trying again after a pause if the site is busy. The hiscores often time out on one
	 * page in a few, and a search reads around a dozen.
	 */
	private String fetch(HttpUrl url) throws IOException
	{
		for (int attempt = 1; ; attempt++)
		{
			try
			{
				return fetchOnce(url);
			}
			catch (IOException e)
			{
				if (attempt >= TRIES)
				{
					throw e;
				}
			}
			try
			{
				Thread.sleep(RETRY_PAUSE_MS * attempt);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new IOException("Stopped while waiting for the hiscores", e);
			}
		}
	}

	private String fetchOnce(HttpUrl url) throws IOException
	{
		Request request = new Request.Builder().url(url).build();
		try (Response response = http.newCall(request).execute())
		{
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null)
			{
				throw new IOException("The hiscores answered " + response.code());
			}
			return body.string();
		}
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

	interface Pages
	{
		Page get(int page) throws IOException;
	}

	/**
	 * Search a board for the group. The pages run from the highest total level down, so the group's level
	 * and XP say which way to look.
	 *
	 * @return where the group is, or null if it isn't on the pages where it should be
	 */
	static Place locate(Group group, Pages pages) throws IOException
	{
		String name = normalize(group.getName());
		int number = 1;
		Page page = pages.get(number);
		int low = 1;
		int high = page.getLastPage();
		for (int read = 1; ; read++)
		{
			List<Row> rows = page.getRows();
			for (int i = 0; i < rows.size(); i++)
			{
				if (normalize(rows.get(i).getName()).equals(name))
				{
					Row above = i > 0 ? rows.get(i - 1) : null;
					if (above == null && number > 1 && read < MAX_PAGES)
					{
						List<Row> previous = pages.get(number - 1).getRows();
						above = previous.isEmpty() ? null : previous.get(previous.size() - 1);
					}
					return new Place(rows.get(i), above);
				}
			}
			if (rows.isEmpty())
			{
				return null;
			}
			if (compare(group, rows.get(0)) >= 0)
			{
				high = number - 1;
			}
			else if (compare(group, rows.get(rows.size() - 1)) <= 0)
			{
				low = number + 1;
			}
			else
			{
				return null;
			}
			if (low > high || read >= MAX_PAGES)
			{
				return null;
			}
			number = (low + high) >>> 1;
			page = pages.get(number);
		}
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
			rows.add(new Row((int) number(row.group(1)), unescape(row.group(2)).trim(), (int) number(row.group(3)), number(row.group(4))));
		}
		Matcher last = LAST_PAGE.matcher(html);
		return new Page(rows, last.find() ? Integer.parseInt(last.group(1)) : 1);
	}

	/**
	 * @return what it'd take to pass the group one place above, which is a higher total level, or the
	 * same total level and more XP
	 */
	public static String toPass(Group group, Row above)
	{
		int levels = above.getLevel() - group.getTotalLevel();
		if (levels < 0 || (levels == 0 && group.getTotalXp() > above.getXp()))
		{
			return "You've caught them up since the hiscores were read.";
		}
		if (levels == 0)
		{
			return "You're level on total level, so " + formatted(above.getXp() - group.getTotalXp() + 1) + " more XP puts you ahead.";
		}
		if (group.getTotalXp() > above.getXp())
		{
			return plural(levels, "more total level") + " puts you ahead, as you have more XP.";
		}
		return plural(levels + 1, "more total level") + " puts you ahead, or " + levels + " if you also pass their XP.";
	}

	private static String plural(int count, String what)
	{
		return count + " " + what + (count == 1 ? "" : "s");
	}

	private static String formatted(long number)
	{
		return String.format(Locale.ROOT, "%,d", number);
	}

	private static long number(String text)
	{
		return Long.parseLong(text.replace(",", ""));
	}

	static String normalize(String name)
	{
		return name.replace(' ', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
	}

	private static String unescape(String text)
	{
		return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
			.replace("&nbsp;", " ").replace("&amp;", "&");
	}
}
