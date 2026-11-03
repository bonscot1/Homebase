# The Group Ironman Homebase

One home for your group in RuneLite. See what everyone has and where they're up to, ask each other for items, set goals together, and point things out to each other in game. There's no website to sign up to and no server to run.

It's made for Group Ironman groups, and works for any group of friends too.

## What it does

**See your whole group**
- A card for each member, with a green dot and their world while they're online, and live hitpoints, prayer and run energy bars.
- Their skills, laid out like the in-game skills tab, plus quests, achievement diaries, combat achievements, collection log pages and boss kill counts.
- Their inventory, equipment, bank, seed vault and looting bag, as they were last time they opened them, and your group storage, with who saw it last.
- Useful furniture built in their house, and what's in their costume room.
- An **Updates** tab, plus a chat line when you log in, saying what everyone did while you were away: "Magic 15 → 32, completed The Restless Ghost".

**Play together**
- A **Group HUD** in the corner of the screen with each member's hitpoints, prayer, run energy, combat level and where they are ("Varrock", "near Catherby", "Wilderness (level 11)").
- Group members shown on the world map.
- **Pings:** middle-click the ground or a monster, and everyone sees it flash in your colour with your name for a few seconds.
- **Group highlights:** outline a monster for the whole group until it dies.
- **Group tiles:** mark tiles that the whole group sees, including inside raids.

**Organise the group**
- **Requests:** ask for an item ("Rune scimitar ×1, for Barrows"). Someone takes it on and gets it to you, either by putting it in group storage, where it's kept for you until you take it out, or by trading it to you. Nobody can just mark it done, and everyone gets a chat message along the way.
- **Goals:** set targets for everyone or one member, such as 50 Firemaking before Wintertodt, 100 Vorkath kills or the Varrock Hard diary. Each member's progress shows as a bar, and the group gets a message when someone gets there.
- **Bank bounties:** put a bounty on some of an item in group storage, tied to a goal. Members it's locked for can't withdraw that many until they reach the goal.

## Getting started

**Group Ironman:** turn the plugin on, log in, open the house icon in the sidebar and click **Connect**. The plugin reads your group's name from the game, so there's nothing to set up. Click **Connect** once each time you start RuneLite.

**Any other group:** one of you opens the house icon in the sidebar, clicks **Create group** and then **Copy passphrase**, and sends the passphrase to the others. Everyone pastes it into **Group passphrase** in the plugin's settings and clicks **Connect**.

You stay connected when you log out, so you can switch worlds or characters without connecting again. After 30 minutes at the login screen you're disconnected, the same as with RuneLite's Party plugin.

The house icon in the sidebar opens the panel, with four tabs: **Members**, **Updates**, **Requests** and **Goals**.

## Controls

| To | Do this |
| --- | --- |
| Ping a spot or a monster | Middle-click it (a quick click; dragging still turns the camera) |
| Highlight a monster for the group | Shift + right-click it, **Group highlight** |
| Mark a tile for the group | Shift + right-click the tile, **Group mark** |
| Put a bounty on a group storage item | **Goals** tab, **Add bounty** on a goal |

Under **Pings and highlights** in the settings you can switch pings to a key + left-click, change how long they last, or turn off the sound. **Your colour** sets the colour of your pings, highlights and tiles.

## Privacy and how sharing works

- You share only your own character's details, and only with your group.
- Data goes through RuneLite's own party service, and only after you click **Connect**. Nothing is sent to any other server.
- Each member keeps a copy of the group's data on their own computer. When two members are online together, they fill in each other's gaps, so news reaches members who are never online at the same time.
- Group Ironman group names are public on the hiscores, so someone else could join your group's party. The plugin checks everyone in the party against your group's member list in the game. While anyone else is there, it shares nothing, ignores whatever they send, and the panel says who it is. A modified client could still pretend to be one of your group, so for complete peace of mind, everyone in the group can enter the same word in **Group Ironman secret**.
- If RuneLite's own Party plugin is turned on, it shares your position and stats with whatever party you're in. That's separate from this plugin.
- An account that isn't in your Group Ironman group, such as an alt, shares nothing.

## Good to know

- Data only moves while at least two members are online at once.
- RuneLite allows one party at a time. If you join someone else's party, syncing pauses until you click **Switch to group**.
- Bank bounties and items kept for requests only stop withdrawals for members using RuneLite with this plugin turned on, and only count when someone in your group set them up. To see the padlocks without the plugin hiding any Withdraw options, turn off **Hold items in group storage**.
- Only the amount asked for is held back. If 5 are kept for someone and there are 8 in group storage, anyone can take the other 3, with Withdraw-1, -5 or -10 when the amount fits. Withdraw-X is hidden while any of an item is held back, because the plugin can't see the number you type.
- Banks, the seed vault, the looting bag and group storage update when someone opens them. To share your whole collection log, open it and click **Search** once; browsing a page also updates that page.

## Feedback

Found a bug or have an idea? Open an issue at https://github.com/bonscot1/Homebase/issues.

Working on the plugin? See [DEVELOPMENT.md](DEVELOPMENT.md).

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
