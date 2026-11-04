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
- RuneLite allows one party at a time. If you join someone else's party, syncing pauses until you switch back with the panel's button.
- The party is only joined when the player clicks **Connect**. RuneLite has turned down plugins that join parties by themselves.
- Bank bounties are a soft lock. They only stop withdrawals for members running RuneLite with this plugin on.
- Group Ironman group names are public on the hiscores, so anyone could join a group's party. Everyone in the party is checked against the group's member list (`ClanSettings` for `ClanID.GROUP_IRONMAN`). While anyone isn't on it, nothing is sent (sync, presence, pings, and the party service's position and stats), and nothing from them is used. Party display names are set by each client, so a modified client could pose as a member; the **Group Ironman secret** setting closes that gap. RuneLite's party server keeps listing a connection for a few minutes after it closes, with no name for anyone who joins later, so a nameless member who sends nothing for 10 seconds is taken to be closed and ignored; anyone nameless who sends anything at all still pauses sharing.
- On an account that isn't in your Group Ironman group, such as an alt, nothing is shared.
- Within the group, members trust each other. A member with a modified client could write records that claim to be another member's, or change shared requests and bounties however they like. Signing records with a key per member would close this, if it's ever needed.
- Withdraw-X is hidden while any of an item is held back, because the amount typed can't be checked before the game takes it.
- The group storage history is one log per member (`m:<id>:storagelog`), not one for the group, so two members saving while they can't reach each other can't overwrite one another, and who did something comes from whose record it is. Members on older versions pass the logs on without showing them, and don't add to them. A log keeps a member's latest 100 entries, which also limits how far back **Still borrowed** can see.
- While group storage is open, `INV_GROUP_TEMP` is a temporary copy, and changes can still be lost until the game saves them, so entries are only added when the game's "Saving..." message (`LOADING_ICON_MODAL`) appears. Each save is compared with what storage held when it was opened or last saved. An empty container is only believed when the interface was just drawn from it (`GROUP_IRONMAN_STORAGE_BUILD`), because other plugins have found it can report empty at other times. A save that seems to move over 40 kinds of item in one direction is dropped, since nobody can carry that many.
- Received messages are limited: no nesting past 32 levels, at most 2,000 records a message, and every record from another client is checked (missing lists become empty, numbers kept in range) before use. Request chat lines are limited to 3 every 10 seconds, and ping sounds to one a second.

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
- [ ] After logging in on your Group Ironman account, the header shows your group's name (for example HungryHorse) and a **Connect** button. Clicking it changes the status to "Connected".
- [ ] Logging out keeps you connected (your card shows you offline). Logging back in within 30 minutes needs no Connect; after 30 minutes at the login screen you're disconnected.
- [ ] After restarting RuneLite, before you log in, the panel shows your group's saved data and says to log in on an account in your group to connect.
- [ ] Logged in on an account that isn't in the group, the panel says it can't connect.
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
- [ ] Clicking **Search** in the collection log adds every page you have items on at once.
- [ ] Going home (house portal **Home**, Teleport to House or a house tab) lists your useful furniture under **House** on your card. Opening the costume room storage lists what's in it.
- [ ] Middle-click the ground (a quick click; dragging still turns the camera). The tile flashes in **Your colour** with your name and a dot on the minimap for a few seconds (**Ping length**). Middle-clicking a monster outlines it instead. Setting **Ping with** to **Ping key + left-click** pings with Ctrl + left-click instead, and that click does nothing in game.
- [ ] Shift + right-click a monster and choose **Group highlight**. It stays outlined in your colour with your name until it dies, you choose **Group unhighlight**, or you change worlds.
- [ ] On the **Goals** tab, set a goal you've already reached (for example 1 Attack) and one you haven't. The first shows a tick by your name, the second your progress such as "45/50".
- [ ] **Add bounty** on a goal you haven't reached, pick an item from group storage and tick yourself. In group storage that item gets a red padlock with the goal in its tooltip, and its Withdraw options are gone. Deleting the bounty brings them back.
- [ ] With **Hold items in group storage** turned off, the padlock stays but no Withdraw options are hidden.
- [ ] A few seconds after logging in, **Kill counts** on your card fills in bosses from the hiscores, with none at 0. A boss already listed from chat (for example "Gauntlet") isn't listed twice.
- [ ] Open group storage and close it without changing anything. No new line appears under **History** in the **Group storage** section, and the opening doesn't list everything as put in or taken out.
- [ ] Move items between slots, or put something in and take it back out, then save. No new line appears.
- [ ] Take out 5 of something and save, using each of the **Save** button, the cross and Esc in turn. A line such as "bonscott1 took out Shark ×5" appears under **History** after each one (one line each), and the item appears under **Still borrowed**.
- [ ] Put 3 of it back and save. **History** gains a line, and **Still borrowed** drops to 2. Putting the rest back removes it from the list.
- [ ] Put in something new and save, then take it back out and save. **History** shows both, and **Still borrowed** doesn't list it.
- [ ] Take the only item out of storage that holds just one kind of item, and save. It's listed as taken out, with storage now empty.
- [ ] Change something and leave without saving, if the game lets you (log out or switch worlds with storage open). No line appears, and reopening shows the game put things back.
- [ ] Open storage with a change another member just saved. That change isn't listed as yours.

