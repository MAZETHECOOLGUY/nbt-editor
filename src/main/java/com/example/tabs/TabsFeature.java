package com.example.tabs;

import com.example.tabs.network.CreateTabPayload;
import com.example.tabs.network.OpenTabsPayload;
import com.example.tabs.network.SwitchTabPayload;
import com.example.tabs.network.TabSyncPayload;
import java.util.Objects;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Server-side wiring for the multi-tab storage: packet types, their handlers, and the
 * death hook that spills every tab at the death spot.
 */
public final class TabsFeature {
	/** No storage screen is open. */
	private static final int NO_ACTIVE_TAB = -1;

	private TabsFeature() {}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(OpenTabsPayload.TYPE, OpenTabsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SwitchTabPayload.TYPE, SwitchTabPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CreateTabPayload.TYPE, CreateTabPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TabSyncPayload.TYPE, TabSyncPayload.STREAM_CODEC);

		// Touch both classes so the attachment and the menu type are registered during init
		// rather than lazily, the first time some player happens to open the screen.
		Objects.requireNonNull(TabStorage.TABS);
		Objects.requireNonNull(TabsMenu.TYPE);

		ServerPlayNetworking.registerGlobalReceiver(OpenTabsPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> openTabs(player, payload.tabIndex()));
		});

		ServerPlayNetworking.registerGlobalReceiver(SwitchTabPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> switchTab(player, payload.tabIndex()));
		});

		ServerPlayNetworking.registerGlobalReceiver(CreateTabPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> createTab(player));
		});

		// The strip is drawn on the vanilla inventory screen, so hand out the count on join.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player, NO_ACTIVE_TAB));

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof ServerPlayer player) || player.isSpectator()) {
				return;
			}
			if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
				return;
			}
			if (player.containerMenu instanceof TabsMenu) {
				player.closeContainer();
			}
			TabStorage.dropAllAt(player);
		});
	}

	private static void openTabs(ServerPlayer player, int tabIndex) {
		PlayerTabs tabs = TabStorage.get(player);
		player.openMenu(TabsMenu.provider(tabs, tabIndex));
		sync(player, activeTabOf(player));
	}

	private static void switchTab(ServerPlayer player, int tabIndex) {
		if (!(player.containerMenu instanceof TabsMenu menu)) {
			return;
		}
		menu.setActiveTab(tabIndex);
		sync(player, menu.getActiveTab());
	}

	private static void createTab(ServerPlayer player) {
		PlayerTabs tabs = TabStorage.get(player);
		int created = tabs.addTab();

		if (created == -1) {
			player.sendSystemMessage(Component.translatable("message.nbt-editor.tab_limit", PlayerTabs.MAX_TABS)
					.withStyle(ChatFormatting.RED));
			sync(player, activeTabOf(player));
			return;
		}

		if (player.containerMenu instanceof TabsMenu menu) {
			menu.setActiveTab(created);
		} else {
			// "+" was pressed from the vanilla inventory screen: open straight into the new tab.
			player.openMenu(TabsMenu.provider(tabs, created));
		}
		sync(player, activeTabOf(player));
	}

	private static int activeTabOf(ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		return menu instanceof TabsMenu tabsMenu ? tabsMenu.getActiveTab() : NO_ACTIVE_TAB;
	}

	private static void sync(ServerPlayer player, int activeTab) {
		if (!ServerPlayNetworking.canSend(player, TabSyncPayload.TYPE)) {
			// Vanilla client: the storage still works, there is just no GUI to update.
			return;
		}
		ServerPlayNetworking.send(player, new TabSyncPayload(TabStorage.get(player).tabCount(), activeTab));
	}
}
