# The Group Ironman Homebase

A RuneLite plugin that gives a group one home for everything: synced inventories, banks and stats, quest and activity tracking, item requests, group goals and bank bounties.

It works for Group Ironman groups and for normal groups of friends. Nobody hosts a server.

## How syncing works

- Everyone in the group enters the same **group passphrase**. The plugin uses it to join a RuneLite party automatically every time you launch, so there's no joining by hand.
- Every member's client keeps its own copy of the group's data on disk.
- Data is stored per member, per section (bank, skills, inventory and so on), and each section is stamped with the time it was captured. When two copies meet, the newer one wins.
- When anyone joins, everyone online compares what they hold and fills in the gaps. Whoever receives data passes it on later, so members who are never online at the same time still catch up through the others.
- Live stats (HP, prayer, run energy) come from RuneLite's own party data while members are online.

Example: I have bonscott1's data from 5pm and mundell's from 1pm. Fred has mundell's from 4pm and bonscott1's from 2pm. When Fred and I are online together, I update Fred's copy of bonscott1 and Fred updates my copy of mundell.

### Limits

- Data only moves while at least two members are online at once.
- RuneLite allows one party at a time. If you join someone else's party, syncing pauses, and the plugin rejoins your group when you leave.
- Bank bounties are a soft lock. They only stop withdrawals for members running RuneLite with this plugin on.

## Testing it in RuneLite

You need a JDK (Java 11 or 17, for example [Eclipse Temurin](https://adoptium.net/)).

1. Clone this repository and open a terminal in it.
2. Run `./gradlew run` (on Windows, `gradlew.bat run`). This starts a development RuneLite with the plugin loaded. Alternatively, open the folder in IntelliJ IDEA and run `HomebasePluginTest`.
3. To log in with a Jagex account, follow RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) guide once.
4. Open the house icon in the sidebar and click **Create group**, then **Copy passphrase** and send it to your group.
5. Everyone else pastes the passphrase into the plugin's settings (**Group passphrase**).

### What to check

With one account:

- [ ] The house icon appears in the sidebar, and **Create group** changes the status to "Connected".
- [ ] After logging in, the header shows your Group Ironman group's name (for example HungryHorse) under "Group Homebase".
- [ ] Your card appears on the **Members** tab with a green world number. Clicking your name shows your skills, inventory and equipment.
- [ ] Opening your bank adds a **Bank** line to your card. Clicking it shows the items. The seed vault and looting bag work the same way.
- [ ] Opening the group storage adds a **Group storage** section at the top, saying who saw it last.
- [ ] On the **Requests** tab, requesting an item adds it to the list. **Cancel** removes it.
- [ ] After closing and reopening RuneLite, everything is still there before you log in.

With a second group member running the plugin:

- [ ] Each of you sees the other's card, with live HP, prayer and run energy while they're online.
- [ ] A request one of you makes appears for the other, with a chat message starting "[Homebase]".
- [ ] **I'll get it**, **Done** and **Clear** update for both of you.
- [ ] When one of you logs out, the other's panel shows "Seen … ago" for them.

## To-do

Items are ticked when they're built. Each stage's last box is ticked once it has been tested in game.

### Stage 1: Foundation (sync and member panel)

- [x] Plugin project set up from RuneLite's template (Java 11, BSD-2 licence)
- [x] Group passphrase setting, **Create group** and **Copy passphrase** buttons
- [x] Join the group party automatically on launch
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
- [x] Tested in a simulated RuneLite: three players, the real plugin code, 42 checks
- [ ] Tested in game with at least two group members

### Stage 2: On-screen features

- [ ] Group status HUD: each member's HP, prayer, run energy, level and position
- [ ] HUD settings: choose what's shown, compact mode, size
- [ ] Area names for members' positions (for example "Varrock")
- [ ] Group members on the world map, updated live
- [ ] Group tile markers: mark a tile and the whole group sees it
- [ ] Per-member tile colours, set in the plugin settings
- [ ] Tile markers work inside instances (raids and similar)
- [ ] Tested in game

### Stage 3: Quests, activities and goals

- [ ] Quests
- [ ] Achievement diaries
- [ ] Combat achievements
- [ ] Collection log (captured as members browse it, plus new-slot chat messages)
- [ ] Boss kill counts
- [ ] Player-owned house contents (rooms and furniture built, costume room storage)
- [ ] Panel views to compare who has done what
- [ ] Goals: set a target for every member (for example 50 Firemaking for Wintertodt)
- [ ] Goals tick off automatically as each member reaches them
- [ ] Tested in game

### Stage 4: Request panel

- [x] Request an item from the group (for example a rune scimitar), with a quantity and a note
- [x] Members can assign themselves to a request, or give it back
- [x] Mark requests done, cancel them, or clear finished ones
- [x] In-game chat message when someone makes, takes on or finishes a request (can be turned off in settings)
- [x] Text from other players is cleaned before it's shown, so a request can't inject formatting or load images
- [x] Tested in the simulated RuneLite
- [ ] Tested in game

### Stage 5: Bank bounties

- [ ] Put a bounty on an item in group storage, tied to a goal
- [ ] Choose which members are locked out until the goal is met
- [ ] Hide the Withdraw options for locked members (soft lock, RuneLite only)
- [ ] Lock icon and goal shown on the item in group storage
- [ ] Bounty lifts automatically when the goal is reached
- [ ] Tested in game

### Release

- [ ] Screenshots and a final README for the Plugin Hub
- [ ] Submit to the RuneLite Plugin Hub
