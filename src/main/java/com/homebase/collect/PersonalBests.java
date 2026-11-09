package com.homebase.collect;

import com.homebase.data.Clean;
import com.homebase.data.KillCountsData;
import com.homebase.data.Section;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

/**
 * Shares the player's best boss kill times along with their kill counts. RuneLite's Chat Commands
 * plugin, which is on by default, already keeps a personal best for each boss from the game's messages,
 * so they're read from where it keeps them, and never changed. Runs on the client thread.
 */
@Singleton
public class PersonalBests
{
	/**
	 * Where Chat Commands keeps them, for each account: the boss's name in lower case, to seconds.
	 */
	static final String GROUP = "personalbest";

	/**
	 * Raids have a best for each team size, such as "chambers of xeric 3 players". The fastest is the one shared.
	 */
	private static final Pattern TEAM_SIZE = Pattern.compile(" (?:solo|\\d+(?:\\+|-\\d+)? players?)$");

	private static final int MAX_BOSS_NAME = 40;

	private final ConfigManager configManager;
	private final LocalCollector collector;

	/**
	 * The member whose times were last read, so they're read once for each account that logs in.
	 */
	private String readFor;

	/**
	 * Set when a time changes, for the next game tick, so a burst of changes is read once.
	 */
	private boolean changed;

	@Inject
	private PersonalBests(ConfigManager configManager, LocalCollector collector)
	{
		this.configManager = configManager;
		this.collector = collector;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		String memberId = collector.getMemberId();
		if (memberId != null && (changed || !memberId.equals(readFor)))
		{
			readFor = memberId;
			changed = false;
			share(read());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GROUP.equals(event.getGroup()) && event.getProfile() != null)
		{
			changed = true;
		}
	}

	private Map<String, Double> read()
	{
		Map<String, Double> best = new HashMap<>();
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return best;
		}
		for (String key : configManager.getRSProfileConfigurationKeys(GROUP, profile, ""))
		{
			Double seconds = configManager.getRSProfileConfiguration(GROUP, key, double.class);
			String boss = bossName(key);
			if (seconds != null && seconds > 0 && boss != null)
			{
				best.merge(boss, seconds, Math::min);
			}
		}
		return best;
	}

	/**
	 * @return the boss's name from where its time is kept, without a raid's team size, such as
	 * "Chambers of Xeric" for "chambers of xeric 3 players"
	 */
	static String bossName(String key)
	{
		String name = Clean.text(TEAM_SIZE.matcher(key.trim().toLowerCase(Locale.ROOT)).replaceFirst(""), MAX_BOSS_NAME);
		if (name == null)
		{
			return null;
		}
		StringBuilder out = new StringBuilder();
		for (String word : name.split(" "))
		{
			boolean small = out.length() > 0 && (word.equals("of") || word.equals("the") || word.equals("and"));
			out.append(out.length() > 0 ? " " : "")
				.append(small || word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}
		return out.toString();
	}

	private void share(Map<String, Double> best)
	{
		if (best.isEmpty())
		{
			return;
		}
		KillCountsData kills = collector.shared(Section.KILL_COUNTS, KillCountsData.class);
		if (kills == null)
		{
			kills = new KillCountsData();
		}
		if (merge(kills, best))
		{
			collector.share(Section.KILL_COUNTS, kills);
		}
	}

	/**
	 * Put the times in, under the name the boss's kill count has if it has one.
	 *
	 * @return true if anything changed
	 */
	static boolean merge(KillCountsData kills, Map<String, Double> best)
	{
		boolean changed = false;
		for (Map.Entry<String, Double> entry : best.entrySet())
		{
			String counted = kills.existingName(entry.getKey());
			String kept = kills.existingTimeName(entry.getKey());
			String name = counted != null ? counted : kept != null ? kept : entry.getKey();
			if (kept != null && !kept.equals(name))
			{
				// its kill count has turned up since, under a slightly different name
				kills.getBestTimes().remove(kept);
			}
			boolean full = !kills.getBestTimes().containsKey(name) && kills.getBestTimes().size() >= KillCountsData.MAX_BOSSES;
			if (!full && !Objects.equals(kills.getBestTimes().get(name), entry.getValue()))
			{
				kills.getBestTimes().put(name, entry.getValue());
				changed = true;
			}
		}
		return changed;
	}
}
