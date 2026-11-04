package com.homebase.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.homebase.storage.StorageHistory;
import org.junit.Test;

public class StorageHistoryViewTest
{
	private static final int WHIP = 4151;

	private static String line(int amount)
	{
		return StorageHistoryView.text(new StorageHistory.Line("Mundell", WHIP, amount, System.currentTimeMillis()));
	}

	@Test
	public void linesSayWhoDidWhatWithHowManyAndWhen()
	{
		String out = line(-5);
		assertTrue(out, out.contains("Mundell"));
		assertTrue(out, out.contains(" took out " + ItemNames.get(WHIP) + " ×5"));
		assertTrue(out, out.contains("just now"));

		String in = line(5);
		assertTrue(in, in.contains(" put in " + ItemNames.get(WHIP) + " ×5"));
	}

	@Test
	public void oneOfSomethingHasNoCount()
	{
		String out = line(-1);
		assertTrue(out, out.contains(ItemNames.get(WHIP)));
		assertFalse(out, out.contains("×"));
	}

	@Test
	public void owedLinesSayWhoHasHowMany()
	{
		String out = StorageHistoryView.text(new StorageHistory.Owed("Mundell", WHIP, 3, System.currentTimeMillis()));
		assertTrue(out, out.contains("Mundell"));
		assertTrue(out, out.contains(": " + ItemNames.get(WHIP) + " ×3"));
	}

	@Test
	public void namesCantAddFormatting()
	{
		String image = StorageHistoryView.text(new StorageHistory.Line("<img src='http://x'>", WHIP, 1, 1));
		assertFalse(image, image.contains("<img"));

		// shown as it was typed, not turned into an ampersand
		String entity = StorageHistoryView.text(new StorageHistory.Line("A&amp;B", WHIP, 1, 1));
		assertTrue(entity, entity.contains("A&amp;amp;B"));
	}

	@Test
	public void aMissingNameStillReads()
	{
		String out = StorageHistoryView.text(new StorageHistory.Owed("<>", WHIP, 1, 1));
		assertTrue(out, out.contains("Someone"));
	}
}
