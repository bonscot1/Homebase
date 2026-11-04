package com.homebase.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;

/**
 * The pictures that mark things on the world map: an arrow for a group member and a pin for a spot
 * they've pointed out, each in the member's colour. Either can have a name above it, on a dark label so
 * it reads against any part of the map.
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
	private static final Polygon ARROW = new Polygon(
		new int[]{0, 9, 4, 4, -4, -4, -9},
		new int[]{-12, -1, -1, 12, 12, -1, -1}, 7);

	/**
	 * How far an arrow pointing down, or a pin, reaches below the middle of its box.
	 */
	private static final int TIP_DISTANCE = 12;

	/**
	 * A pin's round head and its point, and the hole in its head.
	 */
	private static final Shape PIN = pinShape();
	private static final Shape PIN_HOLE = new Ellipse2D.Double(-3, -8, 6, 6);

	/**
	 * The square a picture can turn inside, with room for its black edge.
	 */
	private static final int BOX = 28;
	private static final Stroke EDGE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

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

	/**
	 * @param label the name to show above it, or null for none
	 * @param scale how much bigger or smaller than normal to draw it
	 */
	static BufferedImage arrow(String label, Color color, int angle, double scale)
	{
		BufferedImage image = canvas(label, color, scale);
		Graphics2D g = shapeGraphics(image, scale);
		g.rotate(Math.toRadians(angle));
		paint(g, ARROW, color);
		g.dispose();
		return image;
	}

	/**
	 * @param label the name to show above it, or null for none
	 * @param scale how much bigger or smaller than normal to draw it
	 */
	static BufferedImage pin(String label, Color color, double scale)
	{
		BufferedImage image = canvas(label, color, scale);
		Graphics2D g = shapeGraphics(image, scale);
		paint(g, PIN, color);
		g.setColor(Color.BLACK);
		g.fill(PIN_HOLE);
		g.dispose();
		return image;
	}

	/**
	 * @return where the tip of an arrow pointing {@link #DOWN}, or of a pin, is in its picture, which is
	 * the pixel to put on the spot it marks
	 */
	static Point tip(BufferedImage image, double scale)
	{
		return new Point(image.getWidth() / 2, (int) (image.getHeight() - boxSize(scale) / 2.0 + TIP_DISTANCE * scale));
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

	private static Shape pinShape()
	{
		Area shape = new Area(new Ellipse2D.Double(-8, -13, 16, 16));
		shape.add(new Area(new Polygon(new int[]{-6, 6, 0}, new int[]{0, 0, TIP_DISTANCE}, 3)));
		return shape;
	}

	private static int boxSize(double scale)
	{
		return (int) Math.ceil(BOX * scale);
	}

	/**
	 * @return a picture sized to fit the box and the label, with the label already drawn on it
	 */
	private static BufferedImage canvas(String label, Color color, double scale)
	{
		int box = boxSize(scale);
		if (label == null)
		{
			return new BufferedImage(box, box, BufferedImage.TYPE_INT_ARGB);
		}

		Font font = FontManager.getRunescapeBoldFont();
		Graphics2D measuring = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		FontMetrics metrics = measuring.getFontMetrics(font);
		measuring.dispose();
		int labelWidth = metrics.stringWidth(label) + 2 * LABEL_PADDING_X;
		int labelHeight = metrics.getHeight() + 2 * LABEL_PADDING_Y;
		int width = Math.max(labelWidth, box);

		BufferedImage image = new BufferedImage(width, labelHeight + box, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		int labelX = (width - labelWidth) / 2;
		g.setColor(LABEL_BACKGROUND);
		g.fillRoundRect(labelX, 0, labelWidth, labelHeight, LABEL_CORNER, LABEL_CORNER);
		g.setFont(font);
		int textX = labelX + LABEL_PADDING_X;
		int textY = LABEL_PADDING_Y + metrics.getAscent();
		g.setColor(Color.BLACK);
		g.drawString(label, textX + 1, textY + 1);
		g.setColor(readableOnLabel(color));
		g.drawString(label, textX, textY);
		g.dispose();
		return image;
	}

	/**
	 * @return graphics for drawing a shape around the middle of the box at the bottom of the picture
	 */
	private static Graphics2D shapeGraphics(BufferedImage image, double scale)
	{
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.translate(image.getWidth() / 2.0, image.getHeight() - boxSize(scale) / 2.0);
		g.scale(scale, scale);
		return g;
	}

	private static void paint(Graphics2D g, Shape shape, Color color)
	{
		g.setColor(color);
		g.fill(shape);
		g.setColor(Color.BLACK);
		g.setStroke(EDGE);
		g.draw(shape);
	}

	private static Color readableOnLabel(Color color)
	{
		int brightness = (299 * color.getRed() + 587 * color.getGreen() + 114 * color.getBlue()) / 1000;
		return brightness < MIN_TEXT_BRIGHTNESS ? Color.WHITE : color;
	}
}
