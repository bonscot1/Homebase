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
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LineComponent;
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

	private static final int MAX_NAME = 20;

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
			// a name that is nothing once cleaned would leave the title with no text to draw
			if (!presence.isOnline(member) || Clean.text(member.getDisplayName(), MAX_NAME) == null)
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
		String name = Clean.text(member.getDisplayName(), MAX_NAME);
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
		String name = Clean.text(member.getDisplayName(), MAX_NAME);
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
		// our own numbers come straight from the game, since the party only sends them once they change
		PartyMember local = partyService.getLocalMember();
		boolean self = local != null && local.getMemberId() == member.getMemberId();

		List<ProgressBarComponent> bars = new ArrayList<>();
		int maxHp = self ? client.getRealSkillLevel(Skill.HITPOINTS) : member.getStatBase(Skill.HITPOINTS);
		if (config.hudHitpoints() && maxHp > 0)
		{
			int hp = self ? client.getBoostedSkillLevel(Skill.HITPOINTS) : member.getStatBoost(Skill.HITPOINTS);
			bars.add(bar(hp, maxHp, hp + "/" + maxHp, HP_FG, HP_BG));
		}
		int maxPrayer = self ? client.getRealSkillLevel(Skill.PRAYER) : member.getStatBase(Skill.PRAYER);
		if (config.hudPrayer() && maxPrayer > 0)
		{
			int prayer = self ? client.getBoostedSkillLevel(Skill.PRAYER) : member.getStatBoost(Skill.PRAYER);
			bars.add(bar(prayer, maxPrayer, prayer + "/" + maxPrayer, PRAY_FG, PRAY_BG));
		}
		if (config.hudRunEnergy())
		{
			// the game counts run energy in hundredths of a percent
			int run = self ? client.getEnergy() / 100 : member.getRunEnergy();
			bars.add(bar(run, 100, run + "%", RUN_FG, RUN_BG));
		}
		if (bars.isEmpty())
		{
			return;
		}
		int width = widthFor(size) - 2 * ComponentConstants.STANDARD_BORDER;
		int each = (width - BAR_GAP * (bars.size() - 1)) / bars.size();
		panelComponent.getChildren().add(new BarRow(bars, each, barHeight(size), BAR_GAP, 1, 3));
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
	 * Where a member is. Our own is read straight from the game, since it's what we send everyone else.
	 */
	private WorldPoint coordOf(PartyMember member)
	{
		PartyMember local = partyService.getLocalMember();
		boolean self = local != null && local.getMemberId() == member.getMemberId();
		return self ? presence.localPosition() : presence.positionOf(member);
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
