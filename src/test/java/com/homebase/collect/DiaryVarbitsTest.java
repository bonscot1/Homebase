package com.homebase.collect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class DiaryVarbitsTest
{
	@Test
	public void startedKaramjaDiaryIsNotDone()
	{
		assertFalse(DiaryVarbits.isDone(VarbitID.ATJUN_EASY_DONE, 0));
		assertFalse(DiaryVarbits.isDone(VarbitID.ATJUN_EASY_DONE, 1));
		assertTrue(DiaryVarbits.isDone(VarbitID.ATJUN_EASY_DONE, 2));
		assertFalse(DiaryVarbits.isDone(VarbitID.ATJUN_HARD_DONE, 1));
	}

	@Test
	public void otherDiariesAreFlags()
	{
		assertTrue(DiaryVarbits.isDone(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 1));
		assertFalse(DiaryVarbits.isDone(VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, 0));
		assertTrue(DiaryVarbits.isDone(VarbitID.KARAMJA_DIARY_ELITE_COMPLETE, 1));
	}
}
