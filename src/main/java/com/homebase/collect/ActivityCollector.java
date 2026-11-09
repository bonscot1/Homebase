package com.homebase.collect;

import com.homebase.data.Clean;
import com.homebase.data.CollectionLogData;
import com.homebase.data.CombatAchievementsData;
import com.homebase.data.HouseData;
import com.homebase.data.KillCountsData;
import com.homebase.data.Section;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreManager;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.util.Text;

/**
 * Captures combat achievements, the collection log, boss kill counts and the player-owned house, and
 * shares them through the {@link LocalCollector}.
 */
@Slf4j
@Singleton
public class ActivityCollector
{
	/**
	 * One bit per combat achievement task, set once the task is done.
	 */
	private static final int[] CA_TASK_VARPS = {
		VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1, VarPlayerID.CA_TASK_COMPLETED_2,
		VarPlayerID.CA_TASK_COMPLETED_3, VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7, VarPlayerID.CA_TASK_COMPLETED_8,
		VarPlayerID.CA_TASK_COMPLETED_9, VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13, VarPlayerID.CA_TASK_COMPLETED_14,
		VarPlayerID.CA_TASK_COMPLETED_15, VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19, VarPlayerID.CA_TASK_COMPLETED_20,
	};

	/**
	 * Points needed for each tier, in the order of {@link CombatAchievementsData#TIERS}.
	 */
	private static final int[] CA_THRESHOLDS = {
		VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER,
	};

	/**
	 * Whether each tier is complete, in the same order, for when the thresholds aren't known.
	 */
	private static final int[] CA_TIER_STATUS = {
		VarbitID.CA_TIER_STATUS_EASY, VarbitID.CA_TIER_STATUS_MEDIUM, VarbitID.CA_TIER_STATUS_HARD,
		VarbitID.CA_TIER_STATUS_ELITE, VarbitID.CA_TIER_STATUS_MASTER, VarbitID.CA_TIER_STATUS_GRANDMASTER,
	};

	private static final Set<Integer> CA_VARPS = new HashSet<>();
	private static final Set<Integer> CA_VARBITS = new HashSet<>();

	static
	{
		for (int varp : CA_TASK_VARPS)
		{
			CA_VARPS.add(varp);
		}
		CA_VARBITS.add(VarbitID.CA_POINTS);
		for (int varbit : CA_THRESHOLDS)
		{
			CA_VARBITS.add(varbit);
		}
		for (int varbit : CA_TIER_STATUS)
		{
			CA_VARBITS.add(varbit);
		}
	}

	/**
	 * "Your Vorkath kill count is: 12.", "Your completed Chambers of Xeric count is: 5.",
	 * "Your Barrows chest count is: 50.", "Your subdued Wintertodt count is: 100."
	 */
	private static final Pattern KILL_COUNT = Pattern.compile(
		"^Your (?:completed |subdued )?(.+?) (?:kill |success |completion |harvest |chest |rift search )?count is: ([\\d,]+)\\.?$",
		Pattern.CASE_INSENSITIVE);

	/**
	 * Kill counts in the collection log's page header, such as "Vorkath kills: 50".
	 */
	private static final Pattern HEADER_KILLS = Pattern.compile("^(.+?) kills: ([\\d,]+)$", Pattern.CASE_INSENSITIVE);

	private static final String NEW_LOG_SLOT = "New item added to your collection log: ";

	/**
	 * Furniture worth showing the group, matched against object names. Decorations like chairs are
	 * left out.
	 */
	private static final List<String> FURNITURE = Arrays.asList(
		"rejuvenation pool", "revitalisation pool", "restoration pool", "jewellery box", "portal nexus",
		"fairy ring", "spirit tree", "altar", "mounted glory", "xeric's talisman", "digsite pendant",
		"mythical cape", "lectern", "larder", "armour case", "magic wardrobe", "treasure chest", "cape rack",
		"toy box", "fancy dress box", "telescope", "teleport focus", "scrying pool", "obelisk",
		"tip jar", "bank chest", "achievement gallery", "quest list", "combat dummy", "elemental balance");

	/**
	 * Menu options and targets that take you into your own house, rather than a friend's.
	 */
	private static final Set<String> OWN_HOUSE_OPTIONS = new HashSet<>(Arrays.asList("home", "build mode", "inside"));
	private static final List<String> OWN_HOUSE_TARGETS = Arrays.asList("teleport to house", "tele to poh", "construct. cape");

	/**
	 * Ticks after login before the first read of combat achievements and the hiscores, once the game
	 * has sent the player's varps.
	 */
	private static final int LOGIN_DELAY_TICKS = 10;

