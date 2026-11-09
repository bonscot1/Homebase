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
	public void bigNumbersAreShortenedToFitTheirColumn()
	{
		assertEquals("84,120", CompareView.shortNumber(84_120));
		assertEquals("312K", CompareView.shortNumber(312_987));
		assertEquals("8.1M", CompareView.shortNumber(8_120_331));
		assertEquals("999.9M", CompareView.shortNumber(999_999_999));
		assertEquals("1.6B", CompareView.shortNumber(1_684_997_520L));
	}
}
