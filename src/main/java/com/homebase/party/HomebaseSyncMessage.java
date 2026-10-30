package com.homebase.party;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Carries a compressed summary or batch of records over RuneLite's party service. The class name is
 * the message type on the wire, so it must stay unique among plugins and must not be renamed.
 */
@Getter
@RequiredArgsConstructor
public class HomebaseSyncMessage extends PartyMemberMessage
{
	static final String SUMMARY = "S";
	static final String RECORDS = "R";

	private final int protocol;
	private final String type;
	private final String payload;
}
