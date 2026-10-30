package com.homebase.data;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What changed for one member in one batch of updates, such as "Magic 15 → 32" and "completed The
 * Restless Ghost". Kept on this computer only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEntry
{
	private String memberId;
	private String name;
	private long time;
	private List<String> changes;

	/**
	 * Whether it has been shown in the chat box yet.
	 */
	private transient boolean announced;
}