With a second group member running the plugin:

- [ ] Both of you click **Connect**. If someone outside the group joins the party, both panels say "Nothing is shared while … is in the party", and sharing resumes when they leave.

- [ ] Each of you sees the other's card, with live HP, prayer and run energy while they're online.
- [ ] A request one of you makes appears for the other, with a chat message starting "[Homebase]".
- [ ] **I'll get it** and **Give back** update for both of you, and whoever took it on is told to put it in group storage or trade it.
- [ ] Whoever took a request on puts the item in group storage. Both of you see "In group storage for …", and the requester gets "Waiting for you in group storage". For the other member the item has a red padlock ("kept for …'s request"), and the Withdraw options that would take any of the requested amount are gone. If there's more than was asked for, Withdraw-1 still takes the spare.
- [ ] The requester takes the item out of group storage, and the request shows as done for both of you. **Clear** removes it. Taking out only part of it shows "x of y received" and leaves the request open.
- [ ] Closing group storage without taking anything leaves the request waiting (it isn't counted as collected).
- [ ] If whoever put it in takes it back out, it stops being held for the requester.
- [ ] Instead, whoever took a request on trades the item to the requester. When the trade is accepted, the amount counts towards the request, and it's done once everything asked for has arrived. Typing "Accepted trade." in chat does nothing.
- [ ] When one of you logs out, the other's panel shows "Seen … ago" for them straight away, and they drop out of the other's HUD and world map. If one of you closes RuneLite without logging out, the same happens within about a minute and a quarter.
- [ ] While one of you is offline, the other gains a level, finishes a quest or completes a diary tier. On logging back in, the first gets one chat line such as "[Homebase] bonscott1: Magic 15 -> 32, completed The Restless Ghost, completed the Varrock Easy diary." The same summary appears on the **Updates** tab.
- [ ] Each of you sees the other in the **Group HUD**, on the world map (an arrow in their colour with their name above it, and their area as the tooltip), and their marked tiles in their colour, including inside a raid you both enter.
- [ ] One of you logs in and stands still. The other still gets their arrow within a few seconds of connecting, in the right place.
- [ ] Scrolling the world map so someone is off screen leaves their arrow at the edge, pointing towards them. Scrolling back points it down at them again.
- [ ] One of you goes into a dungeon. On the surface map, the other sees their arrow above the dungeon. In a player-owned house or a raid, their arrow stays where they were last on the map. On a boat, it follows the boat.
- [ ] The **Group HUD** and member cards show a special attack bar that follows the other's special attack as they use it.
- [ ] Right-click the world map and choose **Pin for group**. The other sees a pin in your colour with your name and gets a chat message; **Focus on** takes them to it. **Remove your pin** takes it away for both of you, and placing another moves it.
- [ ] One of you gets a drop worth more than **Drops worth at least**, or dies. The other gets it on the **Updates** tab and in chat, with the amount it's worth or where it happened. A chest or clue casket counts too, with the Loot Tracker plugin on.
- [ ] Typing an item in the search box above the tabs lists everyone who has it, with how many and where, including items in the group storage. Clearing it, or clicking a tab, goes back to the tabs. Keep typing while results appear: the box keeps the keyboard.
- [ ] In the collection log, an item the other has shows a dot in their colour, and hovering over any item says who else has it. Scrolling the page doesn't draw dots outside the list.
- [ ] Each option under **What you share**: turning off your bank, inventory or the like empties it on the other's card, and turning it back on brings it back (your bank, once you next open it). Turning off your position takes your arrow off the other's map and the area off their HUD within a few seconds.
- [ ] The other options do what they say: **Only your world**, **Order**, **Names on map markers**, **Map marker size**, **Keep off-screen members at the edge**, **Pins last**, **Pings on the minimap**, **Show offline members**, **Online members first**, **Live bars on cards**, and each kind of update under **Updates and chat**.
- [ ] Opening a member's big bank doesn't make the game stutter, and it still doesn't when the panel refreshes with the bank open. Scrolling shows the items as they come into view, and hovering shows each one's name and amount.
- [ ] A ping from one of you shows up for the other with the pinger's name and colour, and a ping sound (**Ping sound**). A monster pinged or highlighted by one of you is outlined for the other when you're on the same world.
- [ ] When one of you reaches a goal, the other gets a chat message such as "[Homebase] bonscott1 reached the goal: 50 Firemaking." and the goal ticks off for them.
- [ ] A bounty that locks only one of you leaves the other's Withdraw options alone and shows them a green padlock.
- [ ] One of you takes something out of group storage and saves. The other sees the line under **History** and the item under **Still borrowed** with the first one's name, within a few seconds and without opening storage. If they then put it back, it leaves the list for both of you.
- [ ] Each of you saves a change while the other is offline. Once you're both online, both changes are listed for both of you, in time order.
- [ ] Someone on an older version of the plugin in the group still gets the logs and passes them on, and nothing breaks for them.
- [ ] Expanding a member shows **Quests** and **Diaries** lines with their counts. Clicking them shows the quest list and the diary grid. Karamja's Easy, Medium and Hard tiers use older game flags than the other diaries, so check those against the in-game journal.

## To-do

Items are ticked when they're built. Each stage's last box is ticked once it has been tested in game.

### Stage 1: Foundation (sync and member panel)

- [x] Plugin project set up from RuneLite's template (Java 11, BSD-2 licence)
- [x] Group Ironman players connect with one click, with no passphrase, and the group is remembered for next time
- [x] Only accounts in the Group Ironman group share data, so alts stay out
- [x] Optional **Group Ironman secret** to keep out people who only know the group's name
- [x] Group passphrase setting, **Create group** and **Copy passphrase** buttons, for groups that aren't Group Ironman groups
- [x] Connect once per session with a button; stay connected through logouts and leave after 30 minutes at the login screen
- [x] Check party members against the Group Ironman member list and share nothing while anyone else is in the party
- [x] Pause syncing while in another party, with a button to switch back
- [x] Save the group's data to disk (loaded on start, saved every 30 seconds and on exit)
- [x] Sync protocol: summaries on join, newest copy wins per member per section, relaying between members, no duplicate sends
- [x] Safety checks: members can only write their own data, deleted items stay deleted, bad clocks and malformed messages are rejected
- [x] Unit tests for the sync protocol, including the bonscott1/mundell/Fred example
- [x] Share profile (name, world, last seen), skills and inventory
- [x] Share equipment
- [x] Share the bank, seed vault and looting bag whenever they're opened
- [x] Share the group storage whenever anyone opens it
- [x] Live HP, prayer and run energy for online members
- [x] Members show as offline as soon as they log out (each client says so before leaving the party), or after 75 seconds without a "still here" if RuneLite closes without logging out
- [x] Each player listed once even when the party briefly keeps their old connection after they log back in
- [x] Sidebar panel: members with online status or last seen, live stats and total level
- [x] Expand a member to see skills, inventory, equipment, bank, seed vault and looting bag
- [x] Bank, seed vault and looting bag start collapsed, with an item count, so a full bank doesn't take over the panel
- [x] Group storage section showing when it was last seen and by whom
- [x] Your Group Ironman group's name (for example HungryHorse) shown in the panel header
- [x] Tested in a simulated RuneLite: three players, the real plugin code, 56 checks
- [ ] Tested in game with at least two group members

### Stage 2: On-screen features

- [x] Group status HUD: each member's HP, prayer and run energy as bars, combat level and position
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
- [x] Collection log: every page at once from the log's Search (script 4100 runs once per obtained item; pages and their items come from the cache, enum 2102 and params 683, 689 and 690, as WikiSync does), pages captured as members browse them, new slots from the "New item added to your collection log" message, and the total when the game provides it
- [x] Boss kill counts, from kill count messages, the collection log's page headers, and the hiscores at login (bosses never killed aren't ranked, so they stay hidden; names are matched loosely so "The Gauntlet" and "Gauntlet" are one boss)
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
- [x] Quick repeated withdrawals can't dip into what's held: withdrawals let through are counted until the game reports them
- [ ] Tested in game

