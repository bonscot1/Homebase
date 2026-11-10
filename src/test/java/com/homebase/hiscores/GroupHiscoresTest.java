package com.homebase.hiscores;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.homebase.hiscores.GroupHiscores.Group;
import com.homebase.hiscores.GroupHiscores.Page;
import com.homebase.hiscores.GroupHiscores.Place;
import com.homebase.hiscores.GroupHiscores.Row;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;

public class GroupHiscoresTest
{
	private static final int PER_PAGE = 20;
	private static final String SITE = "https://secure.runescape.com/m=hiscore_oldschool_hardcore_ironman/group-ironman/view-group?name=";

	@Test
	public void readsAGroupsPage()
	{
		Group group = GroupHiscores.parseGroup(groupPage("muddy boots", 4, 2_725, 8_120_331L));

		assertNotNull(group);
		assertEquals("muddy boots", group.getName());
		assertEquals(4, group.getSize());
		assertEquals(2_725, group.getTotalLevel());
		assertEquals(8_120_331L, group.getTotalXp());
		assertEquals(3, group.getLevels().size());
		assertEquals(Integer.valueOf(165), group.getLevels().get("Attack"));
		assertEquals(Integer.valueOf(202), group.getLevels().get("Sailing"));
		assertEquals(Long.valueOf(3_628_849L), group.getXp().get("Sailing"));
	}

	@Test
	public void aMissingGroupIsNull()
	{
		assertNull(GroupHiscores.parseGroup("<html><head><title>Old School Group Ironman - group not found</title></head>"
			+ "<body>Unable to find group with name nobody</body></html>"));
	}

	@Test
	public void readsABoardPage()
	{
		Page page = GroupHiscores.parsePage(boardPage(1005, Arrays.asList(
			new Row(1, "barrow &amp; co", 11_770, 1_684_997_520L, SITE + "barrow co", false),
			new Row(3, "sea snail&#39;s", 11_723, 1_552_582_052L, SITE + "sea snails", false))));

		assertEquals(1005, page.getLastPage());
		assertEquals(2, page.getRows().size());
		assertEquals(new Row(1, "barrow & co", 11_770, 1_684_997_520L, SITE + "barrow co", false), page.getRows().get(0));
		assertEquals("sea snail's", page.getRows().get(1).getName());
	}

	@Test
	public void readsStarredAndSkulledLines()
	{
		// a star marks a prestiged group, and a skull a hardcore group that has lost a member
		Page page = GroupHiscores.parsePage(boardPage(3, Arrays.asList(
			new Row(5, "starry", 7_917, 480_927_008L, SITE + "starry", false),
			new Row(6, "Group name not set", 7_910, 1_112_761_452L, "https://secure.runescape.com/m=x/view-group?id=164939", true),
			new Row(7, "plain", 7_903, 566_573_391L, SITE + "plain", false))));

		assertEquals(3, page.getRows().size());
		assertEquals("starry", page.getRows().get(0).getName());
		assertTrue(page.getRows().get(1).isPerished());
		assertEquals("https://secure.runescape.com/m=x/view-group?id=164939", page.getRows().get(1).getLink());
		assertEquals(7_903, page.getRows().get(2).getLevel());
	}

	@Test
	public void findsTheGroupAndTheOneAboveInAFewPages()
	{
		List<Row> board = board(30_000);
		Row ours = board.get(12_345);
		int[] reads = new int[1];

		Place place = GroupHiscores.locate(group(ours), number ->
		{
			reads[0]++;
			return page(board, number);
		}).join();

		assertNotNull(place);
		assertEquals(ours, place.getRow());
		assertEquals(board.get(12_344), place.getAbove());
		assertTrue("read " + reads[0] + " pages", reads[0] <= 12);
	}

	@Test
	public void theGroupAboveCanBeOnThePageBefore()
	{
		List<Row> board = board(2_000);
		Row ours = board.get(PER_PAGE * 7);

		Place place = GroupHiscores.locate(group(ours), number -> page(board, number)).join();

		assertNotNull(place);
		assertEquals(board.get(PER_PAGE * 7 - 1), place.getAbove());
	}

	@Test
	public void theTopGroupHasNobodyAbove()
	{
		List<Row> board = board(500);

		Place place = GroupHiscores.locate(group(board.get(0)), number -> page(board, number)).join();

		assertNotNull(place);
		assertNull(place.getAbove());
	}

	@Test
	public void aGroupThatIsntListedIsntFound()
	{
		List<Row> board = board(5_000);
		Row hidden = board.remove(2_222);

		assertNull(GroupHiscores.locate(group(hidden), number -> page(board, number)).join());
	}

	@Test
	public void saysWhatItTakesToPassAGroupAhead()
	{
		Group us = new Group("us", 4, 2_725, 8_120_331L, Collections.emptyMap(), Collections.emptyMap());

		// with more XP than them, drawing level is enough
		assertEquals("2 levels to pass", GroupHiscores.gap(us, 2_727, 4_975_274L));
		assertEquals("3 levels to pass", GroupHiscores.gap(us, 2_727, 9_000_000L));
		assertEquals("1 level to pass", GroupHiscores.gap(us, 2_726, 1_000L));
		assertEquals("879,670 XP to pass", GroupHiscores.gap(us, 2_725, 9_000_000L));
		assertEquals("6,109 levels to pass", GroupHiscores.gap(us, 8_833, 1_000_000_000L));
	}

