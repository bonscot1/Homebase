package com.homebase.overlay;

import com.homebase.HomebaseConfig;
import com.homebase.data.Clean;
import com.homebase.data.GroupIndex;
import com.homebase.map.AreaNames;
import com.homebase.party.GroupSync;
import com.homebase.party.PartyMembers;
import com.homebase.party.Presence;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * An on-screen box with each online group member's hitpoints, prayer and run energy as bars, their
 * combat level and where they are. Live numbers come from RuneLite's party; combat level comes from
 * shared skills.
 */
@Singleton
public class GroupHudOverlay extends OverlayPanel
{
	private static final Color AREA = new Color(0xBDBDBD);

	/**
	 * Bar colours, the same as the sidebar panel's (and RuneLite's Party panel's).
	 */
	private static final Color HP_FG = new Color(0, 146, 54, 230);
	private static final Color HP_BG = new Color(102, 15, 16, 230);
	private static final Color PRAY_FG = new Color(0, 149, 151);
	private static final Color PRAY_BG = Color.BLACK;
	private static final Color RUN_FG = new Color(0xC8A01E);
	private static final Color RUN_BG = new Color(0x3A3220);
	private static final int BAR_GAP = 3;

	private final Client client;
	private final HomebaseConfig config;
	private final PartyService partyService;
	private final GroupSync groupSync;
	private final GroupIndex index;
	private final Presence presence;

	@Inject
	private GroupHudOverlay(Client client, HomebaseConfig config, PartyService partyService, GroupSync groupSync, GroupIndex index,
		Presence presence)
	{
		this.client = client;
		this.config = config;
		this.partyService = partyService;
		this.groupSync = groupSync;
		this.index = index;
		this.presence = presence;
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
		panelComponent.setPreferredSize(new Dimension(widthFor(size), 0));

		for (PartyMember member : members)
		{
			if (compact)
			{
				addCompact(member, font, size);
			}
			else
			{
				addFull(member, font, size);
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
			if (!presence.isOnline(member) || member.getDisplayName() == null)
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

	private void addFull(PartyMember member, Font font, HomebaseConfig.HudSize size)
	{
		String name = Clean.text(member.getDisplayName(), 20);
		int level = index.combatLevel(name);
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(level > 0 && config.hudLevel() ? name + "  (level " + level + ")" : name)
			.color(index.color(name))
			.build());
		addBars(member, size);
		WorldPoint coord = coordOf(member);
		if (config.hudPosition() && coord != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Area")
				.leftFont(font)
				.right(AreaNames.describe(coord))
				.rightColor(AREA)
				.rightFont(font)
				.build());
		}
	}

	private void addCompact(PartyMember member, Font font, HomebaseConfig.HudSize size)
	{
		String name = Clean.text(member.getDisplayName(), 20);
		int level = index.combatLevel(name);
		panelComponent.getChildren().add(LineComponent.builder()
			.left(level > 0 && config.hudLevel() ? name + " (" + level + ")" : name)
			.leftColor(index.color(name))
			.leftFont(font)
			.build());
		addBars(member, size);
	}

	/**
	 * Hitpoints, prayer and run energy as bars side by side, in the colours of the sidebar panel.
	 */
	private void addBars(PartyMember member, HomebaseConfig.HudSize size)
	{
		List<ProgressBarComponent> bars = new ArrayList<>();
		if (config.hudHitpoints() && member.getStatBase(Skill.HITPOINTS) > 0)
		{
			int hp = member.getStatBoost(Skill.HITPOINTS);
			int max = member.getStatBase(Skill.HITPOINTS);
			bars.add(bar(hp, max, hp + "/" + max, HP_FG, HP_BG));
		}
		if (config.hudPrayer() && member.getStatBase(Skill.PRAYER) > 0)
		{
			int prayer = member.getStatBoost(Skill.PRAYER);
			int max = member.getStatBase(Skill.PRAYER);
			bars.add(bar(prayer, max, prayer + "/" + max, PRAY_FG, PRAY_BG));
		}
		if (config.hudRunEnergy())
		{
			bars.add(bar(member.getRunEnergy(), 100, member.getRunEnergy() + "%", RUN_FG, RUN_BG));
		}
		if (bars.isEmpty())
		{
			return;
		}
		int width = widthFor(size) - 8;
		int each = (width - BAR_GAP * (bars.size() - 1)) / bars.size();
		int height = barHeight(size);
		PanelComponent row = new PanelComponent();
		row.setOrientation(ComponentOrientation.HORIZONTAL);
		row.setGap(new java.awt.Point(BAR_GAP, 0));
		row.setBorder(new Rectangle(0, 1, 0, 3));
		row.setBackgroundColor(null);
		for (ProgressBarComponent bar : bars)
		{
			bar.setPreferredSize(new Dimension(each, height));
			row.getChildren().add(bar);
		}
		panelComponent.getChildren().add(row);
	}

	private static ProgressBarComponent bar(int value, int max, String label, Color fg, Color bg)
	{
		ProgressBarComponent bar = new ProgressBarComponent();
		bar.setMinimum(0);
		bar.setMaximum(Math.max(1, max));
		bar.setValue(Math.max(0, Math.min(value, max)));
		bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
		bar.setCenterLabel(label);
		bar.setForegroundColor(fg);
		bar.setBackgroundColor(bg);
		bar.setFontColor(Color.WHITE);
		return bar;
	}

	private static int barHeight(HomebaseConfig.HudSize size)
	{
		switch (size)
		{
			case SMALL:
				return 12;
			case LARGE:
				return 18;
			default:
				return 15;
		}
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

	private static int widthFor(HomebaseConfig.HudSize size)
	{
		switch (size)
		{
			case SMALL:
				return 130;
			case LARGE:
				return 190;
			default:
				return 155;
		}
	}
}
