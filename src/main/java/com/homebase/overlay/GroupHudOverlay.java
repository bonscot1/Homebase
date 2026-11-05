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
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * An on-screen box with each online group member's hitpoints, prayer, run energy and special attack as bars, their
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
	private static final Color SPEC_FG = new Color(0x8E5CC8);
	private static final Color SPEC_BG = new Color(0x2B2140);
	private static final int BAR_GAP = 3;

	/**
	 * How much wider the box gets when all four bars are showing, to keep their numbers readable.
	 */
	private static final int FOURTH_BAR_WIDTH = 30;

	private static final int MAX_NAME = 20;

	/**
	 * How bright a name has to be to read on the HUD's dark box, from 0 to 255.
	 */
	private static final double MIN_NAME_BRIGHTNESS = 130;

	/**
	 * How long since it was last drawn that the box still counts as being there for a click.
	 */
	private static final long MAX_DRAWN_AGE_MS = 250;

	private final Client client;
	private final HomebaseConfig config;
	private final PartyService partyService;
	private final GroupSync groupSync;
	private final GroupIndex index;
	private final Presence presence;

	/**
	 * Where each member starts in the box as last drawn, top first. The mouse is read on another thread.
	 */
	private volatile List<MemberStart> memberStarts = Collections.emptyList();
	private List<MemberStart> drawing = new ArrayList<>();

	/**
	 * Whether the mouse was over plain ground as last drawn. The HUD is drawn under interfaces and over
	 * the game, so otherwise a click on it could really be meant for the bank on top of it or a monster
	 * behind it.
	 */
	private volatile boolean mouseOverGround;

	/**
	 * RuneLite doesn't draw overlays at the login screen, and that doesn't reach render, so this says
	 * whether what's above is still true.
	 */
	private volatile long drawnAt;

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
			memberStarts = Collections.emptyList();
			return null;
		}
		mouseOverGround = isMouseOverGround();
		List<PartyMember> members = visibleMembers();
		if (members.isEmpty())
		{
			memberStarts = Collections.emptyList();
			return null;
		}

		HomebaseConfig.HudSize size = config.hudSize();
		Font font = fontFor(size);
		boolean compact = config.hudCompact();
		panelComponent.setPreferredSize(new Dimension(boxWidth(size), 0));

		drawing = new ArrayList<>();
		for (PartyMember member : members)
		{
			panelComponent.getChildren().add(new MemberStart(member.getMemberId()));
			if (compact)
			{
				addCompact(member, font, size);
			}
			else
			{
				addFull(member, font, size);
			}
		}
		Dimension drawn = super.render(graphics);
		memberStarts = drawing;
		drawnAt = System.currentTimeMillis();
		return drawn;
	}

	/**
	 * @return the member whose part of the box a point on the screen is in, or null if it isn't in the
	 * box or a click there is meant for something else
	 */
	public Long memberAt(Point point)
	{
		Rectangle box = getBounds();
		if (!mouseOverGround || box == null || !box.contains(point) || System.currentTimeMillis() - drawnAt > MAX_DRAWN_AGE_MS)
		{
			return null;
		}
		int y = point.y - box.y;
		Long found = null;
		for (MemberStart start : memberStarts)
		{
			if (start.y > y)
			{
				break;
			}
			found = start.memberId;
		}
		return found;
	}

	private boolean isMouseOverGround()
	{
		if (client.isMenuOpen())
		{
			return false;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		// the last entry is the one a left-click would choose, leaving out what overlays add for right-clicks
		for (int i = entries.length - 1; i >= 0; i--)
		{
			if (entries[i].getType() != MenuAction.RUNELITE_OVERLAY)
			{
				return entries[i].getType() == MenuAction.WALK;
			}
		}
		return false;
	}

	/**
	 * Takes up no room, and notes where in the box a member's lines begin.
	 */
	private final class MemberStart implements LayoutableRenderableEntity
	{
		private final long memberId;
		private final Rectangle bounds = new Rectangle();
		private int y;

		MemberStart(long memberId)
		{
			this.memberId = memberId;
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			drawing.add(this);
			return new Dimension();
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}

		@Override
		public void setPreferredLocation(Point location)
		{
			y = location.y;
		}

		@Override
		public void setPreferredSize(Dimension size)
		{
		}
	}

	public List<PartyMember> visibleMembers()
	{
		PartyMember local = partyService.getLocalMember();
		boolean includeSelf = config.hudIncludeSelf();
		boolean sameWorldOnly = config.hudSameWorld();
		List<PartyMember> out = new ArrayList<>();
		for (PartyMember member : PartyMembers.current(partyService))
		{
			// a name that is nothing once cleaned would leave the title with no text to draw
			if (!presence.isOnline(member) || Clean.text(member.getDisplayName(), MAX_NAME) == null)
			{
				continue;
			}
			boolean self = local != null && local.getMemberId() == member.getMemberId();
			if (!includeSelf && self)
			{
				continue;
			}
			if (sameWorldOnly && !self && index.world(member.getDisplayName()) != client.getWorld())
			{
				continue;
			}
			out.add(member);
		}
		Comparator<PartyMember> byName = Comparator.comparing(PartyMember::getDisplayName, String.CASE_INSENSITIVE_ORDER);
		out.sort(config.hudOrder() == HomebaseConfig.HudOrder.LOWEST_HITPOINTS
			? Comparator.comparingDouble(this::hitpointsLeft).thenComparing(byName)
			: byName);
		return out;
	}

	/**
	 * @return how much of a member's hitpoints they have left, from 0 to 1
	 */
	private double hitpointsLeft(PartyMember member)
	{
		PartyMember local = partyService.getLocalMember();
		boolean self = local != null && local.getMemberId() == member.getMemberId();
		int max = self ? client.getRealSkillLevel(Skill.HITPOINTS) : member.getStatBase(Skill.HITPOINTS);
		int hp = self ? client.getBoostedSkillLevel(Skill.HITPOINTS) : member.getStatBoost(Skill.HITPOINTS);
		return max <= 0 ? 1 : (double) hp / max;
	}

	private void addFull(PartyMember member, Font font, HomebaseConfig.HudSize size)
	{
		String name = Clean.text(member.getDisplayName(), MAX_NAME);
		int level = index.combatLevel(name);
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(level > 0 && config.hudLevel() ? name + "  (level " + level + ")" : name)
			.color(readable(index.color(name)))
			.build());
		addBars(member, size);
		WorldPoint coord = presence.positionOf(member);
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
			.leftColor(readable(index.color(name)))
			.leftFont(font)
			.build());
		addBars(member, size);
	}

	/**
	 * Whichever of hitpoints, prayer, run energy and special attack are turned on, as bars side by side.
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
		if (config.hudSpecEnergy() && (self || presence.knowsSpec(member.getMemberId())))
		{
			// the game counts special attack energy in tenths of a percent
			int spec = self ? client.getVarpValue(VarPlayerID.SA_ENERGY) / 10 : member.getSpecEnergy();
			bars.add(bar(spec, 100, spec + "%", SPEC_FG, SPEC_BG));
		}
		if (bars.isEmpty())
		{
			return;
		}
		int width = boxWidth(size) - 2 * ComponentConstants.STANDARD_BORDER;
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

	/**
	 * @return the colour, lightened just enough to read if a player has chosen a dark one
	 */
	static Color readable(Color color)
	{
		double brightness = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
		if (brightness >= MIN_NAME_BRIGHTNESS)
		{
			return color;
		}
		double toWhite = (MIN_NAME_BRIGHTNESS - brightness) / (255 - brightness);
		return new Color(lighten(color.getRed(), toWhite), lighten(color.getGreen(), toWhite), lighten(color.getBlue(), toWhite));
	}

	private static int lighten(int channel, double toWhite)
	{
		return (int) Math.round(channel + (255 - channel) * toWhite);
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

	private int boxWidth(HomebaseConfig.HudSize size)
	{
		boolean allFourBars = config.hudHitpoints() && config.hudPrayer() && config.hudRunEnergy() && config.hudSpecEnergy();
		return widthFor(size) + (allFourBars ? FOURTH_BAR_WIDTH : 0);
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
