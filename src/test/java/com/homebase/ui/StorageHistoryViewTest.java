package com.homebase.ui;

import com.homebase.storage.StorageHistory;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class StorageHistoryViewTest
{
	private static final int WHIP = 4151;
	private static final int COAL = 453;

	@Test
	public void aVisitHasABlockForWhatWasTakenOutAndAnotherForWhatWasPutIn()
	{
		List<StorageHistory.Line> lines = Arrays.asList(
			new StorageHistory.Line("Mundell", WHIP, -5, 200),
			new StorageHistory.Line("Mundell", COAL, 3, 200),
			new StorageHistory.Line("Mundell", COAL, -2, 200),
			new StorageHistory.Line("Bob", WHIP, 1, 100));
		List<ItemBlocks.Block> blocks = StorageHistoryView.historyBlocks(lines, 10).getBlocks();

		assertEquals(3, blocks.size());
		assertEquals("Mundell", blocks.get(0).getHeading());
		assertTrue(blocks.get(0).getDetail(), blocks.get(0).getDetail().startsWith("Took out"));
		assertEquals(2, blocks.get(0).getItems().size());
		assertEquals(5, blocks.get(0).getItems().get(0).getQuantity());
		assertTrue(blocks.get(1).getDetail().startsWith("Put in"));
		assertEquals("Bob", blocks.get(2).getHeading());
	}

	@Test
	public void savesCloseTogetherAreOneVisit()
	{
		long hour = 60 * 60_000;
		List<StorageHistory.Line> lines = Arrays.asList(
			new StorageHistory.Line("Mundell", WHIP, 2, 10 * hour),
			new StorageHistory.Line("Mundell", COAL, 4, 10 * hour - 5 * 60_000),
			new StorageHistory.Line("Mundell", WHIP, 1, 10 * hour - 8 * 60_000),
			new StorageHistory.Line("Mundell", COAL, 1, 9 * hour));
		List<ItemBlocks.Block> blocks = StorageHistoryView.historyBlocks(lines, 10).getBlocks();

		assertEquals(2, blocks.size());
		assertEquals(2, blocks.get(0).getItems().size());
		assertEquals("the same item adds up", 3, blocks.get(0).getItems().get(0).getQuantity());
	}

	@Test
	public void olderVisitsAreCountedNotShown()
	{
		long hour = 60 * 60_000;
		List<StorageHistory.Line> lines = Arrays.asList(
			new StorageHistory.Line("Mundell", WHIP, -5, 3 * hour),
			new StorageHistory.Line("Bob", WHIP, 5, 2 * hour),
			new StorageHistory.Line("Mundell", COAL, -1, hour));
		StorageHistoryView.Shown shown = StorageHistoryView.historyBlocks(lines, 2);
		assertEquals(2, shown.getBlocks().size());
		assertEquals(1, shown.getOlder());
	}

	@Test
	public void owedIsABlockForEachMember()
	{
		List<StorageHistory.Owed> owed = Arrays.asList(
			new StorageHistory.Owed("Mundell", WHIP, 3, 100),
			new StorageHistory.Owed("Bob", COAL, 1, 100),
			new StorageHistory.Owed("Mundell", COAL, 2, 200));
		List<ItemBlocks.Block> blocks = StorageHistoryView.owedBlocks(owed, item ->
		{
		});
		assertEquals(2, blocks.size());
		assertEquals("Mundell", blocks.get(0).getHeading());
		assertEquals(2, blocks.get(0).getItems().size());
		assertTrue(blocks.get(0).getDetail().startsWith("Owes these back"));
	}

	@Test
	public void aMissingNameStillReads()
	{
		List<ItemBlocks.Block> blocks = StorageHistoryView.owedBlocks(
			Collections.singletonList(new StorageHistory.Owed("<>", WHIP, 1, 1)), null);
		assertEquals("Someone", blocks.get(0).getHeading());
	}

	@Test
	public void namesCantAddFormatting()
	{
		List<ItemBlocks.Block> blocks = StorageHistoryView.historyBlocks(
			Collections.singletonList(new StorageHistory.Line("<img src='http://x'>", WHIP, 1, 1)), 10).getBlocks();
		String shown = String.join(" ", labels(ItemBlocks.list(blocks, null)));
		assertFalse(shown, shown.contains("<img"));
	}

	private static List<String> labels(Container parent)
	{
		List<String> out = new ArrayList<>();
		for (Component child : parent.getComponents())
		{
			if (child instanceof JLabel)
			{
				out.add(((JLabel) child).getText());
			}
		}
		return out;
	}
}
