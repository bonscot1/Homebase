package com.homebase.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.homebase.data.KillCountsData;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
import org.junit.Test;

public class CompareViewTest
{
	@Test
	public void theWinnerIsAheadOnMoreRows()
	{
		int[] score = CompareView.score(Arrays.asList(
			new CompareView.Line(new JLabel(), "99", "70", 1),
			new CompareView.Line(new JLabel(), "1", "5", -1),
			new CompareView.Line(new JLabel(), "3", "3", 0),
			new CompareView.Line(new JLabel(), "40", "2", 1)));
		assertArrayEquals(new int[]{2, 1}, score);
	}

	@Test
	public void whoeverIsHigherIsAheadAndAMissingNumberIsNobodysWin()
	{
		assertTrue(CompareView.compare(99, 70) > 0);
		assertTrue(CompareView.compare(70, 99) < 0);
		assertEquals(0, CompareView.compare(50, 50));
		assertEquals(0, CompareView.compare(null, 50));
		assertEquals(0, CompareView.compare(50, null));
	}

	@Test
	public void bigNumbersAreShortenedToFitTheirColumn()
	{
		assertEquals("84,120", CompareView.shortNumber(84_120));
		assertEquals("312K", CompareView.shortNumber(312_987));
		assertEquals("8.1M", CompareView.shortNumber(8_120_331));
		assertEquals("999.9M", CompareView.shortNumber(999_999_999));
		assertEquals("1.6B", CompareView.shortNumber(1_684_997_520L));
	}

	@Test
	public void killCountsGoMostKilledFirstWithNamesWrittenEitherWayAsOne()
	{
		KillCountsData a = new KillCountsData();
		a.getCounts().put("Zulrah", 40);
		a.getCounts().put("The Gauntlet", 3);
		a.getCounts().put("Vorkath", 0);
		KillCountsData b = new KillCountsData();
		b.getCounts().put("Gauntlet", 12);
		b.getCounts().put("Vorkath", 0);

		List<CompareView.Line> lines = CompareView.kills(a, b);

		// nobody has killed Vorkath, so it's left out
		assertEquals(2, lines.size());
		assertEquals("Zulrah", text(lines.get(0)));
		assertEquals("40", lines.get(0).a);
		assertEquals("Gauntlet", text(lines.get(1)));
		assertEquals("3", lines.get(1).a);
		assertEquals("12", lines.get(1).b);
		assertTrue(lines.get(1).compare < 0);
	}

	@Test
	public void bossNamesFromAGroupmateCantBeHtml()
	{
		KillCountsData theirs = new KillCountsData();
		theirs.getCounts().put("<html><img src='http://x.co/a'>Zulrah", 5);
		theirs.getBestTimes().put("<html><img src='http://x.co/a'>Vorkath", 70.0);

		String kills = text(CompareView.kills(new KillCountsData(), theirs).get(0));
		String times = text(CompareView.bestTimes(new KillCountsData(), theirs).get(0));

		assertTrue(kills, !kills.contains("<") && kills.endsWith("Zulrah"));
		assertTrue(times, !times.contains("<") && times.endsWith("Vorkath"));
	}

	private static String text(CompareView.Line line)
	{
		return ((JLabel) line.label).getText();
	}
}
