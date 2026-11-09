# Developing The Group Ironman Homebase

Notes for working on the plugin. For how to use it, see [README.md](README.md).

## How syncing works

Group Ironman players don't set anything up. At login the plugin reads the group's name (HungryHorse, say), and **Connect** joins a RuneLite party for that group, so the whole group ends up in the same party. You stay in it through logouts and leave after 30 minutes at the login screen, which keeps RuneLite's party servers to people who are actually playing. The group is remembered, so its saved data shows before you log in.

Other groups share a **Group passphrase** instead, set once in the plugin's settings.

Every member's client keeps its own copy of the group's data on disk, split by member and by section (bank, skills, inventory and so on). Each section is stamped with the time it was captured, and when two copies meet, the newer one wins. When anyone joins, everyone online compares what they hold and fills in the gaps. Whoever receives data passes it on later, so members who are never online at the same time still catch up through the others.

Live stats (HP, prayer, run energy and special attack) come from RuneLite's own party data while members are online.

Example: I have bonscott1's data from 5pm and mundell's from 1pm. Fred has mundell's from 4pm and bonscott1's from 2pm. When Fred and I are online together, I update Fred's copy of bonscott1 and Fred updates my copy of mundell.

### Limits

- Data only moves while at least two members are online at once.
- RuneLite allows one party at a time. If you join someone else's party, syncing pauses until you click **Switch to group** in the panel.
- The party is only joined when the player clicks **Connect**. RuneLite has turned down plugins that join parties by themselves.
- Group Ironman group names are public on the hiscores, so anyone could join a group's party. Everyone in the party is checked against the group's member list (`ClanSettings` for `ClanID.GROUP_IRONMAN`). While anyone in it isn't on the list, nothing is sent (sync, presence, pings, and the party service's position and stats), and nothing from them is used.
- Party display names are set by each client, so a modified client could pose as a member. The **Group Ironman secret** setting closes that gap.
- Within the group, members trust each other. A modified client could write records that claim to be another member's, or change shared requests and bounties however it likes. Signing records with a key per member would fix this if it's ever needed.
- RuneLite's party server keeps listing a connection for a few minutes after it closes, with no name for anyone who joins later. The plugin treats a nameless member who has sent nothing for 10 seconds as closed and ignores them. One who does send something pauses sharing until they've been quiet for 10 seconds.
- A member at the login screen also has no name for anyone who joins after them. So a logged-out member of a Group Ironman group only spends 10 seconds finishing what's waiting to be sent, then stays quiet until they log in again, and whoever joins takes them for a closed connection. Members still on the first Plugin Hub release (3 November 2026) keep answering, so they can pause a newcomer for 10 seconds after each thing they send.
- An account that isn't in your Group Ironman group, such as an alt, sends nothing, not even its name, even while it stays in the party. To the others it looks like a connection that has gone quiet, so an alt can't pause sharing for them. RuneLite's own Party plugin can still send its name if it's on.
- A member's name and live details only go out once the game has shown their account is in the group. When they start, everyone in the party is asked to send theirs too (`UserSync`), because RuneLite only sends a name again when asked or when it changes.
- Two copies of a record with the same version (the time it was captured) and different authors don't settle until the record next changes, because summaries only compare versions. It takes two changes in the same millisecond, or a clock running fast. There's an ignored test for it, `GroupSyncSimulationTest.twoChangesWithTheSameVersionEndUpTheSameEverywhere`.
- Best kill times are read from where RuneLite's Chat Commands plugin keeps each account's personal bests (the `personalbest` group in the RuneScape profile config). The plugin never writes there. With Chat Commands off, no new times are recorded. Raids keep a best for each team size, and the fastest is shared.
- The group storage history is one log per member (`m:<id>:storagelog`) rather than one for the group. That way two members saving while they can't reach each other can't overwrite one another, and who did something comes from whose record it is. A log keeps the member's latest 100 entries, which also limits how far back **Owed back** can see: a withdrawal that has dropped off no longer counts. Members on the first release pass the logs on without showing them, and don't add to them.
- Each member's storage log is a single record and the newest copy wins. Saving a group storage change on a fresh install or a second computer, before connecting and catching up, starts that member's log again for everyone.
- While group storage is open, `INV_GROUP_TEMP` is a temporary copy, and changes can still be lost until the game saves them. So entries are only added when the game's "Saving..." message (`LOADING_ICON_MODAL`) appears, and each save is compared with what storage held when it was opened or last saved. An empty container is only believed when the interface was just drawn from it (`GROUP_IRONMAN_STORAGE_BUILD`), because other plugin developers have found it can report empty at other times. A save that seems to move more than 40 kinds of item in one direction is dropped, since nobody can carry that many.
- Received messages are limited to 32 levels of nesting and 2,000 records each. Every record from another client is checked before it's used: missing lists become empty and numbers are kept in range. Request chat lines are limited to 3 every 10 seconds, and ping sounds to one a second.

