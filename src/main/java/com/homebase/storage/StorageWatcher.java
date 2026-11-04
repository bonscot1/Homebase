package com.homebase.storage;

import com.homebase.collect.LocalCollector;
import com.homebase.data.Section;
import com.homebase.data.StorageLogData;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

/**
 * Adds to our group storage log each time the game saves storage, and shares the log with the group.
 * Only one member can have group storage open at a time, so the changes seen there are our own.
 */
@Singleton
public class StorageWatcher
{
	private final Client client;
	private final LocalCollector collector;
	private final StorageSession session = new StorageSession();

	/**
	 * Set when the game's "Saving..." message closes, for the next game tick. By then the game has
	 * reported every change that came before the save.
	 */
	private boolean saveFinished;

	@Inject
	private StorageWatcher(Client client, LocalCollector collector)
	{
		this.client = client;
		this.collector = collector;
	}

	/**
	 * Forget storage in progress, when the plugin stops.
	 */
	public void reset()
	{
		session.reset();
		saveFinished = false;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV_GROUP_TEMP && event.getItemContainer() != null)
		{
			session.changed(StorageHistory.counts(event.getItemContainer().getItems()), isOpen());
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.GROUP_IRONMAN_STORAGE_BUILD)
		{
			ItemContainer storage = client.getItemContainer(InventoryID.INV_GROUP_TEMP);
			if (storage != null)
			{
				session.built(StorageHistory.counts(storage.getItems()), isOpen());
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.LOADING_ICON_MODAL)
		{
			// it's closing, so it no longer counts as showing, but its text is still there
			Widget text = client.getWidget(InterfaceID.LoadingIconModal.TEXT);
			saveFinished |= text != null && isSavingText(text.getText());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean saving = saveFinished || savingShown();
		saveFinished = false;
		add(session.tick(isOpen(), saving));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// the interface is gone without a save, so its changes are too
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	private void add(List<StorageHistory.Change> changes)
	{
		if (changes.isEmpty() || collector.getMemberId() == null)
		{
			return;
		}
		StorageLogData log = collector.shared(Section.STORAGE_LOG, StorageLogData.class);
		if (log == null)
		{
			log = new StorageLogData();
		}
		long now = System.currentTimeMillis();
		for (StorageHistory.Change change : changes)
		{
			log.add(change.getItem(), change.getAmount(), now);
		}
		collector.share(Section.STORAGE_LOG, log);
	}

	private boolean isOpen()
	{
		Widget items = client.getWidget(InterfaceID.SharedBank.ITEMS);
		return items != null && !items.isHidden();
	}

	/**
	 * @return true while the game's "Saving..." message is on screen. Its text can stay behind after it
	 * closes, which would turn later changes into saves if it counted.
	 */
	private boolean savingShown()
	{
		Widget text = client.getWidget(InterfaceID.LoadingIconModal.TEXT);
		return text != null && !text.isHidden() && isSavingText(text.getText());
	}

	/**
	 * @return true if the game's loading message is the one it shows while saving group storage
	 */
	static boolean isSavingText(String text)
	{
		return text != null && text.toLowerCase(Locale.ROOT).contains("saving");
	}
}