### Stage 6: Group storage history

- [x] A log per member of what they put in and took out of group storage, how many and when, added each time the game saves storage and shared through the normal sync
- [x] **History** and **Still borrowed** lists under **Group storage** on the **Members** tab
- [x] Opening and closing storage, moving items around, and changes that cancel out or are never saved add nothing
- [ ] Tested in game

### Release

- [x] Panel styled after RuneLite's own panels: one row of tabs, cards with live HP, prayer and run bars, skills laid out like the in-game tab, rows that open each section, progress bars on goals, and item pictures on bounties
- [x] Quests and miniquests counted apart
- [x] A player README, with these developer notes moved here
- [x] Plugin Hub icon (`icon.png`, 48×72)
- [ ] Screenshots in the README
- [x] Make the repository public (the Plugin Hub requires it)
- [x] Code review: hardened message handling, checked data, request amounts, trade check, connect button
- [ ] Ask in RuneLite's Discord `#development` about bounties hiding Withdraw options
- [x] Submit to the RuneLite Plugin Hub ([runelite/plugin-hub#20070](https://github.com/runelite/plugin-hub/pull/20070), at commit 0d9b517)

### Submitting to the Plugin Hub

The Plugin Hub builds plugins from a public GitHub repository at a fixed commit. Once the repository is public and the last change is on `main`:

1. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub) on GitHub.
2. In the fork, add a file named `plugins/group-ironman-homebase` (no extension) containing:

   ```
   repository=https://github.com/bonscot1/Homebase.git
   commit=<the full hash of the commit on main to release>
   ```

3. Open a pull request to `runelite/plugin-hub` titled "Add Group Ironman Homebase". In the description, say what the plugin does and point reviewers at these:
   - It joins a party only when the player clicks **Connect**.
   - It hides Withdraw options in group storage for items held back by the group's own bounties and requests (can be turned off with **Hold items in group storage**).
   - Party traffic: summaries every few seconds while something changed, records in batches of up to about 48 KB before compression, and a presence message every 30 seconds.
4. The Plugin Hub's checks run on the pull request. Fix anything they flag and update `commit=` to the new hash.

Updates later work the same way: push to `main`, then open a pull request that changes `commit=`.