	/**
	 * How often to look around the house for furniture while in it.
	 */
	private static final int HOUSE_SCAN_TICKS = 10;

	/**
	 * Ticks after arriving in the house before the first look around, giving its furniture time to load.
	 */
	private static final int FIRST_SCAN_TICKS = 3;

	/**
	 * How long to wait for the house to load after choosing to go home.
	 */
	private static final int HOUSE_ARRIVAL_TICKS = 30;

	private static final int MAX_ITEM_NAME = 60;
	private static final int MAX_BOSS_NAME = 40;
	private static final int MAX_FURNITURE_NAME = 40;

	private final Client client;
	private final ClientThread clientThread;
	private final LocalCollector collector;
	private final CollectionLogSearch logSearch;
	private final HiscoreManager hiscoreManager;
	private final ScheduledExecutorService executor;

	/**
	 * Ticks since login while waiting to read combat achievements, or -1 when not waiting.
	 */
	private int loginTicks = -1;

	/**
	 * Set to read combat achievements on the next tick, so a burst of changes is read once.
	 */
	private boolean combatAchievementsDue;

	private String lastPageSignature;
	private String lastCostumesSignature;

	/**
	 * Whether the house we're in, or are about to enter, is the player's own.
	 */
	private boolean ownHouse;
	private boolean inHouse;
	private int ticksSinceScan;
	private int arrivalTicksLeft;

