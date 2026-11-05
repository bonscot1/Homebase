# Developing The Group Ironman Homebase

Notes for working on the plugin: how the sync works, how to run it from source, what to check in game, and what's left to do. The player guide is in [README.md](README.md).

## How syncing works

- **Group Ironman players don't set anything up.** When you log in, the plugin reads your group's name (for example HungryHorse), and **Connect** joins a RuneLite party for that group. Everyone in the group ends up in the same party. You stay in it through logouts and leave after 30 minutes at the login screen, so RuneLite's party servers only carry people who are playing. The group is remembered, so its saved data shows before you log in.
- Other groups share a **group passphrase** instead, set once in the plugin's settings.
- Every member's client keeps its own copy of the group's data on disk.
- Data is stored per member, per section (bank, skills, inventory and so on), and each section is stamped with the time it was captured. When two copies meet, the newer one wins.
- When anyone joins, everyone online compares what they hold and fills in the gaps. Whoever receives data passes it on later, so members who are never online at the same time still catch up through the others.
- Live stats (HP, prayer, run energy, special attack) come from RuneLite's own party data while members are online.

Example: I have bonscott1's data from 5pm and mundell's from 1pm. Fred has mundell's from 4pm and bonscott1's from 2pm. When Fred and I are online together, I update Fred's copy of bonscott1 and Fred updates my copy of mundell.

### Limits

- Data only moves while at least two members are online at once.
- RuneLite allows one party at a time. If you join someone else's party, syncing pauses until you switch back with the panel's button.
- The party is only joined when the player clicks **Connect**. RuneLite has turned down plugins that join parties by themselves.
- Each member's data is one record and the newest copy wins, so saving a group storage change on a fresh install or a second computer, before connecting and catching up, replaces that member's storage log for everyone.
- Turning off **Your position** hides it from members on this version or later. Members on versions before it was added fall back to RuneLite's party position, which stops updating unless the player has RuneLite's own Party plugin on, since that keeps sending it.
- Bank bounties are a soft lock. They only stop withdrawals for members running RuneLite with this plugin on.
- Group Ironman group names are public on the hiscores, so anyone could join a group's party. Everyone in the party is checked against the group's member list (`ClanSettings` for `ClanID.GROUP_IRONMAN`). While anyone isn't on it, nothing is sent (sync, presence, pings, and the party service's position and stats), and nothing from them is used. Party display names are set by each client, so a modified client could pose as a member; the **Group Ironman secret** setting closes that gap. RuneLite's party server keeps listing a connection for a few minutes after it closes, with no name for anyone who joins later, so a nameless member who has sent nothing for 10 seconds is taken to be closed and ignored; anyone nameless who sends something pauses sharing until they've been quiet that long.
- On an account that isn't in your Group Ironman group, such as an alt, nothing is sent, not even its name, even while it stays in the party. To the others it looks like a connection that has gone quiet, so an alt can't pause sharing for them. RuneLite's own Party plugin can still send its name, if it's on. A member's name and live details only start after the game has shown their account is in the group, and starting asks everyone in the party to send theirs (`UserSync`), because RuneLite only sends a name again when asked or when it changes.
- A member at the login screen has no name for anyone who joins after them, and a nameless member who says anything pauses sharing. So a logged-out member of a Group Ironman group only finishes sending what's waiting for 10 seconds, then stays quiet until they log in again, and whoever joins takes them for a closed connection. Members on a version before this go on answering, so they can still pause a newcomer for 10 seconds after each thing they send.
- Two copies of a record with the same version and different authors don't settle until its next change, because summaries only compare versions. It takes two changes in the same millisecond, or a clock running fast, and there's an ignored test for it.
- Within the group, members trust each other. A member with a modified client could write records that claim to be another member's, or change shared requests and bounties however they like. Signing records with a key per member would close this, if it's ever needed.
- Withdraw-X is hidden while any of an item is held back, because the amount typed can't be checked before the game takes it.
- The group storage history is one log per member (`m:<id>:storagelog`), not one for the group, so two members saving while they can't reach each other can't overwrite one another, and who did something comes from whose record it is. Members on older versions pass the logs on without showing them, and don't add to them. A log keeps a member's latest 100 entries, which also limits how far back **Owed back** can see.
- While group storage is open, `INV_GROUP_TEMP` is a temporary copy, and changes can still be lost until the game saves them, so entries are only added when the game's "Saving..." message (`LOADING_ICON_MODAL`) appears. Each save is compared with what storage held when it was opened or last saved. An empty container is only believed when the interface was just drawn from it (`GROUP_IRONMAN_STORAGE_BUILD`), because other plugins have found it can report empty at other times. A save that seems to move over 40 kinds of item in one direction is dropped, since nobody can carry that many.
- Received messages are limited: no nesting past 32 levels, at most 2,000 records a message, and every record from another client is checked (missing lists become empty, numbers kept in range) before use. Request chat lines are limited to 3 every 10 seconds, and ping sounds to one a second.

