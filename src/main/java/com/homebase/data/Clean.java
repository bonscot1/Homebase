package com.homebase.data;

/**
 * Text typed by other players reaches our panel and chat box. Swing renders labels starting with
 * {@code <html>} as HTML, which can load remote images, and the chat box understands tags, so angle
 * brackets and control characters are stripped before anything is shown.
 */
public final class Clean
{
	private Clean()
	{
	}

	/**
	 * @return the text without tags or control characters, trimmed and cut to {@code max} characters,
	 * or null if nothing is left
	 */
	public static String text(String text, int max)
	{
		if (text == null)
		{
			return null;
		}
		StringBuilder sb = new StringBuilder(Math.min(text.length(), max));
		for (int i = 0; i < text.length() && sb.length() < max; i++)
		{
			char c = text.charAt(i);
			if (c != '<' && c != '>' && !Character.isISOControl(c))
			{
				sb.append(c);
			}
		}
		String out = sb.toString().trim();
		return out.isEmpty() ? null : out;
	}
}