	@Test
	public void saysHowFarAheadOfAGroupBehindWeAre()
	{
		Group us = new Group("us", 4, 2_725, 8_120_331L, Collections.emptyMap(), Collections.emptyMap());

		assertEquals("1 level ahead", GroupHiscores.gap(us, 2_724, 90_000_000L));
		assertEquals("25 levels ahead", GroupHiscores.gap(us, 2_700, 1_000L));
		assertEquals("Ahead on XP", GroupHiscores.gap(us, 2_725, 8_000_000L));
		assertEquals("Tied", GroupHiscores.gap(us, 2_725, 8_120_331L));
	}

	@Test
	public void namesMatchWhateverTheGameDoesWithSpaces()
	{
		assertEquals("muddy boots", GroupHiscores.normalize("Muddy\u00A0Boots "));
	}

	/**
	 * A board of made-up groups, from the highest total level down, with every rank listed.
	 */
	private static List<Row> board(int groups)
	{
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < groups; i++)
		{
			// a few groups share each total level, ordered by XP
			int level = 10_000 - i / 3;
			long xp = 1_000_000L * (3 - i % 3);
			rows.add(new Row(i + 1, "group " + i, level, xp, SITE + "group " + i, i % 7 == 0));
		}
		return rows;
	}

	private static Group group(Row row)
	{
		return new Group(row.getName(), 4, row.getLevel(), row.getXp(), Collections.emptyMap(), Collections.emptyMap());
	}

	private static CompletableFuture<Page> page(List<Row> board, int number)
	{
		int from = Math.min(board.size(), (number - 1) * PER_PAGE);
		int to = Math.min(board.size(), from + PER_PAGE);
		int last = (board.size() + PER_PAGE - 1) / PER_PAGE;
		return CompletableFuture.completedFuture(GroupHiscores.parsePage(boardPage(last, board.subList(from, to))));
	}

	/**
	 * A board page, marked up the way the hiscores mark theirs up.
	 */
	private static String boardPage(int lastPage, List<Row> rows)
	{
		StringBuilder html = new StringBuilder("<table><tbody>");
		for (Row row : rows)
		{
			boolean starred = row.getRank() % 5 == 0;
			html.append("<tr class='uc-scroll__table-row").append(row.isPerished() ? " uc-scroll__table-row--type-death" : "")
				.append("' data-test='group-").append(row.getRank() + 100).append("'>\n")
				.append("  <td class='uc-scroll__table-cell' style='text-align: right;'>").append(String.format(Locale.ROOT, "%,d", row.getRank())).append("</td>\n")
				.append("  <td class='uc-scroll__table-cell'>\n    ")
				.append(starred ? "<img class='uc-prestige-icon' src='prestige_icon.svg' alt=\"star icon\" title=\"Group Prestiged\" />" : "")
				.append("<a class='uc-scroll__link' href='").append(row.getLink()).append("'>").append(row.getName()).append("</a>\n  </td>\n")
				.append("  <td class='uc-scroll__table-cell'>\n    ")
				.append(row.isPerished() ? "<img class='ua-death-icon' src='skull.svg' alt=\"skull icon\" title=\"Group Perished\" />" : "")
				.append("<span>").append(String.format(Locale.ROOT, "%,d", row.getLevel())).append("</span>\n  </td>\n")
				.append("  <td class='uc-scroll__table-cell'>").append(String.format(Locale.ROOT, "%,d", row.getXp())).append("</td>\n")
				.append("</tr>\n");
		}
		html.append("</tbody></table><form><label for='gotopage' class='gotoPage'>Go to page "
			+ "<input type='number' min='1' max='").append(lastPage).append("' name='page' class='paginationWrap__number text' id='gotopage' required />"
			+ "</label></form>");
		return html.toString();
	}

	private static String groupPage(String name, int size, int level, long xp)
	{
		return "<html><head><title>Old School Hardcore Group Ironman - Hiscores - " + name + "</title></head><body><dl class='um-detail-list'>\n"
			+ stat("<span class=''>Size</span>", "stat", String.valueOf(size))
			+ stat("<span class=''>Total level</span>\n<button class='ua-expand' title=\"View breakdown\">Expand</button>", "total",
				String.format(Locale.ROOT, "%,d", level))
			+ stat(skill("attack", "Attack"), "level", "165")
			+ stat(skill("hitpoints", "Hitpoints"), "level", "172")
			+ stat(skill("sailing", "Sailing"), "level", "202")
			+ stat("<span class=''>Total contribution XP</span>\n<button class='ua-expand' title=\"View breakdown\">Expand</button>", "total",
				String.format(Locale.ROOT, "%,d", xp))
			+ stat(skill("attack", "Attack"), "xp", "219,831")
			+ stat(skill("hitpoints", "Hitpoints"), "xp", "295,011")
			+ stat(skill("sailing", "Sailing"), "xp", "3,628,849")
			+ "</dl></body></html>";
	}

	private static String skill(String key, String name)
	{
		return "<span class='ua-skill-icon ua-skill-icon--" + key + "'>" + name + "</span>";
	}

	private static String stat(String title, String type, String value)
	{
		return "<dt class='um-detail-list__title um-detail-list__title--type-" + type + "' data-js-type='" + type + "'>\n    " + title + "\n</dt>\n"
			+ "<dd class='um-detail-list__value um-detail-list__value--type-" + type + "' data-js-type='" + type + "'>" + value + "</dd>\n";
	}
}
