package com.homebase.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;

/**
 * The picture that marks a group member on the world map: an arrow in their colour, with their name
 * above it on a dark label so it reads against any part of the map.
 */
final class MapMarker
{
	/**
	 * Angles are in degrees clockwise from north, which is up on the map.
	 */
	static final int DOWN = 180;
	private static final int ANGLE_STEP = 45;

	/**
	 * The arrow's outline, pointing up around its middle.
	 */
	private static final int[] ARROW_X = {0, 9, 4, 4, -4, -4, -9};
	private static final int[] ARROW_Y = {-12, -1, -1, 12, 12, -1, -1};
	private static final int ARROW_TIP_DISTANCE = 12;

	/**
	 * The square the arrow can turn inside, with room for its black edge.
	 */
	private static final int ARROW_BOX = 28;
	private static final Stroke ARROW_EDGE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private static final int LABEL_PADDING_X = 3;
	private static final int LABEL_PADDING_Y = 1;
	private static final int LABEL_CORNER = 4;
	private static final Color LABEL_BACKGROUND = new Color(0, 0, 0, 170);

	/**
	 * A name in a colour darker than this can't be read on the label, so it's written in white.
	 */
	private static final int MIN_TEXT_BRIGHTNESS = 90;

	private MapMarker()
	{
	}

	static BufferedImage draw(String name, Color color, int angle)
	{
		Font font = FontManager.getRunescapeBoldFont();
		FontMetrics metrics = measuringGraphics().getFontMetrics(font);
		int labelWidth = metrics.stringWidth(name) + 2 * LABEL_PADDING_X;
		int labelHeight = metrics.getHeight() + 2 * LABEL_PADDING_Y;
		int width = Math.max(labelWidth, ARROW_BOX);
		int height = labelHeight + ARROW_BOX;

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		int labelX = (width - labelWidth) / 2;
		g.setColor(LABEL_BACKGROUND);
		g.fillRoundRect(labelX, 0, labelWidth, labelHeight, LABEL_CORNER, LABEL_CORNER);
		g.setFont(font);
		int textX = labelX + LABEL_PADDING_X;
		int textY = LABEL_PADDING_Y + metrics.getAscent();
		g.setColor(Color.BLACK);
		g.drawString(name, textX + 1, textY + 1);
		g.setColor(readableOnLabel(color));
		g.drawString(name, textX, textY);

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.translate(width / 2.0, height - ARROW_BOX / 2.0);
		g.rotate(Math.toRadians(angle));
		Polygon arrow = new Polygon(ARROW_X, ARROW_Y, ARROW_X.length);
		g.setColor(color);
		g.fill(arrow);
		g.setColor(Color.BLACK);
		g.setStroke(ARROW_EDGE);
		g.draw(arrow);
		g.dispose();
		return image;
	}

	/**
	 * @return where the tip of an arrow pointing {@link #DOWN} is in its picture, which is the pixel to
	 * put on the member's position
	 */
	static Point tip(BufferedImage image)
	{
		return new Point(image.getWidth() / 2, image.getHeight() - ARROW_BOX / 2 + ARROW_TIP_DISTANCE);
	}

	/**
	 * @return the angle from one spot on the map to another, to the nearest eighth of a turn
	 */
	static int angleBetween(int fromX, int fromY, int toX, int toY)
	{
		// y grows going north on the game's map, so this is clockwise from north
		double degrees = Math.toDegrees(Math.atan2(toX - fromX, toY - fromY));
		return Math.floorMod((int) (Math.round(degrees / ANGLE_STEP) * ANGLE_STEP), 360);
	}

	private static Color readableOnLabel(Color color)
	{
		int brightness = (299 * color.getRed() + 587 * color.getGreen() + 114 * color.getBlue()) / 1000;
		return brightness < MIN_TEXT_BRIGHTNESS ? Color.WHITE : color;
	}

	private static Graphics2D measuringGraphics()
	{
		return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
	}
}
