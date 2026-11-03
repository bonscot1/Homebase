package com.homebase.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * What a member has built in their player-owned house, captured while they're in it.
 */
@Data
public class HouseData implements Checkable
{
	private static final int MAX_ENTRIES = 1_000;

	public static final int MAX_FURNITURE = 100;
	public static final int MAX_COSTUMES = 2000;

	/**
	 * Names of useful furniture built, such as "Ornate rejuvenation pool" and "Portal nexus", sorted.
	 */
	private List<String> furniture = new ArrayList<>();

	/**
	 * Item ids stored in the costume room, as seen when the member last opened its storage.
	 */
	private List<Integer> costumes = new ArrayList<>();

	@Override
	public void check()
	{
		furniture = Checkable.list(furniture, MAX_ENTRIES);
		costumes = Checkable.list(costumes, MAX_ENTRIES);
	}
}
