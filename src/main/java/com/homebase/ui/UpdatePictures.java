package com.homebase.ui;

import com.homebase.data.KillCountsData;
import com.homebase.data.UpdateLine;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * The picture beside each line on the Updates tab: the skill for a level, the boss for a kill count or
 * best time, the item for a drop, and the game's own icons for quests, diaries and the rest.
 */
final class UpdatePictures
{
	private static final int SIZE = 20;
	private static final Color BULLET = new Color(0xFF9800);

	private final ItemSprites items;
	private final SkillIconManager skills;
	private final SpriteManager sprites;

	UpdatePictures(ItemSprites items, SkillIconManager skills, SpriteManager sprites)
	{
		this.items = items;
		this.skills = skills;
		this.sprites = sprites;
	}

	/**
	 * @return the picture for a line, or a plain bullet for one whose kind isn't known
	 */
	JComponent of(UpdateLine line)
	{
		Picture picture = new Picture();
		if (line == null || line.getKind() == null)
		{
			return picture;
		}
		switch (line.getKind())
		{
			case LEVEL:
				Skill skill = skillNamed(line.getSubject());
				if (skill != null)
				{
					picture.show(skills.getSkillImage(skill, true));
				}
				break;
			case KILL_COUNT:
			case BEST_TIME:
				sprite(picture, bossSprite(line.getSubject()));
				break;
			case DROP:
				item(picture, line.getItem() > 0 ? line.getItem() : ItemID.COINS_10000);
				break;
			case QUEST:
				sprite(picture, SpriteID.SideiconsInterface.QUESTS);
				break;
			case DIARY:
				sprite(picture, SpriteID.SideiconsInterface.ACHIEVEMENT_DIARIES);
				break;
			case COMBAT_ACHIEVEMENT:
				sprite(picture, SpriteID.SideiconsInterface.COMBAT);
				break;
			case COLLECTION_LOG:
				sprite(picture, SpriteID.IconActivities25x25.COLLECTIONS_LOGGED);
				break;
			case PET:
				sprite(picture, SpriteID.Mapfunction.PET_SHOP);
				break;
			case DEATH:
				sprite(picture, SpriteID.HeadiconsPkInterface.PLAYER_KILLER_SKULL);
				break;
			default:
		}
		return picture;
	}

	private void sprite(Picture picture, int spriteId)
	{
		// this can call back on the client thread, which is fine, since show only sets the image and repaints
		sprites.getSpriteAsync(spriteId, 0, picture::show);
	}

	private void item(Picture picture, int itemId)
	{
		AsyncBufferedImage image = items.picture(new Item(itemId, 1));
		picture.show(image);
		image.onLoaded(picture::repaint);
	}

	private static Skill skillNamed(String name)
	{
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(name))
			{
				return skill;
			}
		}
		return null;
	}

	/**
	 * @return the boss's picture from the hiscores, or the combat icon for one the hiscores don't have
	 */
	private static int bossSprite(String boss)
	{
		if (boss != null)
		{
			String key = KillCountsData.bossKey(boss);
			for (HiscoreSkill skill : HiscoreSkill.values())
			{
				if (KillCountsData.bossKey(skill.getName()).equals(key) && skill.getSpriteId() > 0)
				{
					return skill.getSpriteId();
				}
			}
		}
		return SpriteID.SideiconsInterface.COMBAT;
	}

	/**
	 * A fixed-size square showing a picture shrunk to fit, or a bullet until there is one.
	 */
	private static final class Picture extends JComponent
	{
		private volatile Image image;

		Picture()
		{
			Dimension size = new Dimension(SIZE, SIZE);
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
		}

		void show(Image image)
		{
			this.image = image;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			Image shown = image;
			int width = shown == null ? 0 : shown.getWidth(null);
			int height = shown == null ? 0 : shown.getHeight(null);
			if (width <= 0 || height <= 0)
			{
				g2.setColor(BULLET);
				g2.fillOval(getWidth() / 2 - 2, getHeight() / 2 - 2, 4, 4);
			}
			else
			{
				double scale = Math.min(1, Math.min((double) SIZE / width, (double) SIZE / height));
				int w = (int) Math.round(width * scale);
				int h = (int) Math.round(height * scale);
				g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g2.drawImage(shown, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
			}
			g2.dispose();
		}
	}
}