## How things behave

The finer points for players, which the README leaves out.

- **What you share:** your position, inventory, equipment, bank, seed vault, looting bag, drops and pets, and deaths. These all start on. Turning one off stops sharing it. Your position and containers are also removed for your group, but drops and deaths they've already seen stay, and your card still shows your world. Members on an older version of the plugin keep the last position they got.
- **Map pins:** right-click the world map and choose **Pin for group** to show everyone a spot. You have one pin at a time, and pins last 24 hours unless you change **Pins last (hours)**.
- **Group highlights:** outline a monster for everyone on your world until it dies, or, with **Keep highlights after death** on, through every respawn until you change world. You can have 20 at once, and a new one replaces your oldest.
- Data only moves while at least two members are online at once.
- RuneLite allows one party at a time. If you join someone else's party, syncing pauses until you click **Switch to group**.
- Bank bounties and items kept for requests only stop withdrawals for members using RuneLite with this plugin turned on, and only count when someone in your group set them up. To see the padlocks without the plugin hiding any Withdraw options, turn off **Hold items in group storage**.
- Only the amount asked for is held back. If 5 are kept for someone and there are 8 in group storage, anyone can take the other 3, with Withdraw-1, -5 or -10 when the amount fits. Withdraw-X is hidden while any of an item is held back, because the plugin can't see the number you type.
- On the world map, someone in a dungeon is shown above it, and someone somewhere the map doesn't cover, such as a player-owned house, stays where they were last on it. If you have a dungeon's own map open, members on the surface don't appear until you switch back to the surface map.
- Drops are valued at Grand Exchange prices, so untradeable drops don't count as valuable; new collection log slots are reported separately. Loot from chests and clue caskets relies on RuneLite's Loot Tracker plugin being on, which it is by default.
- Item search and the collection log dots only know what members have shared, so they can be out of date.
- Clicking a member in the **Group HUD** doesn't walk you to the ground behind it. Monsters, objects and interfaces still get the click. Turn off **Click to find on map** if you'd rather it walked.
- Banks, the seed vault, the looting bag and group storage update when someone opens them. To share your whole collection log, open it and click **Search** once; browsing a page also updates that page.
- The group storage history only shows what was saved, so putting something in and taking it back before saving leaves no line. It only knows about changes made by members using RuneLite with this version of the plugin or later turned on, and each member keeps their latest 100 lines. The tab shows the newest 30. Saving storage on a fresh install or a second computer before connecting starts that member's log again, for everyone.
- **Owed back** only counts items marked as needing returning, and only what's been taken out since they were marked. It's what a member has taken out, less what they've put in themselves, so taking back your own deposit doesn't count. Someone else putting the same item in doesn't settle it. A withdrawal that has dropped off those 100 lines no longer counts. To clear a line, right-click it and choose **Doesn't need returning**. That clears the item for everyone who owes it, and anyone in the group can do it.

## Testing it in RuneLite

