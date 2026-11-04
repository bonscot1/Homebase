package com.homebase.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileData
{
	private String name;

	/**
	 * World the member is on, or 0 once they have logged out.
	 */
	private int world;

	/**
	 * Name of their in-game Group Ironman group, if they are in one.
	 */
	private String groupName;

	/**
	 * The colour they chose, as 0xRRGGBB, or null from a version of the plugin that didn't send it.
	 */
	private Integer color;

	public ProfileData(String name, int world, String groupName)
	{
		this(name, world, groupName, null);
	}
}
