package com.homebase.sync;

/**
 * Record keys. Every key belongs to one of three namespaces:
 * <ul>
 *     <li>{@code m:<memberId>:<section>} - data owned by one member, only that member may write it</li>
 *     <li>{@code g:<name>} - data about the group as a whole, such as the group storage</li>
 *     <li>{@code s:<type>:<id>} - shared items any member can edit, such as requests and goals</li>
 * </ul>
 */
public final class Keys
{
	static final String MEMBER = "m:";
	static final String GROUP = "g:";
	static final String SHARED = "s:";

	private Keys()
	{
	}

	public static String member(String memberId, String section)
	{
		return MEMBER + memberId + ':' + section;
	}

	public static String memberPrefix(String memberId)
	{
		return MEMBER + memberId + ':';
	}

	public static String group(String name)
	{
		return GROUP + name;
	}

	public static String shared(String type, String id)
	{
		return SHARED + type + ':' + id;
	}

	public static String sharedPrefix(String type)
	{
		return SHARED + type + ':';
	}

	/**
	 * @return the owning member of a member key, or null for any other key
	 */
	public static String memberOf(String key)
	{
		if (!key.startsWith(MEMBER))
		{
			return null;
		}
		int end = key.indexOf(':', MEMBER.length());
		return end < 0 ? null : key.substring(MEMBER.length(), end);
	}

	/**
	 * @return the section of a member key, or null for any other key
	 */
	public static String sectionOf(String key)
	{
		if (!key.startsWith(MEMBER))
		{
			return null;
		}
		int end = key.indexOf(':', MEMBER.length());
		return end < 0 ? null : key.substring(end + 1);
	}

	public static boolean isValid(String key)
	{
		if (key == null || key.isEmpty() || key.length() > 128)
		{
			return false;
		}
		if (key.startsWith(MEMBER))
		{
			return memberOf(key) != null;
		}
		return key.startsWith(GROUP) || key.startsWith(SHARED);
	}
}
