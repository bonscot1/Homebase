package com.homebase.overlay;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.map.AreaNames;
import com.homebase.party.GroupSync;
import com.homebase.party.PartyMembers;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * An on-screen box with each online group member's hitpoints, prayer, run energy, combat level and
 * where they are. Live numbers come from RuneLite's party; combat level comes from shared skills.
 */
@Singleton
public class GroupHudOverlay extends OverlayPanel
{
	private static final Color PRAYER = new Color(0x4FC3F7);
	private static final Color RUN = new Color(0xFFD54F);
	private static final Color AREA = new Color(0xBDBDBD);

	private final Client client;
	private final HomebaseConfig config;
	private final PartyService partyService;
	private final GroupSync groupSync;
	private final GroupIndex index;

	@Inject
	private GroupHudOverlay(Client client, HomebaseConfig config, PartyService partyService, GroupSync groupSync, GroupIndex index)
	{
		this.client = client;
		this.config = config;
		this.partyService = partyService;
		this.groupSync = groupSync;
		this.index = index;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.hudEnabled() || client.getGameState() != GameState.LOGGED_IN || !groupSync.isGroupParty())
		{
			return null;
		}
		List<PartyMember> members = visibleMembers();
		if (members.isEmpty())
		{
			return null;
		}

		HomebaseConfig.HudSize size = config.hudSize();
		Font font = fontFor(size);
		boolean compact = config.hudCompact();
		panelComponent.setPreferredSize(new Dimension(widthFor(size, compact), 0));

		for (PartyMember member : members)
		{
			if (compact)
			{
				addCompact(member, font);
			}
			else
			{
				addFull(member, font);
			}
		}
		return super.render(graphics);
	}

	private List<PartyMember> visibleMembers()
	{
		PartyMember local = partyService.getLocalMember();
		boolean includeSelf = config.hudIncludeSelf();
		List<PartyMember> out = new ArrayList<>();
		for (PartyMember member : PartyMembers.current(partyService))
		{
			if (!member.isLoggedIn() || member.getDisplayName() == null)
			{
				continue;
			}
			if (!includeSelf && local != null && local.getMemberId() == member.getMemberId())
			{
				continue;
			}
			out.add(member);
		}
		out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getDisplayName(), b.getDisplayName()));
		return out;
	}

	private void addFull(PartyMember member, Font font)
	{
		String name = Clean.text(member.getDisplayName(), 20);
		int level = index.combatLevel(name);
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(level > 0 && config.hudLevel() ? name + "  (level " + level + ")" : name)
			.color(index.color(name))
			.build());

		if (config.hudHitpoints() && member.getStatBase(Skill.HITPOINTS) > 0)
		{
			int hp = member.getStatBoost(Skill.HITPOINTS);
			int max = member.getStatBase(Skill.HITPOINTS);
			add("Hitpoints", hp + "/" + max, hitpointsColor(hp, max), font);
		}
		if (config.hudPrayer() && member.getStatBase(Skill.PRAYER) > 0)
		{
			add("Prayer", member.getStatBoost(Skill.PRAYER) + "/" + member.getStatBase(Skill.PRAYER), PRAYER, font);
		}
		if (config.hudRunEnergy())
		{
			add("Run", member.getRunEnergy() + "%", RUN, font);
		}
		WorldPoint coord = coordOf(member);
		if (config.hudPosition() && coord != null)
		{
			add("Area", AreaNames.describe(coord), AREA, font);
		}
	}

	private void addCompact(PartyMember member, Font font)
	{
		String name = Clean.text(member.getDisplayName(), 20);
		int level = index.combatLevel(name);
		StringBuilder right = new StringBuilder();
		Color color = Color.WHITE;
		if (config.hudHitpoints() && member.getStatBase(Skill.HITPOINTS) > 0)
		{
			int hp = member.getStatBoost(Skill.HITPOINTS);
			color = hitpointsColor(hp, member.getStatBase(Skill.HITPOINTS));
			right.append(hp);
		}
		if (config.hudPrayer() && member.getStatBase(Skill.PRAYER) > 0)
		{
			append(right, "Pr " + member.getStatBoost(Skill.PRAYER));
		}
		if (config.hudRunEnergy())
		{
			append(right, member.getRunEnergy() + "%");
		}
		WorldPoint coord = coordOf(member);
		if (config.hudPosition() && coord != null)
		{
			append(right, AreaNames.describe(coord));
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left(level > 0 && config.hudLevel() ? name + " (" + level + ")" : name)
			.leftColor(index.color(name))
			.leftFont(font)
			.right(right.toString())
			.rightColor(color)
			.rightFont(font)
			.build());
	}

	/**
	 * Where a member is. The party only sends a position once the player moves, so after joining
	 * before login our own would be missing until we walk; ours is read from the game instead.
	 */
	private WorldPoint coordOf(PartyMember member)
	{
		PartyMember local = partyService.getLocalMember();
		Player player = client.getLocalPlayer();
		if (local != null && local.getMemberId() == member.getMemberId() && player != null)
		{
			return WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		}
		return member.getCoord();
	}

	private void add(String label, String value, Color color, Font font)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(label)
			.leftFont(font)
			.right(value)
			.rightColor(color)
			.rightFont(font)
			.build());
	}

	private static void append(StringBuilder sb, String part)
	{
		if (sb.length() > 0)
		{
			sb.append("  ");
		}
		sb.append(part);
	}

	static Color hitpointsColor(int hp, int max)
	{
		if (max <= 0)
		{
			return Color.WHITE;
		}
		double ratio = (double) hp / max;
		if (ratio > 0.66)
		{
			return new Color(0x66BB6A);
		}
		return ratio > 0.33 ? new Color(0xFFA726) : new Color(0xEF5350);
	}

	private static Font fontFor(HomebaseConfig.HudSize size)
	{
		switch (size)
		{
			case SMALL:
				return FontManager.getRunescapeSmallFont();
			case LARGE:
				return FontManager.getRunescapeBoldFont();
			default:
				return FontManager.getRunescapeFont();
		}
	}

	private static int widthFor(HomebaseConfig.HudSize size, boolean compact)
	{
		int base;
		switch (size)
		{
			case SMALL:
				base = 130;
				break;
			case LARGE:
				base = 190;
				break;
			default:
				base = 155;
		}
		return compact ? base + 90 : base;
	}
}
