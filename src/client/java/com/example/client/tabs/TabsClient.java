package com.example.client.tabs;

import com.example.client.mixin.ContainerScreenAccessor;
import com.example.tabs.TabsMenu;
import com.example.tabs.network.OpenTabsPayload;
import com.example.tabs.network.TabSyncPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

/** Client-side wiring for the storage tabs: screen, keybind, and vanilla-inventory strip. */
public final class TabsClient {
	/** Defaults to the "+/=" key; rebindable under Controls -> Inventory. */
	private static final KeyMapping OPEN_TABS = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.nbt-editor.open_tabs", GLFW.GLFW_KEY_EQUAL, KeyMapping.Category.INVENTORY));

	private TabsClient() {}

	public static void init() {
		MenuScreens.register(TabsMenu.TYPE, TabsScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(TabSyncPayload.TYPE, (payload, context) -> {
			ClientTabState.setTabCount(payload.tabCount());
			if (context.player().containerMenu instanceof TabsMenu menu) {
				menu.applyClientState(payload.tabCount(), payload.activeTab());
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientTabState.reset());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_TABS.consumeClick()) {
				if (client.player != null && client.screen == null) {
					ClientPlayNetworking.send(new OpenTabsPayload(0));
				}
			}
		});

		// Draw and handle the same tab strip on the vanilla inventory screen, so the storage
		// reads as extra tabs of the inventory rather than as a separate screen.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen inventory)) {
				return;
			}
			ContainerScreenAccessor geometry = (ContainerScreenAccessor) inventory;

			ScreenEvents.afterBackground(screen).register((rendered, graphics, mouseX, mouseY, partialTick) ->
					TabStrip.render(graphics, client.font, geometry.nbteditor$leftPos(), geometry.nbteditor$topPos(),
							geometry.nbteditor$imageWidth(), geometry.nbteditor$imageHeight(),
							TabStrip.ENTRY_INVENTORY, mouseX, mouseY));

			// Delete over a tab in the strip deletes that tab. Over a slot it falls through
			// to AbstractContainerScreenMixin, which deletes the hovered item instead.
			ScreenKeyboardEvents.allowKeyPress(screen).register((pressed, event) ->
					event.key() != GLFW.GLFW_KEY_DELETE || !TabStrip.deleteHoveredTab(pressed));

			ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) -> {
				int entry = TabStrip.entryAt(geometry.nbteditor$leftPos(), geometry.nbteditor$topPos(),
						geometry.nbteditor$imageWidth(), geometry.nbteditor$imageHeight(), event.x(), event.y());
				if (entry == TabStrip.ENTRY_NONE) {
					return true;
				}
				TabStrip.click(entry, TabStrip.ENTRY_INVENTORY);
				return false;
			});
		});
	}
}
