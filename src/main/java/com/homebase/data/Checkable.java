package com.homebase.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data that arrives from other members' clients. Whatever those clients send could be malformed, so
 * after it's read, missing lists become empty, nulls are dropped and numbers are kept in range. The
 * rest of the plugin can then use it without checking every field.
 */
public interface Checkable
{
	/**
	 * Fix anything a malformed message left missing or out of range.
	 */
	void check();

	/**
	 * @return the same data, checked if it's a kind that can be
	 */
	static <T> T checked(T data)
	{
		if (data instanceof Checkable)
		{
			((Checkable) data).check();
		}
		return data;
	}

	/**
	 * @return the list without nulls and cut to {@code max} entries, or an empty list for none
	 */
	static <T> List<T> list(List<T> list, int max)
	{
		List<T> out = new ArrayList<>();
		if (list != null)
		{
			for (T value : list)
			{
				if (value != null && out.size() < max)
				{
					out.add(value);
				}
			}
		}
		return out;
	}

	/**
	 * @return the map without null keys or values, or an empty map for none
	 */
	static <K, V> Map<K, V> map(Map<K, V> map)
	{
		return map(map, Integer.MAX_VALUE);
	}

	/**
	 * @return the map without null keys or values and cut to {@code max} entries, or an empty map for none
	 */
	static <K, V> Map<K, V> map(Map<K, V> map, int max)
	{
		Map<K, V> out = new HashMap<>();
		if (map != null)
		{
			for (Map.Entry<K, V> entry : map.entrySet())
			{
				if (out.size() >= max)
				{
					break;
				}
				if (entry.getKey() != null && entry.getValue() != null)
				{
					out.put(entry.getKey(), entry.getValue());
				}
			}
		}
		return out;
	}

	/**
	 * @return the map without nulls, with every number between 0 and {@code maxValue}
	 */
	static <K> Map<K, Integer> counts(Map<K, Integer> map, int maxValue)
	{
		Map<K, Integer> out = map(map);
		out.replaceAll((key, value) -> Math.max(0, Math.min(maxValue, value)));
		return out;
	}
}
