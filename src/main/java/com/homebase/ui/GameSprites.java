package com.homebase.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.game.SpriteManager;

/**
 * Game interface images, such as the equipment slot tiles, kept once loaded. The game only has them
 * from the login screen on, so a component asks for one and is repainted when it arrives. Used on the
 * Swing thread.
 */
final class GameSprites
{
	private final SpriteManager spriteManager;
	private final Map<Integer, BufferedImage> loaded = new ConcurrentHashMap<>();
	private final Map<Integer, List<JComponent>> waiting = new HashMap<>();

	GameSprites(SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
	}

	/**
	 * @return the sprite if it's loaded; otherwise null, and {@code component} is repainted once it is
	 */
	BufferedImage get(int spriteId, JComponent component)
	{
		BufferedImage image = loaded.get(spriteId);
		if (image != null)
		{
			return image;
		}
		List<JComponent> list = waiting.get(spriteId);
		if (list != null)
		{
			list.add(component);
			return null;
		}
		list = new ArrayList<>();
		list.add(component);
		waiting.put(spriteId, list);
		spriteManager.getSpriteAsync(spriteId, 0, img ->
		{
			loaded.put(spriteId, img);
			SwingUtilities.invokeLater(() ->
			{
				List<JComponent> repaint = waiting.remove(spriteId);
				if (repaint != null)
				{
					repaint.forEach(JComponent::repaint);
				}
			});
		});
		return null;
	}
}
