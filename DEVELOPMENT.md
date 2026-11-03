# Developing The Group Ironman Homebase

Notes for working on the plugin: how the sync works, how to run it from source, what to check in game, and what's left to do. The player guide is in [README.md](README.md).

## How syncing works

- **Group Ironman players don't set anything up.** When you log in, the plugin reads your group's name (for example HungryHorse) and joins a RuneLite party for that group. Everyone in the group ends up in the same party. You're in the party only while you're logged in: the plugin joins when you load into the game and leaves when you log out, so RuneLite's party servers only carry people who are playing. The group is remembered, so its saved data shows before you log in.
- Other groups share a **group passphrase** instead, set once in the plugin's settings.
- Every member's client keeps its own copy of the group's data on disk.
- Data is stored per member, per section (bank, skills, inventory and so on), and each section is stamped with the time it was captured. When two copies meet, the newer one wins.
- When anyone joins, everyone online compares what they hold and fills in the gaps. Whoever receives data passes it on later, so members who are never online at the same time still catch up through the others.
- Live stats (HP, prayer, run energy) come from RuneLite's own party data while members are online.

Example: I have bonscott1's data from 5pm and mundell's from 1pm. Fred has mundell's from 4pm and bonscott1's from 2pm. When Fred and I are online together, I update Fred's copy of bonscott1 and Fred updates my copy of mundell.

### Limits

- Data only moves while at least two members are online at once.
- RuneLite allows one party at a time. If you join someone else's party, syncing pauses, and the plugin rejoins your group when you leave.
- Bank bounties are a soft lock. They only stop withdrawals for members running RuneLite with this plugin on.
- Group Ironman group names are public on the hiscores, so anyone with this plugin who knows your group's name could join its party and see what's shared. To keep them out, everyone in the group enters the same word in the **Group Ironman secret** setting.
- On an account that isn't in your Group Ironman group, such as an alt, nothing is shared.

## Testing it in RuneLite