## How things behave

Finer points for players that the README leaves out.

- You share your position, inventory, equipment, bank, seed vault, looting bag, drops and pets, and deaths, and all of them start on. Turning one off stops sharing it. Your position and containers are also removed for the rest of the group, but drops and deaths they've already seen stay, and your card still shows your world.
- Turning off **Your position** hides it from everyone except members on the first release. Their plugin falls back to RuneLite's party position, so they keep the last one they got. If you have RuneLite's own Party plugin on, though, it keeps sending your position and they keep seeing it move.
- Right-click the world map and choose **Pin for group** to show everyone a spot. You get one pin at a time, and pins last 24 hours unless you change **Pins last (hours)**.
- A **Group highlight** outlines a monster for everyone on your world until it dies. With **Keep highlights after death** on, it stays through every respawn until you change world. You can have 20 at once, and a new one replaces your oldest.
- Bounties and items kept for requests are a soft lock. They only stop withdrawals for members running RuneLite with this plugin turned on, and only count when someone in your group set them up. To see the padlocks without any Withdraw options being hidden, turn off **Hold items in group storage**.
- Only the amount asked for is held back. If 5 are kept for someone and there are 8 in group storage, anyone can take the other 3, with Withdraw-1, -5 or -10 when the amount fits. Withdraw-X is hidden while any of an item is held back, because the plugin can't check the number you type before the game takes it.
- On the world map, someone in a dungeon is shown above it. Someone the map doesn't cover, such as in a player-owned house, stays where they were last on it. If you have a dungeon's own map open, members on the surface don't appear until you switch back to the surface map.
- Drops are valued at Grand Exchange prices, so untradeable drops never count as valuable. New collection log slots are reported separately. Loot from chests and clue caskets needs RuneLite's Loot Tracker plugin, which is on by default.
- Item search and the collection log dots only know what members have shared, so they can be out of date.
- Clicking a member in the **Group HUD** doesn't walk you to the ground behind it, though monsters, objects and interfaces still get the click. Turn off **Click to find on map** if you'd rather it walked.
- Banks, the seed vault, the looting bag and group storage update when someone opens them. To share your whole collection log, open it and click **Search** once. Browsing a page also updates that page.
- The group storage history only shows what was saved, so putting something in and taking it back out before saving leaves no trace. It only has changes saved by members running RuneLite with the plugin turned on, and none from members on the first release. The tab shows the 10 latest visits and counts the older changes underneath. A visit is one member putting things in, or taking things out, in saves no more than 10 minutes apart.
- **Owed back** only counts items marked as needing returning, and only what's been taken out since they were marked. It's what a member has taken out less what they've put in themselves, so taking back your own deposit doesn't count, and someone else putting the same item in doesn't settle it. To clear an item, right-click it under **Owed back** and choose **Doesn't need returning**. That clears it for everyone who owes it, and anyone in the group can do it.

## Testing it in RuneLite