	@Inject
	private ActivityCollector(Client client, ClientThread clientThread, LocalCollector collector, CollectionLogSearch logSearch,
		HiscoreManager hiscoreManager, ScheduledExecutorService executor)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.collector = collector;
		this.logSearch = logSearch;
		this.hiscoreManager = hiscoreManager;
		this.executor = executor;
	}

	public void start()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loginTicks = 0;
		}
	}

	public void stop()
	{
		loginTicks = -1;
		combatAchievementsDue = false;
		lastPageSignature = null;
		lastCostumesSignature = null;
		ownHouse = false;
		inHouse = false;
		logSearch.reset();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			stop();
		}
		else if (event.getGameState() == GameState.LOGGED_IN && loginTicks < 0 && collector.getMemberId() == null)
		{
			loginTicks = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (loginTicks >= 0 && ++loginTicks >= LOGIN_DELAY_TICKS && collector.getMemberId() != null)
		{
			loginTicks = -1;
			combatAchievementsDue = true;
			String name = collector.getLocalName();
			if (name != null)
			{
				fetchHiscoreKills(name);
			}
		}
		if (collector.getMemberId() == null)
		{
			return;
		}
		if (combatAchievementsDue)
		{
			combatAchievementsDue = false;
			shareCombatAchievements();
		}
		capturePage();
		Map<String, CollectionLogSearch.PageResult> searched = logSearch.takeFinished();
		if (searched != null)
		{
			saveSearchedPages(searched);
		}
		captureCostumes();
		trackHouse();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (CA_VARPS.contains(event.getVarpId()) || CA_VARBITS.contains(event.getVarbitId()))
		{
			combatAchievementsDue = true;
		}
		else if (event.getVarpId() == VarPlayerID.COLLECTION_COUNT || event.getVarpId() == VarPlayerID.COLLECTION_COUNT_MAX)
		{
			updateLogCounts();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (collector.getMemberId() == null
			|| (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM))
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		if (message.startsWith(NEW_LOG_SLOT))
		{
			recordNewSlot(message.substring(NEW_LOG_SLOT.length()));
			return;
		}
		String[] kill = parseKillCount(message);
		if (kill != null)
		{
			recordKills(kill[0], Integer.parseInt(kill[1]));
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption()).toLowerCase(Locale.ROOT);
		String target = Text.removeTags(event.getMenuTarget()).toLowerCase(Locale.ROOT);
		if (option.equals("friend's house"))
		{
			ownHouse = false;
			return;
		}
		boolean houseTarget = target.contains("portal") || target.contains("house");
		if ((OWN_HOUSE_OPTIONS.contains(option) && houseTarget) || OWN_HOUSE_TARGETS.stream().anyMatch(target::contains))
		{
			ownHouse = true;
			inHouse = false;
			arrivalTicksLeft = HOUSE_ARRIVAL_TICKS;
			ticksSinceScan = HOUSE_SCAN_TICKS - FIRST_SCAN_TICKS;
		}
	}

	private void shareCombatAchievements()
	{
		CombatAchievementsData ca = readCombatAchievements();
		if (ca.getPoints() > 0 || ca.getTasks() > 0)
		{
			collector.share(Section.COMBAT_ACHIEVEMENTS, ca);
		}
	}

	private CombatAchievementsData readCombatAchievements()
	{
		CombatAchievementsData data = new CombatAchievementsData();
		data.setPoints(client.getVarbitValue(VarbitID.CA_POINTS));
		int tasks = 0;
		for (int varp : CA_TASK_VARPS)
		{
			tasks += Integer.bitCount(client.getVarpValue(varp));
		}
		data.setTasks(tasks);

		int tier = -1;
		boolean thresholdsKnown = false;
		for (int i = 0; i < CA_THRESHOLDS.length; i++)
		{
			int threshold = client.getVarbitValue(CA_THRESHOLDS[i]);
			if (threshold > 0)
			{
				thresholdsKnown = true;
				if (data.getPoints() >= threshold)
				{
					tier = i;
				}
			}
		}
		if (!thresholdsKnown)
		{
			for (int i = 0; i < CA_TIER_STATUS.length; i++)
			{
				if (client.getVarbitValue(CA_TIER_STATUS[i]) > 0)
				{
					tier = i;
				}
			}
		}
		data.setTier(tier);
		return data;
	}

	/**
	 * @return the boss name and count from a kill count message, or null if it isn't one
	 */
	static String[] parseKillCount(String message)
	{
		Matcher m = KILL_COUNT.matcher(message.trim());
		if (!m.matches())
		{
			return null;
		}
		String boss = Clean.text(m.group(1), MAX_BOSS_NAME);
		// agility courses count laps in the same words
		if (boss == null || boss.toLowerCase(Locale.ROOT).endsWith(" lap"))
		{
			return null;
		}
		return new String[]{boss, m.group(2).replace(",", "")};
	}

	private void recordKills(String boss, int count)
	{
		recordKills(Collections.singletonMap(boss, count), false);
	}

	/**
	 * Keep kill counts, under whatever name each boss already has.
	 *
	 * @param onlyRaise true when the counts may be behind ours, as the hiscores can be
	 */
	private void recordKills(Map<String, Integer> counts, boolean onlyRaise)
	{
		KillCountsData kills = collector.shared(Section.KILL_COUNTS, KillCountsData.class);
		if (kills == null)
		{
			kills = new KillCountsData();
		}
		boolean changed = false;
		for (Map.Entry<String, Integer> entry : counts.entrySet())
		{
			int count = entry.getValue();
			if (count <= 0)
			{
				// collection log pages show "kills: 0" for bosses never fought, which says nothing
				continue;
			}
			String existing = kills.existingName(entry.getKey());
			String name = existing == null ? entry.getKey() : existing;
			Integer old = kills.getCounts().get(name);
			boolean full = old == null && kills.getCounts().size() >= KillCountsData.MAX_BOSSES;
			boolean keepOld = old != null && (old == count || (onlyRaise && old > count));
			if (full || keepOld)
			{
				continue;
			}
			kills.getCounts().put(name, count);
			changed = true;
		}
		if (changed)
		{
			collector.share(Section.KILL_COUNTS, kills);
		}
	}

	/**
	 * Fill in kill counts from the hiscores, which covers bosses killed before the plugin was installed.
	 */
	private void fetchHiscoreKills(String player)
	{
		executor.execute(() ->
		{
			HiscoreResult result;
			try
			{
				// the game can spell the spaces in a name as non-breaking spaces, which the hiscores don't match
				result = hiscoreManager.lookup(Text.toJagexName(player), HiscoreEndpoint.NORMAL);
			}
			catch (IOException e)
			{
				log.debug("Couldn't read the hiscores", e);
				return;
			}
			if (result == null || result.getSkills() == null)
			{
				return;
			}
			Map<String, Integer> counts = hiscoreKills(result.getSkills());
			clientThread.invokeLater(() ->
			{
				// they may have logged out, or into another account, while the hiscores answered
				if (player.equals(collector.getLocalName()))
				{
					recordKills(counts, true);
				}
			});
		});
	}

	/**
	 * @return the kill count of every boss on the hiscores. A boss only shows up there once the player
	 * is ranked on it, so ones never killed stay hidden.
	 */
	static Map<String, Integer> hiscoreKills(Map<HiscoreSkill, Skill> hiscores)
	{
		Map<String, Integer> counts = new HashMap<>();
		hiscores.forEach((boss, entry) ->
		{
			// unranked bosses come back as -1
			if (boss.getType() == HiscoreSkillType.BOSS && entry != null && entry.getLevel() > 0)
			{
				counts.put(boss.getName(), entry.getLevel());
			}
		});
		return counts;
	}

	/**
	 * Our collection log as last shared, or an empty one, with its page map and recent list never null.
	 */
	private CollectionLogData collectionLog()
	{
		CollectionLogData clog = collector.shared(Section.COLLECTION_LOG, CollectionLogData.class);
		if (clog == null)
		{
			clog = new CollectionLogData();
		}
		if (clog.getPages() == null)
		{
			clog.setPages(new HashMap<>());
		}
		if (clog.getRecent() == null)
		{
			clog.setRecent(new ArrayList<>());
		}
		return clog;
	}

	private void recordNewSlot(String itemName)
	{
		String item = Clean.text(itemName, MAX_ITEM_NAME);
		if (item == null)
		{
			return;
		}
		CollectionLogData clog = collectionLog();
		clog.addRecent(item);
		// 0 means the game hasn't told us the count yet, so there's nothing to add to
		if (clog.getObtained() > 0)
		{
			clog.setObtained(clog.getObtained() + 1);
		}
		collector.share(Section.COLLECTION_LOG, clog);
	}

	/**
	 * Share how many slots are filled, out of how many, as the game counts them.
	 */
	private void updateLogCounts()
	{
		if (collector.getMemberId() == null)
		{
			return;
		}
		int obtained = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		int total = client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX);
		if (obtained <= 0 || total <= 0 || obtained > total)
		{
			return;
		}
		CollectionLogData clog = collectionLog();
		if (clog.getObtained() != obtained || clog.getTotal() != total)
		{
			clog.setObtained(obtained);
			clog.setTotal(total);
			collector.share(Section.COLLECTION_LOG, clog);
		}
	}

	/**
	 * Read the collection log page that's open, if any. Obtained items are drawn solid and the rest
	 * faded, which is how we tell them apart.
	 */
	private void capturePage()
	{
		Widget items = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (items == null || items.isHidden())
		{
			lastPageSignature = null;
			return;
		}
		Widget[] children = items.getDynamicChildren();
		List<String> header = headerLines();
		if (children == null || children.length == 0 || header.isEmpty())
		{
			return;
		}
		String title = Clean.text(header.get(0), CollectionLogSearch.MAX_PAGE_NAME);
		if (title == null)
		{
			return;
		}
		int size = 0;
		List<Integer> obtained = new ArrayList<>();
		for (Widget child : children)
		{
			if (child.getItemId() <= 0)
			{
				continue;
			}
			size++;
			if (child.getOpacity() == 0)
			{
				obtained.add(child.getItemId());
			}
		}
		if (size == 0)
		{
			return;
		}
		String signature = title + obtained + size;
		if (signature.equals(lastPageSignature))
		{
			return;
		}
		lastPageSignature = signature;

		CollectionLogData clog = collectionLog();
		if (clog.getPages().size() >= CollectionLogData.MAX_PAGES && !clog.getPages().containsKey(title))
		{
			return;
		}
		clog.getPages().put(title, new CollectionLogData.Page(size, obtained, System.currentTimeMillis()));
		collector.share(Section.COLLECTION_LOG, clog);
		updateLogCounts();
		recordHeaderKills(header);
	}

	/**
	 * @return the open page's header: its title, then lines such as "Vorkath kills: 50"
	 */
	private List<String> headerLines()
	{
		List<String> out = new ArrayList<>();
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		if (header == null)
		{
			return out;
		}
		addText(out, header.getText());
		Widget[] children = header.getDynamicChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				addText(out, child.getText());
			}
		}
		return out;
	}

	private static void addText(List<String> out, String text)
	{
		if (text != null)
		{
			String clean = Text.removeTags(text).trim();
			if (!clean.isEmpty())
			{
				out.add(clean);
			}
		}
	}

	private void recordHeaderKills(List<String> header)
	{
		for (String line : header)
		{
			Matcher m = HEADER_KILLS.matcher(line);
			if (m.matches())
			{
				String boss = Clean.text(m.group(1), MAX_BOSS_NAME);
				if (boss != null)
				{
					recordKills(boss, Integer.parseInt(m.group(2).replace(",", "")));
				}
			}
		}
	}

	/**
	 * The collection log's search listed everything obtained, so share every page at once.
	 */
	private void saveSearchedPages(Map<String, CollectionLogSearch.PageResult> searched)
	{
		CollectionLogData clog = collectionLog();
		long now = System.currentTimeMillis();
		searched.forEach((name, page) ->
		{
			if (clog.getPages().size() < CollectionLogData.MAX_PAGES || clog.getPages().containsKey(name))
			{
				clog.getPages().put(name, new CollectionLogData.Page(page.getSize(), page.getObtained(), now));
			}
		});
		collector.share(Section.COLLECTION_LOG, clog);
		updateLogCounts();
	}

	/**
	 * Read the costume room storage while it's open.
	 */
	private void captureCostumes()
	{
		Widget items = client.getWidget(InterfaceID.PohCostumes.ITEMS);
		if (items == null || items.isHidden())
		{
			lastCostumesSignature = null;
			return;
		}
		Widget[] children = items.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return;
		}
		Set<Integer> seen = new LinkedHashSet<>();
		for (Widget child : children)
		{
			if (child.getItemId() > 0 && child.getOpacity() == 0)
			{
				seen.add(child.getItemId());
			}
		}
		String signature = seen.toString();
		if (seen.isEmpty() || signature.equals(lastCostumesSignature))
		{
			return;
		}
		lastCostumesSignature = signature;

		// each box only shows its own kind of costume, so add to what the other boxes showed
		HouseData house = house();
		Set<Integer> all = new LinkedHashSet<>(house.getCostumes());
		all.addAll(seen);
		List<Integer> list = new ArrayList<>(all);
		house.setCostumes(list.size() > HouseData.MAX_COSTUMES ? list.subList(0, HouseData.MAX_COSTUMES) : list);
		collector.share(Section.HOUSE, house);
	}

	private void trackHouse()
	{
		if (!ownHouse)
		{
			return;
		}
		if (client.getTopLevelWorldView().isInstance())
		{
			inHouse = true;
			if (++ticksSinceScan >= HOUSE_SCAN_TICKS)
			{
				ticksSinceScan = 0;
				captureFurniture();
			}
		}
		else if (inHouse || --arrivalTicksLeft <= 0)
		{
			// left the house, or never got there
			ownHouse = false;
			inHouse = false;
		}
	}

	/**
	 * Look around the house for useful furniture.
	 */
	private void captureFurniture()
	{
		Set<String> found = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Tile[][] plane : client.getTopLevelWorldView().getScene().getTiles())
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile != null)
					{
						addFurnitureOn(found, tile);
					}
				}
			}
		}
		if (found.isEmpty())
		{
			// not a house after all, or nothing worth listing
			return;
		}
		List<String> furniture = new ArrayList<>(found);
		if (furniture.size() > HouseData.MAX_FURNITURE)
		{
			furniture = furniture.subList(0, HouseData.MAX_FURNITURE);
		}
		HouseData house = house();
		if (!furniture.equals(house.getFurniture()))
		{
			house.setFurniture(furniture);
			collector.share(Section.HOUSE, house);
		}
	}

	private void addFurnitureOn(Set<String> found, Tile tile)
	{
		GameObject[] objects = tile.getGameObjects();
		if (objects != null)
		{
			for (GameObject object : objects)
			{
				if (object != null)
				{
					addIfFurniture(found, object.getId());
				}
			}
		}
		WallObject wall = tile.getWallObject();
		if (wall != null)
		{
			addIfFurniture(found, wall.getId());
		}
		DecorativeObject decoration = tile.getDecorativeObject();
		if (decoration != null)
		{
			addIfFurniture(found, decoration.getId());
		}
	}

	private void addIfFurniture(Set<String> found, int objectId)
	{
		ObjectComposition definition = client.getObjectDefinition(objectId);
		if (definition == null)
		{
			return;
		}
		if (definition.getImpostorIds() != null)
		{
			// the object changes with the player's varbits, so name the version they see
			ObjectComposition impostor = definition.getImpostor();
			if (impostor != null)
			{
				definition = impostor;
			}
		}
		String name = Clean.text(Text.removeTags(definition.getName()), MAX_FURNITURE_NAME);
		if (name != null && isFurniture(name))
		{
			found.add(name);
		}
	}

	static boolean isFurniture(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		for (String keyword : FURNITURE)
		{
			if (lower.contains(keyword))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Our house as last shared, or an empty one, with its lists never null.
	 */
	private HouseData house()
	{
		HouseData house = collector.shared(Section.HOUSE, HouseData.class);
		if (house == null)
		{
			house = new HouseData();
		}
		if (house.getFurniture() == null)
		{
			house.setFurniture(new ArrayList<>());
		}
		if (house.getCostumes() == null)
		{
			house.setCostumes(new ArrayList<>());
		}
		return house;
	}
}
