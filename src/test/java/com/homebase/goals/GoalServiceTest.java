package com.homebase.goals;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.homebase.bounties.BountyService;
import com.homebase.collect.LocalCollector;
import com.homebase.data.BountyData;
import com.homebase.data.DiariesData;
import com.homebase.data.GoalData;
import com.homebase.data.GroupIndex;
import com.homebase.data.GroupView;
import com.homebase.data.KillCountsData;
import com.homebase.data.ProfileData;
import com.homebase.data.QuestsData;
import com.homebase.data.Section;
import com.homebase.data.SkillsData;
import com.homebase.sync.GroupStore;
import com.homebase.sync.Keys;
import com.homebase.sync.SyncEngine;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;

public class GoalServiceTest
{
	private static final int LEVEL_50_XP = 101_333;

	private final Gson gson = new Gson();
	private GroupStore store;
	private SyncEngine engine;
	private GoalService goals;

	@Before
	public void setUp() throws Exception
	{
		store = new GroupStore();
		long[] clock = {1_000};
		engine = new SyncEngine(store, () -> clock[0]++, new Random(1));
		Constructor<GoalService> c = GoalService.class.getDeclaredConstructor(LocalCollector.class, Gson.class);
		c.setAccessible(true);
		goals = c.newInstance(null, gson);
		goals.setGroup(engine, new GroupView(store, gson), p -> true);
	}

	@Test
	public void skillGoalProgress()
	{
		member("alice", "Alice", LEVEL_50_XP);
		member("bob", "Bob", 0);
		GoalData goal = goal("g1", GoalData.Type.SKILL, "Firemaking", 50);

		assertTrue(goals.progress(goal, "alice").isDone());
		assertFalse(goals.progress(goal, "bob").isDone());
		assertEquals("1/50", goals.progress(goal, "bob").getText());
		assertEquals("?", goals.progress(goal, "nobody").getText());
		assertEquals("50 Firemaking", GoalService.title(goal));
	}

	@Test
	public void questDiaryAndKillGoals()
	{
		member("alice", "Alice", 0);
		QuestsData quests = new QuestsData();
		quests.getQuests().put("Dragon Slayer I", QuestsData.FINISHED);
		engine.publish(Section.QUESTS.keyFor("alice"), "alice", gson.toJsonTree(quests));
		DiariesData diaries = new DiariesData();
		diaries.getDone().put("Varrock", 1);
		engine.publish(Section.DIARIES.keyFor("alice"), "alice", gson.toJsonTree(diaries));
		KillCountsData kills = new KillCountsData();
		kills.getCounts().put("Vorkath", 60);
		engine.publish(Section.KILL_COUNTS.keyFor("alice"), "alice", gson.toJsonTree(kills));

		assertTrue(goals.progress(goal("q", GoalData.Type.QUEST, "dragon slayer i", 0), "alice").isDone());
		assertTrue(goals.progress(goal("d", GoalData.Type.DIARY, "Varrock Easy", 0), "alice").isDone());
		assertFalse(goals.progress(goal("d2", GoalData.Type.DIARY, "Varrock Medium", 0), "alice").isDone());
		assertTrue(goals.progress(goal("k", GoalData.Type.KILL_COUNT, "vorkath", 50), "alice").isDone());
		assertEquals("60/100", goals.progress(goal("k2", GoalData.Type.KILL_COUNT, "Vorkath", 100), "alice").getText());
	}

	@Test
	public void announcesOnlyNewCompletions()
	{
		member("alice", "Alice", LEVEL_50_XP);
		member("bob", "Bob", 0);
		goal("g1", GoalData.Type.SKILL, "Firemaking", 50);

		// the first look only notes that Alice is already there
		assertTrue(goals.checkCompletions().isEmpty());

		member("bob", "Bob", LEVEL_50_XP);
		assertEquals(Collections.singletonList("Bob reached the goal: 50 Firemaking."), goals.checkCompletions());
		assertTrue(goals.checkCompletions().isEmpty());

		// a goal set after Alice already reached it isn't announced as news
		goal("g2", GoalData.Type.SKILL, "Firemaking", 40);
		assertTrue(goals.checkCompletions().isEmpty());
	}

	@Test
	public void goalsCanTargetOneMember()
	{
		member("alice", "Alice", 0);
		member("bob", "Bob", 0);
		GoalData data = new GoalData();
		data.setType(GoalData.Type.SKILL.name());
		data.setTarget("Firemaking");
		data.setAmount(50);
		data.setMembers(Collections.singletonList("bob"));
		assertTrue(data.appliesTo("bob"));
		assertFalse(data.appliesTo("alice"));
	}

	@Test
	public void validatesGoals()
	{
		assertNull(GoalService.skill("Not a skill"));
		assertArrayEquals(new int[]{9, 0}, GoalService.diary("Varrock Easy"));
		assertNull(GoalService.diary("Varrock Impossible"));
		GoalData tooHigh = new GoalData();
		tooHigh.setType(GoalData.Type.SKILL.name());
		tooHigh.setTarget("Firemaking");
		tooHigh.setAmount(120);
		assertFalse(GoalService.isValid(tooHigh));
		GoalData unknown = new GoalData();
		unknown.setType("SOMETHING_NEW");
		assertNull(unknown.typeOrNull());
	}

	@Test
	public void bountyHoldsUntilLockedMemberReachesGoal() throws Exception
	{
		member("alice", "Alice", 0);
		member("bob", "Bob", 0);
		goal("g1", GoalData.Type.SKILL, "Firemaking", 50);
		Constructor<BountyService> c = BountyService.class.getDeclaredConstructor(LocalCollector.class, GoalService.class, GroupIndex.class,
			Gson.class);
		c.setAccessible(true);
		BountyService bounties = c.newInstance(null, goals, null, gson);

		BountyData bounty = new BountyData();
		bounty.setItemId(995);
		bounty.setGoalId("g1");
		bounty.setLocked(Collections.singletonList("bob"));

		assertTrue(bounties.isLocked(bounty, "bob"));
		assertFalse(bounties.isLocked(bounty, "alice"));
		member("bob", "Bob", LEVEL_50_XP);
		assertFalse(bounties.isLocked(bounty, "bob"));

		// a bounty whose goal was deleted no longer holds
		bounty.setGoalId("gone");
		member("bob", "Bob", 0);
		assertFalse(bounties.isLocked(bounty, "bob"));
	}

	private void member(String id, String name, int firemakingXp)
	{
		engine.publish(Section.PROFILE.keyFor(id), id, gson.toJsonTree(new ProfileData(name, 301, null)));
		SkillsData skills = new SkillsData();
		skills.getXp().put("FIREMAKING", firemakingXp);
		skills.getXp().put("ATTACK", 0);
		engine.publish(Section.SKILLS.keyFor(id), id, gson.toJsonTree(skills));
	}

	private GoalData goal(String id, GoalData.Type type, String target, int amount)
	{
		GoalData data = new GoalData();
		data.setType(type.name());
		data.setTarget(target);
		data.setAmount(amount);
		data.setCreatedAt(System.currentTimeMillis());
		engine.publish(Keys.shared(GoalService.TYPE, id), "alice", gson.toJsonTree(data));
		return data;
	}
}