You need a JDK (Java 11 or 17, for example [Eclipse Temurin](https://adoptium.net/)).

1. Clone this repository and open a terminal in it.
2. Run `./gradlew run` (on Windows, `gradlew.bat run`). This starts a development RuneLite with the plugin loaded. Alternatively, open the folder in IntelliJ IDEA and run `HomebasePluginTest`.
3. To log in with a Jagex account, follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide once.
4. Log in on your Group Ironman account. That's all: the plugin joins your group by itself.
5. For a group that isn't a Group Ironman group, open the house icon in the sidebar, click **Create group**, then **Copy passphrase** and send it to the others. They paste it into the plugin's settings (**Group passphrase**).

### What to check

With one account:

- [ ] The house icon appears in the sidebar. Before logging in, the status says to log in on your Group Ironman account.
- [ ] After logging in on your Group Ironman account, the status changes to "Connected" by itself, and the header shows your group's name (for example HungryHorse) under "Group Homebase".
- [ ] After restarting RuneLite, before you log in, the panel shows your group's saved data and says "Joins your group when you log in". Logging in changes it to "Connected", and logging out back again.
- [ ] Logging in on an account that isn't in the group adds no card for that account.
- [ ] Your card appears on the **Members** tab with a green world number. Clicking your name shows your skills, inventory and equipment.
- [ ] Opening your bank adds a **Bank** line to your card. Clicking it shows the items. The seed vault and looting bag work the same way.
- [ ] Opening the group storage adds a **Group storage** section at the top, saying who saw it last.
- [ ] On the **Requests** tab, requesting an item adds it to the list. **Cancel** removes it. There's no button to mark it done.
- [ ] After closing and reopening RuneLite, everything is still there before you log in.
- [ ] The **Group HUD** box shows your hitpoints, prayer, run energy, combat level and area (for example "Lumbridge"). Its settings change what it shows, compact mode and size, and **Include yourself** hides your own line.
- [ ] Shift + right-click a tile and choose **Group mark**. The tile is outlined in **Your colour** with your name on it. Changing the colour changes the tile. **Group unmark** removes it, and it's still there after restarting.
- [ ] A combat achievement task, a "New item added to your collection log" message and a boss kill count message each update the matching line on your card (**Combat achievements**, **Collection log**, **Kill counts**).
- [ ] Browsing a page of your collection log adds that page, with how many of its items you have, under **Collection log** on your card.
- [ ] Going home (house portal **Home**, Teleport to House or a house tab) lists your useful furniture under **House** on your card. Opening the costume room storage lists what's in it.
- [ ] Middle-click the ground (a quick click; dragging still turns the camera). The tile flashes in **Your colour** with your name and a dot on the minimap for a few seconds (**Ping length**). Middle-clicking a monster outlines it instead. Setting **Ping with** to **Ping key + left-click** pings with Ctrl + left-click instead, and that click does nothing in game.
- [ ] Shift + right-click a monster and choose **Group highlight**. It stays outlined in your colour with your name until it dies, you choose **Group unhighlight**, or you change worlds.
- [ ] On the **Goals** tab, set a goal you've already reached (for example 1 Attack) and one you haven't. The first shows a tick by your name, the second your progress such as "45/50".
- [ ] **Add bounty** on a goal you haven't reached, pick an item from group storage and tick yourself. In group storage that item gets a red padlock with the goal in its tooltip, and its Withdraw options are gone. Deleting the bounty brings them back.

With a second group member running the plugin:

- [ ] Each of you sees the other's card, with live HP, prayer and run energy while they're online.
- [ ] A request one of you makes appears for the other, with a chat message starting "[Homebase]".
- [ ] **I'll get it** and **Give back** update for both of you, and whoever took it on is told to put it in group storage or trade it.
- [ ] Whoever took a request on puts the item in group storage. Both of you see "In group storage for …", and the requester gets "Waiting for you in group storage". For the other member the item has a red padlock ("kept for …'s request"), and the Withdraw options that would take any of the requested amount are gone. If there's more than was asked for, Withdraw-1 still takes the spare.
- [ ] The requester takes the item out of group storage, and the request shows as done for both of you. **Clear** removes it.
- [ ] Instead, whoever took a request on trades at least the requested amount to the requester. When the trade is accepted, the request shows as done.
- [ ] When one of you logs out, the other's panel shows "Seen … ago" for them.
- [ ] While one of you is offline, the other gains a level, finishes a quest or completes a diary tier. On logging back in, the first gets one chat line such as "[Homebase] bonscott1: Magic 15 -> 32, completed The Restless Ghost, completed the Varrock Easy diary." The same summary appears on the **Updates** tab.
- [ ] Each of you sees the other in the **Group HUD**, on the world map (a dot in their colour, with their name and area as the tooltip), and their marked tiles in their colour, including inside a raid you both enter.
- [ ] A ping from one of you shows up for the other with the pinger's name and colour, and a ping sound (**Ping sound**). A monster pinged or highlighted by one of you is outlined for the other when you're on the same world.
- [ ] When one of you reaches a goal, the other gets a chat message such as "[Homebase] bonscott1 reached the goal: 50 Firemaking." and the goal ticks off for them.
- [ ] A bounty that locks only one of you leaves the other's Withdraw options alone and shows them a green padlock.
- [ ] Expanding a member shows **Quests** and **Diaries** lines with their counts. Clicking them shows the quest list and the diary grid. Karamja's Easy, Medium and Hard tiers use older game flags than the other diaries, so check those against the in-game journal.

## To-do

Items are ticked when they're built. Each stage's last box is ticked once it has been tested in game.

### Stage 1: Foundation (sync and member panel)

- [x] Plugin project set up from RuneLite's template (Java 11, BSD-2 licence)
- [x] Group Ironman players join their group automatically on login, with no passphrase, and the group is remembered for next time
- [x] Only accounts in the Group Ironman group share data, so alts stay out
- [x] Optional **Group Ironman secret** to keep out people who only know the group's name
- [x] Group passphrase setting, **Create group** and **Copy passphrase** buttons, for groups that aren't Group Ironman groups
- [x] Join the group party when you log in and leave it when you log out, so idle clients don't hold a party connection
- [x] Pause syncing while in another party, and rejoin the group after leaving it
- [x] Save the group's data to disk (loaded on start, saved every 30 seconds and on exit)
- [x] Sync protocol: summaries on join, newest copy wins per member per section, relaying between members, no duplicate sends
- [x] Safety checks: members can only write their own data, deleted items stay deleted, bad clocks and malformed messages are rejected
- [x] Unit tests for the sync protocol, including the bonscott1/mundell/Fred example
- [x] Share profile (name, world, last seen), skills and inventory
- [x] Share equipment
- [x] Share the bank, seed vault and looting bag whenever they're opened
- [x] Share the group storage whenever anyone opens it
- [x] Live HP, prayer and run energy for online members
- [x] Sidebar panel: members with online status or last seen, live stats and total level
- [x] Expand a member to see skills, inventory, equipment, bank, seed vault and looting bag
- [x] Bank, seed vault and looting bag start collapsed, with an item count, so a full bank doesn't take over the panel
- [x] Group storage section showing when it was last seen and by whom
- [x] Your Group Ironman group's name (for example HungryHorse) shown in the panel header
- [x] Tested in a simulated RuneLite: three players, the real plugin code, 56 checks
- [ ] Tested in game with at least two group members

### Stage 2: On-screen features

- [x] Group status HUD: each member's HP, prayer, run energy, combat level and position
- [x] HUD settings: choose what's shown, include yourself or not, compact mode, size
- [x] Area names for members' positions (for example "Varrock", "near Catherby", "Wilderness (level 11)", "Varrock (underground)")
- [x] Group members on the world map, updated live, with their area in the tooltip
- [x] Group tile markers: Shift + right-click a tile and choose **Group mark**, and the whole group sees it
- [x] Per-member colours for tiles, pings and highlights, set in the plugin settings (**Your colour**), with optional names
- [x] Tile markers work inside instances (raids and similar): they're stored at the instance's template location
- [x] Pings: middle-click the ground or a monster (or hold a **Ping key** and left-click, if you prefer), and the group sees it flash in your colour, with your name and a minimap dot, for a few seconds
- [x] Group highlights: Shift + right-click a monster and choose **Group highlight** to outline it for the whole group until it dies
- [x] Area names for player-owned houses
- [ ] Tested in game

### Stage 3: Quests, activities and goals

- [x] Quests (finished and started), read after login, after quest messages and about once a minute
- [x] Achievement diaries (every region and tier)
- [x] Summaries of what each member did since your last update: levels gained, quests finished and diary tiers completed. Shown in chat (can be turned off) and on the **Updates** tab, and kept after restarting
- [x] Updates gathered for a few seconds, so catching up on login gives one line per member
- [x] Quests and Diaries sections on each member's card
- [x] Tested in the simulated RuneLite, including your example summary
- [x] Combat achievements: points, tasks done and highest tier reached, read from the game
- [x] Collection log: pages captured as members browse them (how many of each page's items they have), new slots from the "New item added to your collection log" message, and the total when the game provides it
- [x] Boss kill counts, from kill count messages and the collection log's page headers
- [x] Player-owned house: useful furniture built (pools, jewellery box, portal nexus, altars, mounted items and more), captured in your own house, and the costume room storage when it's opened
- [ ] Rooms built in the house (the game doesn't name rooms, so this needs more work)
- [x] Combat achievements, collection log and kill counts in the summaries too
- [x] Goals on a new **Goals** tab: skill levels, total level, quests, diaries, kill counts, combat achievement points and collection log slots, for everyone or one member (for example 50 Firemaking for Wintertodt)
- [x] Goals tick off automatically as each member reaches them, with a chat message when someone does (can be turned off)
- [ ] Tested in game

### Stage 4: Request panel

- [x] Request an item from the group (for example a rune scimitar), with a quantity and a note
- [x] Members can assign themselves to a request, or give it back
- [x] Requests finish only from what happens in game: whoever took one on puts the item in group storage, where it's kept for the requester until they take it out, or trades it to them. Requests can be cancelled, and finished ones cleared
- [x] In-game chat message when someone makes, takes on or finishes a request (can be turned off in settings)
- [x] Text from other players is cleaned before it's shown, so a request can't inject formatting or load images
- [x] Tested in the simulated RuneLite
- [ ] Tested in game

### Stage 5: Bank bounties

- [x] Put a bounty on an item in group storage, tied to a goal (**Add bounty** on a goal)
- [x] Choose which members are locked out until the goal is met
- [x] Hide the Withdraw options for locked members (soft lock, RuneLite only)
- [x] Padlock on the item in group storage, red while it's locked for you and green once it isn't, with the goal in its tooltip
- [x] Bounty lifts automatically for each locked member as they reach the goal
- [x] Bounties and requests hold back only the amount asked for; anything more in group storage is free for anyone
- [ ] Tested in game

### Release

- [x] Panel styled after RuneLite's own panels: one row of tabs, cards with live HP, prayer and run bars, skills laid out like the in-game tab, rows that open each section, progress bars on goals, and item pictures on bounties
- [x] Quests and miniquests counted apart
- [x] A player README, with these developer notes moved here
- [x] Plugin Hub icon (`icon.png`, 48×72)
- [ ] Screenshots in the README
- [ ] Make the repository public (the Plugin Hub requires it)
- [ ] Ask in RuneLite's Discord `#development` about joining the group party at login and bounties hiding Withdraw options
- [ ] Submit to the RuneLite Plugin Hub