You need a JDK (Java 11 or 17, for example [Eclipse Temurin](https://adoptium.net/)).

1. Clone this repository and open a terminal in it.
2. Run `./gradlew run` (`gradlew.bat run` on Windows) to start a development RuneLite with the plugin loaded. Or open the folder in IntelliJ IDEA and run `HomebasePluginTest`.
3. To log in with a Jagex account, follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide once.
4. Log in on your Group Ironman account and click **Connect**. The plugin finds your group by itself.
5. For a group that isn't a Group Ironman group, open the house icon in the sidebar, click **Create group**, then **Copy passphrase** and send it to the others. They paste it into **Group passphrase** in the plugin's settings.

### What to check

With one account:

- [ ] The house icon appears in the sidebar. Before you log in, the status says to log in on your Group Ironman account.
- [ ] Once you're logged in on your Group Ironman account, the header shows your group's name (HungryHorse, say) and a **Connect** button. Clicking it changes the status to "Connected".
- [ ] Logging out keeps you connected, and your card shows you offline. Log back in within 30 minutes and you don't need to press Connect. After 30 minutes at the login screen you're disconnected.
- [ ] After restarting RuneLite, before you log in, the panel shows your group's saved data and says to log in on an account in your group to connect.
- [ ] Logged in on an account that isn't in the group, the panel says it can't connect.
- [ ] Logging in on an account that isn't in the group adds no card for that account.
- [ ] Connect, log out, then log in on an account that isn't in the group. The panel says it can't connect. Log back in on your group account and it says "Connected" again without you pressing anything, with your name and bars back in the **Group HUD**.
- [ ] Connecting again soon after closing RuneLite can say "Connecting..." for up to 10 seconds while the party still lists the old connection. It doesn't warn about someone outside the group, and then it changes to "Connected".
- [ ] Your card appears on the **Members** tab with a green world number. Clicking your name shows your skills, inventory and equipment.
- [ ] Opening your bank adds a **Bank** line to your card, and clicking it shows the items. The seed vault and looting bag work the same way.
- [ ] Opening group storage fills the **Storage** tab and says who updated it last. A line at the top sums up what's owed back, the bounties and what's held for requests. Each of those lists only shows when there's something in it, and folds away when you click its heading.
- [ ] On the **Requests** tab, requesting an item adds it to the list and **Cancel** removes it. There's no button to mark it done.
- [ ] After closing and reopening RuneLite, everything is still there before you log in.
- [ ] The **Group HUD** box shows your hitpoints, prayer, run energy, combat level and area ("Lumbridge", say). Its settings for what to show, **Compact mode** and **Size** all work, and turning off **Include yourself** hides your own line.
- [ ] Clicking a member in the **Group HUD** puts a line in chat telling you to open your world map, and doesn't walk you anywhere. Opening the map within a minute takes it to that member. Right-clicking the box offers **Find on map** for each member, which does the same, even with a monster behind the box. These still get the click instead: a monster or object behind the HUD, an open right-click menu, an interface over the HUD, holding Alt to move it, and the login screen after logging out. Turning off **Click to find on map** lets every click through to the game.
- [ ] Shift + right-click a tile and choose **Group mark**. The tile is outlined in **Your colour** with your name on it, and changing the colour changes the tile. It's still there after restarting, and **Group unmark** removes it.
- [ ] A combat achievement task, a "New item added to your collection log" message and a boss kill count message each update the matching line on your card (**Combat achievements**, **Collection log**, **Kill counts**).
- [ ] Browsing a page of your collection log adds that page under **Collection log** on your card, with how many of its items you have.
- [ ] Clicking **Search** in the collection log adds every page you have items on at once.
- [ ] Going home (the house portal's **Home**, Teleport to House or a house tab) lists your useful furniture under **House** on your card. Opening the costume room storage lists what's in it.
- [ ] Middle-click the ground with a quick click (dragging still turns the camera). The tile flashes in **Your colour** with your name, with a dot on the minimap, for as long as **Ping length**. Middle-clicking a monster outlines it instead. **Ping key** starts as Not set. Set **Ping with** to **Ping key + left-click** and choose a **Ping key** such as Ctrl, and that key + left-click pings instead and does nothing in game.
- [ ] Shift + right-click a monster and choose **Group highlight**. It stays outlined in your colour with your name until it dies, you choose **Group unhighlight**, or you change worlds.
- [ ] With **Keep highlights after death** on, a highlighted monster that dies is highlighted again when it respawns, until you choose **Group unhighlight**. With it off, the highlight goes when it dies.
- [ ] On the **Goals** tab, set a goal you've already reached (1 Attack, say) and one you haven't. The first shows a tick by your name, and the second your progress, such as "45/50".
- [ ] Deleting a goal with a bounty on it removes the bounty, and the padlock on the item goes too.
- [ ] Add a goal and close RuneLite straight away. It's still there when you open it again.
- [ ] **Add bounty** on a goal you haven't reached, pick an item from group storage and tick yourself. In group storage that item gets a red padlock with the goal in its tooltip, and its Withdraw options are gone. Deleting the bounty brings them back.
- [ ] With **Hold items in group storage** turned off, the padlock stays but no Withdraw options are hidden.
- [ ] The **Compare** tab puts any two members side by side, picked from its two lists. It has an overview, skills, kill counts and best times, with whoever is ahead in green, and a faster time counts as ahead. The top says who wins and how many rows each is ahead on, and each section shows its own score. A member's quest points only show once their plugin has updated and read them.
- [ ] With **Your own updates** on, your own level-ups and drops appear on the **Updates** tab, with a picture beside each line, and never in chat. Turning it off hides them again.
- [ ] Your **Kill counts** show your best time beside each boss you have one for, the same as `!pb` gives in chat. Beating it adds a line to the **Updates** tab for the other members. On a boss's collection log page, the other members' kill counts and best times follow the page's own, each in their colour, and hovering over one shows their name.
- [ ] A few seconds after logging in, **Kill counts** on your card fills in bosses from the hiscores, with none at 0. A boss already listed from chat ("Gauntlet", say) isn't listed twice.
- [ ] Open group storage and close it without changing anything. Nothing new appears under **History** on the **Storage** tab, and opening it doesn't list everything as put in or taken out.
- [ ] Move items between slots, or put something in and take it back out, then save. Nothing new appears under **History**.
- [ ] Take out 5 of something and save. Do it three times, saving with the **Save** button, the cross and Esc in turn. Each save adds exactly one to the count beside **History**, and your "Took out" block there gains 5 more (saves no more than 10 minutes apart show as one visit). Nothing is added under **Owed back**, because the item isn't marked.
- [ ] Right-click an item on the **Storage** tab and choose **Needs returning**. It gets an orange dot, and its tooltip says so. Take out 5 and save, and you're listed under **Owed back**. Anything taken out before it was marked doesn't count.
- [ ] Put 3 of it back and save. **History** shows it put in, and **Owed back** drops to 2. Putting the rest back takes it off the list.
- [ ] Take out all of a marked item, then right-click it in your block under **Owed back** and choose **Doesn't need returning**. It leaves the list, and the item is no longer marked.
- [ ] With an item marked, put more of it in and save, then take that back out and save. **History** shows both, and **Owed back** doesn't list it.
- [ ] When storage holds just one kind of item, take all of it out and save. It's listed as taken out, and storage is now empty.
- [ ] Change something and leave without saving, if the game lets you (log out or switch worlds with storage open). Nothing appears under **History**, and reopening shows the game put things back.
- [ ] Open storage after another member has just saved a change. That change isn't listed as yours.

With a second group member running the plugin:

- [ ] One of you connects and logs out. The other logs in and clicks **Connect**, sees "Connecting..." for about 10 seconds and then "Connected", and shares with anyone else online. When the first logs back in, neither of you has to press anything.
- [ ] One of you logs out and logs in on an account that isn't in the group, while staying connected. The other doesn't get "Nothing is shared while …" and still shows "Connected".
- [ ] Both of you click **Connect**. If someone outside the group joins the party, both panels say "Nothing is shared while … is in the party", and sharing resumes when they leave.
- [ ] Each of you sees the other's card, with live HP, prayer and run energy while they're online.
- [ ] A request one of you makes appears for the other, with a chat message starting "[Homebase]".
- [ ] **I'll get it** and **Give back** update for both of you, and whoever took the request on is told to put the item in group storage or trade it.
- [ ] Whoever took a request on puts the item in group storage. Both of you see "In group storage for …", and the requester gets "Waiting for you in group storage". For the other member the item has a red padlock ("kept for …'s request"), and any Withdraw option that would take some of the requested amount is gone. If there's more than was asked for, Withdraw-1 still takes the spare.
- [ ] The requester takes the item out of group storage, and the request shows as done for both of you. **Clear** removes it. Taking out only part of it shows "x of y received" and leaves the request open.
- [ ] Closing group storage without taking anything leaves the request waiting. It isn't counted as collected.
- [ ] If whoever put it in takes it back out, it stops being held for the requester.
- [ ] This time, whoever took a request on trades the item to the requester instead. When the trade is accepted, the amount counts towards the request, and it's done once everything asked for has arrived. Typing "Accepted trade." in chat does nothing.
- [ ] When one of you logs out, the other's panel shows "Updated … ago" for them straight away, and they drop out of the other's HUD and world map. If one of you closes RuneLite without logging out, the same happens within about a minute and a quarter.
- [ ] While one of you is offline, the other gains a level, finishes a quest or completes a diary tier. On logging back in, the first gets one chat line such as "[Homebase] bonscott1: Magic 15 -> 32, completed The Restless Ghost, completed the Varrock Easy diary." The same summary appears on the **Updates** tab.
- [ ] Each of you sees the other in the **Group HUD**, on the world map (an arrow in their colour with their name above it, and their area as the tooltip) and their marked tiles in their colour, including inside a raid you both enter.
- [ ] One of you logs in and stands still. The other still gets their arrow, in the right place, within a few seconds of connecting.
- [ ] Scrolling the world map so someone is off screen leaves their arrow at the edge, pointing towards them. Scrolling back points it down at them again.
- [ ] One of you goes into a dungeon, and the other sees their arrow above it on the surface map. In a player-owned house or a raid, their arrow stays where they were last on the map. On a boat, it follows the boat.
- [ ] The **Group HUD** and member cards show a special attack bar that follows the other's special attack as they use it.
- [ ] Right-click the world map and choose **Pin for group**. The other sees a pin in your colour with your name and gets a chat message, and **Focus on** takes them to it. **Remove your pin** takes it away for both of you, and placing another moves it.
- [ ] One of you dies, or gets a drop worth at least what **Drops worth at least** is set to. The other gets it on the **Updates** tab and in chat, with where it happened or what the drop is worth. A chest or clue casket counts too, with the Loot Tracker plugin on.
- [ ] Typing an item in the search box above the tabs lists everyone who has it, with how many and where, including group storage. Clearing it, or clicking a tab, goes back to the tabs. Keep typing while results appear, and the box keeps the keyboard.
- [ ] In the collection log, both on a page and in the log's search results, an item the other has shows a dot in their colour, and hovering over any item says who else has it. Scrolling the list doesn't draw dots outside it.
- [ ] Try each option under **What you share**. Turning off your bank, inventory or the like empties it on the other's card, and turning it back on brings it back (your bank once you next open it). Turning off your position takes your arrow off the other's map, and your area off their HUD, within a few seconds.
- [ ] The other options do what they say: **Only your world**, **Order**, **Names on map markers**, **Map marker size**, **Keep off-screen members at the edge**, **Pins last (hours)**, **Pings on the minimap**, **Show offline members**, **Online members first**, **Live bars on cards**, and each kind of update under **Updates and chat**.
- [ ] Opening a member's big bank doesn't make the game stutter, even when the panel refreshes with the bank open. Scrolling shows the items as they come into view, and hovering shows each one's name and amount.
- [ ] A ping from one of you shows up for the other with the pinger's name and colour, and plays a sound (**Ping sound**). A monster one of you pings or highlights is outlined for the other when you're on the same world.
- [ ] When one of you reaches a goal, the other gets a chat message such as "[Homebase] bonscott1 reached the goal: 50 Firemaking." and the goal ticks off for them.
- [ ] A bounty that locks only one of you leaves the other's Withdraw options alone and shows them a green padlock.
- [ ] One of you takes something out of group storage and saves. Within a few seconds, without opening storage, the other sees it under **History**, and under **Owed back** with the first one's name if the item is marked as needing returning. Marking an item shows its dot for both of you. Putting it back takes it off the list for both of you.
- [ ] Each of you saves a change while the other is offline. Once you're both online, both changes are listed for both of you, in time order.
- [ ] Someone in the group still on the first release gets the logs and passes them on, and nothing breaks for them.
- [ ] Expanding a member shows **Quests** and **Diaries** lines with their counts. Clicking them shows the quest list and the diary grid. Karamja's Easy, Medium and Hard tiers use older game flags than the other diaries, so check those against the in-game journal.

## To do

- Test with a second group member: everything under "With a second group member" above
- Rooms built in the house (the game doesn't name rooms, so this needs more work)
- Screenshots in the README
- Ask in RuneLite's Discord `#development` about bounties hiding Withdraw options

## Releasing

The Plugin Hub builds from a public GitHub repository at a fixed commit. To release, push to `main`, then open a pull request to [runelite/plugin-hub](https://github.com/runelite/plugin-hub) that changes `commit=` in `plugins/group-ironman-homebase` to the new hash. Say in the pull request what changed, and point reviewers at anything new that hides options or sends more party traffic.
