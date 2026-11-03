package com.homebase.party;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * "Still here" while a member plays, and "going offline" just before they leave the party. The class
 * name is the message type on the wire, so it must not be renamed.
 */
@Getter
@RequiredArgsConstructor
public class HomebasePresence extends PartyMemberMessage
{
	private final boolean online;
}
