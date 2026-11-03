package com.homebase.pings;

import com.homebase.HomebaseConfig;
import com.homebase.data.GroupIndex;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Outlines every monster the group has highlighted, in the colour of whoever highlighted it.
 */
@Singleton
public class GroupHighlightOverlay extends Overlay
{
	private final Client client;
	private final HomebaseConfig config;
	private final GroupIndex index;
	private final ModelOutlineRenderer outlines;

	@Inject
	private GroupHighlightOverlay(Client client, HomebaseConfig config, GroupIndex index, ModelOutlineRenderer outlines)
	{
		this.client = client;
		this.config = config;
		this.index = index;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView view = client.getTopLevelWorldView();
		if (!config.npcHighlights() || client.getGameState() != GameState.LOGGED_IN || view == null)
		{
			return null;
		}
		boolean names = config.tileNames();
		for (GroupIndex.Highlight highlight : index.highlights())
		{
			if (highlight.getWorld() != client.getWorld())
			{
				continue;
			}
			NPC npc = view.npcs().byIndex(highlight.getIndex());
			if (npc == null || npc.getId() != highlight.getId())
			{
				continue;
			}
			outlines.drawOutline(npc, 2, highlight.getColor(), 4);
			if (names)
			{
				String text = highlight.getName();
				Point at = npc.getCanvasTextLocation(graphics, text, npc.getLogicalHeight() + 25);
				if (at != null)
				{
					OverlayUtil.renderTextLocation(graphics, at, text, highlight.getColor());
				}
			}
		}
		return null;
	}
}
