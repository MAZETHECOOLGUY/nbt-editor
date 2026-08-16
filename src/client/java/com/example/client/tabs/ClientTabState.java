package com.example.client.tabs;

import com.example.tabs.PlayerTabs;

/**
 * How many storage tabs the local player has unlocked, as last told by the server.
 *
 * <p>The tab strip is also drawn on the vanilla inventory screen, where no storage
 * menu exists to carry the count, so it is kept here instead.
 */
public final class ClientTabState {
	private static int tabCount = 1;

	private ClientTabState() {}

	public static int tabCount() {
		return tabCount;
	}

	public static void setTabCount(int count) {
		tabCount = Math.clamp(count, 1, PlayerTabs.MAX_TABS);
	}

	public static boolean canCreateTab() {
		return tabCount < PlayerTabs.MAX_TABS;
	}

	/** Back to the default on disconnect, so a stale count never leaks into the next world. */
	public static void reset() {
		tabCount = 1;
	}
}
