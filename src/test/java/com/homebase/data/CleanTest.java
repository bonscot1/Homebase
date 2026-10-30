package com.homebase.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class CleanTest
{
	@Test
	public void keepsOrdinaryText()
	{
		assertEquals("Rune scimitar", Clean.text("  Rune scimitar ", 60));
	}

	@Test
	public void stripsTagsSoLabelsCantBecomeHtml()
	{
		assertEquals("htmlimg src='http://x'", Clean.text("<html><img src='http://x'>", 60));
		assertEquals("col=ff0000hi", Clean.text("<col=ff0000>hi", 60));
	}

	@Test
	public void stripsControlCharacters()
	{
		assertEquals("ab", Clean.text("a\nb\u0000", 60));
	}

	@Test
	public void truncates()
	{
		assertEquals("abc", Clean.text("abcdef", 3));
	}

	@Test
	public void emptyBecomesNull()
	{
		assertNull(Clean.text("<>", 60));
		assertNull(Clean.text(null, 60));
	}
}
