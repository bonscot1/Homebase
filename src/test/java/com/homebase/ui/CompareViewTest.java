package com.homebase.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
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
	public void theLowerRankIsAhead()
	{
		assertTrue(CompareView.compareRanks(1_200, 85_000) > 0);
		assertTrue(CompareView.compareRanks(85_000, 1_200) < 0);
		assertEquals(0, CompareView.compareRanks(null, 1_200));
	}

	@Test
	public void ranksAreShortenedToFitTheirColumn()
	{
		assertEquals("-", CompareView.rankText(null));
		assertEquals("8,412", CompareView.rankText(8_412));
		assertEquals("99,999", CompareView.rankText(99_999));
		assertEquals("100K", CompareView.rankText(100_000));
		assertEquals("312K", CompareView.rankText(312_987));
		assertEquals("999K", CompareView.rankText(999_999));
		assertEquals("1.0M", CompareView.rankText(1_000_000));
		assertEquals("1.9M", CompareView.rankText(1_999_999));
	}
}