You need a JDK (Java 11 or 17, for example [Eclipse Temurin](https://adoptium.net/)).

1. Clone this repository and open a terminal in it.
2. Run `./gradlew run` (on Windows, `gradlew.bat run`). This starts a development RuneLite with the plugin loaded. Alternatively, open the folder in IntelliJ IDEA and run `HomebasePluginTest`.
3. To log in with a Jagex account, follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide once.
4. Log in on your Group Ironman account and click **Connect**. The plugin finds your group by itself.
5. For a group that isn't a Group Ironman group, open the house icon in the sidebar, click **Create group**, then **Copy passphrase** and send it to the others. They paste it into the plugin's settings (**Group passphrase**).

### What to check

With one account:

- [ ] The house icon appears in the sidebar. Before logging in, the status says to log in on your Group Ironman account.
- [ ] After logging in on your Group Ironman account, the header shows your group's name (for example HungryHorse) and a **Connect** button. Clicking it changes the status to "Connected".
- [ ] Logging out keeps you connected (your card shows you offline). Logging back in within 30 minutes needs no Connect; after 30 minutes at the login screen you're disconnected.
- [ ] After restarting RuneLite, before you log in, the panel shows your group's saved data and says to log in on an account in your group to connect.
- [ ] Logged in on an account that isn't in the group, the panel says it can't connect.
- [ ] Logging in on an account that isn't in the group adds no card for that account.
- [ ] Connect, log out, then log in on an account that isn't in the group. The panel says it can't connect. Logging back in on your group account says "Connected" again without pressing anything, and your name and bars are back in the **Group HUD**.
- [ ] Connecting again soon after closing RuneLite can say "Connecting..." for up to 10 seconds while the party still lists the old connection. It doesn't warn about someone outside the group, and it then changes to "Connected".
- [ ] Your card appears on the **Members** tab with a green world number. Clicking your name shows your skills, inventory and equipment.
- [ ] Opening your bank adds a **Bank** line to your card. Clicking it shows the items. The seed vault and looting bag work the same way.
- [ ] Opening the group storage fills the **Storage** tab, saying who saw it last. The tab sums up what's owed back, the bounties and what's held for requests, and each of those lists is folded away when there's nothing in it or you fold it.
- [ ] On the **Requests** tab, requesting an item adds it to the list. **Cancel** removes it. There's no button to mark it done.
- [ ] After closing and reopening RuneLite, everything is still there before you log in.
- [ ] The **Group HUD** box shows your hitpoints, prayer, run energy, combat level and area (for example "Lumbridge"). Its settings change what it shows, compact mode and size, and **Include yourself** hides your own line.
- [ ] Clicking a member in the **Group HUD** says in chat to open your world map, and doesn't walk you there. Opening the map within a minute goes to that member. Right-clicking the box offers **Find on map** for each member, which does the same, even with a monster behind the box. A monster or object behind the HUD, an open right-click menu, an interface over the HUD, or holding Alt to move it all get the click instead, and so does the login screen after logging out. Turning off **Click to find on map** lets every click through to the game.
- [ ] Shift + right-click a tile and choose **Group mark**. The tile is outlined in **Your colour** with your name on it. Changing the colour changes the tile. **Group unmark** removes it, and it's still there after restarting.
- [ ] A combat achievement task, a "New item added to your collection log" message and a boss kill count message each update the matching line on your card (**Combat achievements**, **Collection log**, **Kill counts**).
- [ ] Browsing a page of your collection log adds that page, with how many of its items you have, under **Collection log** on your card.
- [ ] Clicking **Search** in the collection log adds every page you have items on at once.
- [ ] Going home (house portal **Home**, Teleport to House or a house tab) lists your useful furniture under **House** on your card. Opening the costume room storage lists what's in it.
- [ ] Middle-click the ground (a quick click; dragging still turns the camera). The tile flashes in **Your colour** with your name and a dot on the minimap for a few seconds (**Ping length**). Middle-clicking a monster outlines it instead. **Ping key** starts as Not set. Setting **Ping with** to **Ping key + left-click** and choosing a **Ping key** (for example Ctrl) pings with that key + left-click instead, and that click does nothing in game.
- [ ] Shift + right-click a monster and choose **Group highlight**. It stays outlined in your colour with your name until it dies, you choose **Group unhighlight**, or you change worlds.
- [ ] With **Keep highlights after death** on, a highlighted monster that dies is highlighted again when it respawns, until you choose **Group unhighlight**. With it off, its highlight goes when it dies.
- [ ] On the **Goals** tab, set a goal you've already reached (for example 1 Attack) and one you haven't. The first shows a tick by your name, the second your progress such as "45/50".
- [ ] Deleting a goal with a bounty on it removes the bounty, and the padlock on the item goes too.
- [ ] Add a goal and close RuneLite straight away. It's still there when you open it again.
- [ ] **Add bounty** on a goal you haven't reached, pick an item from group storage and tick yourself. In group storage that item gets a red padlock with the goal in its tooltip, and its Withdraw options are gone. Deleting the bounty brings them back.
- [ ] With **Hold items in group storage** turned off, the padlock stays but no Withdraw options are hidden.
- [ ] A few seconds after logging in, **Kill counts** on your card fills in bosses from the hiscores, with none at 0. A boss already listed from chat (for example "Gauntlet") isn't listed twice.
- [ ] Open group storage and close it without changing anything. No new line appears under **History** on the **Storage** tab, and the opening doesn't list everything as put in or taken out.
- [ ] Move items between slots, or put something in and take it back out, then save. No new line appears.
- [ ] Take out 5 of something and save, using each of the **Save** button, the cross and Esc in turn. A line such as "bonscott1 took out Shark ×5" appears under **History** after each one (one line each), and nothing is added under **Owed back**, because the item isn't marked.
- [ ] Right-click an item on the **Storage** tab and choose **Needs returning**. It gets an orange dot, and its tooltip says so. Take out 5 and save: you're listed under **Owed back**. Taking it out before it was marked doesn't count.
- [ ] Put 3 of it back and save. **History** gains a line, and **Owed back** drops to 2. Putting the rest back removes it from the list.
- [ ] Take out all of a marked item, right-click your line under **Owed back** and choose **Doesn't need returning**. The line goes, and the item is no longer marked.
- [ ] With an item marked, put more of it in and save, then take that back out and save. **History** shows both, and **Owed back** doesn't list it.
- [ ] Take the only item out of storage that holds just one kind of item, and save. It's listed as taken out, with storage now empty.
- [ ] Change something and leave without saving, if the game lets you (log out or switch worlds with storage open). No line appears, and reopening shows the game put things back.
- [ ] Open storage with a change another member just saved. That change isn't listed as yours.

With a second group member running the plugin:

- [ ] One of you connects and logs out. The other logs in and clicks **Connect**: "Connecting..." for about 10 seconds, then "Connected", and they share with anyone else online. When the first logs back in, neither has to press anything.
- [ ] One of you logs out and logs in on an account that isn't in the group, while staying connected. The other doesn't get "Nothing is shared while …", and still shows "Connected".
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
- [ ] In the collection log, on a page and in the log's search results, an item the other has shows a dot in their colour, and hovering over any item says who else has it. Scrolling the list doesn't draw dots outside it.
- [ ] Each option under **What you share**: turning off your bank, inventory or the like empties it on the other's card, and turning it back on brings it back (your bank, once you next open it). Turning off your position takes your arrow off the other's map and the area off their HUD within a few seconds.
- [ ] The other options do what they say: **Only your world**, **Order**, **Names on map markers**, **Map marker size**, **Keep off-screen members at the edge**, **Pins last**, **Pings on the minimap**, **Show offline members**, **Online members first**, **Live bars on cards**, and each kind of update under **Updates and chat**.
- [ ] Opening a member's big bank doesn't make the game stutter, and it still doesn't when the panel refreshes with the bank open. Scrolling shows the items as they come into view, and hovering shows each one's name and amount.
- [ ] A ping from one of you shows up for the other with the pinger's name and colour, and a ping sound (**Ping sound**). A monster pinged or highlighted by one of you is outlined for the other when you're on the same world.
- [ ] When one of you reaches a goal, the other gets a chat message such as "[Homebase] bonscott1 reached the goal: 50 Firemaking." and the goal ticks off for them.
- [ ] A bounty that locks only one of you leaves the other's Withdraw options alone and shows them a green padlock.
- [ ] One of you takes something out of group storage and saves. The other sees the line under **History** and, if the item is marked as needing returning, under **Owed back** with the first one's name, within a few seconds and without opening storage. Marking an item shows its dot for both of you. If they then put it back, it leaves the list for both of you.
- [ ] Each of you saves a change while the other is offline. Once you're both online, both changes are listed for both of you, in time order.
- [ ] Someone on an older version of the plugin in the group still gets the logs and passes them on, and nothing breaks for them.
- [ ] Expanding a member shows **Quests** and **Diaries** lines with their counts. Clicking them shows the quest list and the diary grid. Karamja's Easy, Medium and Hard tiers use older game flags than the other diaries, so check those against the in-game journal.

## To do

- Test with a second group member: everything under "With a second group member" above
- Rooms built in the house (the game doesn't name rooms, so this needs more work)
- Screenshots in the README
- Ask in RuneLite's Discord `#development` about bounties hiding Withdraw options

## Releasing

The Plugin Hub builds from a public GitHub repository at a fixed commit. To release, push to `main`, then open a pull request to [runelite/plugin-hub](https://github.com/runelite/plugin-hub) that changes `commit=` in `plugins/group-ironman-homebase` to the new hash. Say in the pull request what changed, and point reviewers at anything new that hides options or sends more party traffic.
