package com.example.client.tabs;

import com.example.tabs.PlayerTabs;
import com.example.tabs.network.CreateTabPayload;
import com.example.tabs.network.DeleteTabPayload;
import com.example.tabs.network.OpenTabsPayload;
import com.example.tabs.network.SwitchTabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The creative-style row of tabs drawn above and below the inventory panel.
 *
 * <p>Entries are, in order: the vanilla inventory, then one per unlocked storage tab,
 * then the "+" button. That is at most {@code 1 + 10 + 1 = 12} entries, which is exactly
 * two rows of six - so unlike the creative screen this strip never needs to scroll.
 *
 * <p>The same strip is drawn on the vanilla inventory screen and on {@link TabsScreen},
 * which is what makes it feel like one screen with tabs rather than two screens.
 */
public final class TabStrip {
	public static final int TAB_WIDTH = 26;
	public static final int TAB_HEIGHT = 32;
	/** Entries per row; 6 * 27 = 162 fits inside the 176px wide panel. */
	public static final int PER_ROW = 6;
	private static final int SPACING = 27;
	/** How far a tab reaches over the panel edge, hidden for unselected tabs. */
	private static final int OVERLAP = 4;

	/** Entry id of the vanilla inventory tab. */
	public static final int ENTRY_INVENTORY = -1;
	/** Entry id of the "+" button. */
	public static final int ENTRY_NEW_TAB = -2;
	/** Returned by {@link #entryAt} when the cursor is not over the strip. */
	public static final int ENTRY_NONE = Integer.MIN_VALUE;

	private static final Identifier[] TOP_UNSELECTED = topSprites("unselected");
	private static final Identifier[] TOP_SELECTED = topSprites("selected");
	private static final Identifier[] BOTTOM_UNSELECTED = bottomSprites("unselected");
	private static final Identifier[] BOTTOM_SELECTED = bottomSprites("selected");

	private static final ItemStack INVENTORY_ICON = new ItemStack(Items.CRAFTING_TABLE);
	private static final ItemStack TAB_ICON = new ItemStack(Items.CHEST);

	private static final int ENABLED_TEXT = 0xFFFFFFFF;
	private static final int DISABLED_TEXT = 0xFF6E6E6E;

	/** Last entry the cursor was over, refreshed every frame by {@link #render}. */
	private static int hoveredEntry = ENTRY_NONE;

	private TabStrip() {}

	/** Entry ids in display order. */
	public static int[] entries() {
		int tabCount = ClientTabState.tabCount();
		int[] entries = new int[tabCount + 2];
		entries[0] = ENTRY_INVENTORY;
		for (int i = 0; i < tabCount; i++) {
			entries[i + 1] = i;
		}
		entries[entries.length - 1] = ENTRY_NEW_TAB;
		return entries;
	}

	/**
	 * @param selected the entry to draw on top of the panel, i.e. the one the open
	 *                 screen is showing.
	 */
	public static void render(GuiGraphicsExtractor graphics, Font font, int leftPos, int topPos,
			int imageWidth, int imageHeight, int selected, int mouseX, int mouseY) {
		int[] entries = entries();

		for (int position = 0; position < entries.length; position++) {
			if (entries[position] == selected) {
				continue;
			}
			// Clip away the part that overlaps the panel, so unselected tabs read as
			// sitting behind it even though they are drawn afterwards.
			boolean top = isTopRow(position);
			int clipTop = top ? topPos - TAB_HEIGHT : topPos + imageHeight;
			int clipBottom = top ? topPos : topPos + imageHeight + TAB_HEIGHT;
			graphics.enableScissor(leftPos - TAB_WIDTH, clipTop, leftPos + imageWidth + TAB_WIDTH, clipBottom);
			renderTab(graphics, font, leftPos, topPos, imageHeight, entries, position, false);
			graphics.disableScissor();
		}

		for (int position = 0; position < entries.length; position++) {
			if (entries[position] == selected) {
				renderTab(graphics, font, leftPos, topPos, imageHeight, entries, position, true);
			}
		}

		int hovered = entryAt(leftPos, topPos, imageWidth, imageHeight, mouseX, mouseY);
		// Remembered for the Delete key, which has no cursor position of its own.
		hoveredEntry = hovered;
		if (hovered != ENTRY_NONE) {
			graphics.setTooltipForNextFrame(font, tooltipFor(hovered), mouseX, mouseY);
		}
	}

	/** The entry under the cursor, or {@link #ENTRY_NONE}. */
	public static int entryAt(int leftPos, int topPos, int imageWidth, int imageHeight, double mouseX, double mouseY) {
		int[] entries = entries();

		for (int position = 0; position < entries.length; position++) {
			int x = tabX(leftPos, position);
			int y = tabY(topPos, imageHeight, position);
			if (mouseX >= x && mouseX < x + TAB_WIDTH && mouseY >= y && mouseY < y + TAB_HEIGHT) {
				return entries[position];
			}
		}

		return ENTRY_NONE;
	}

	/**
	 * Act on a click.
	 *
	 * @param selected which entry the currently open screen is showing.
	 * @return true if the click was consumed.
	 */
	public static boolean click(int entry, int selected) {
		if (entry == ENTRY_NONE || entry == selected) {
			return entry != ENTRY_NONE;
		}

		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return false;
		}

		if (entry == ENTRY_NEW_TAB && !ClientTabState.canCreateTab()) {
			// Greyed out at the cap: swallow the click without the confirmation sound.
			return true;
		}

		playClick(minecraft);

		if (entry == ENTRY_NEW_TAB) {
			ClientPlayNetworking.send(new CreateTabPayload());
		} else if (entry == ENTRY_INVENTORY) {
			// Tell the server the storage menu is gone before swapping in the vanilla screen.
			player.closeContainer();
			minecraft.setScreen(new InventoryScreen(player));
		} else if (selected == ENTRY_INVENTORY) {
			ClientPlayNetworking.send(new OpenTabsPayload(entry));
		} else {
			ClientPlayNetworking.send(new SwitchTabPayload(entry));
		}

		return true;
	}

	/**
	 * Handle the Delete key over the tab strip: prompt, and only then ask the server to
	 * delete the tab. Item deletion is handled per-slot elsewhere.
	 *
	 * @param returnTo the screen to restore once the prompt is answered.
	 * @return true if the key was consumed.
	 */
	public static boolean deleteHoveredTab(Screen returnTo) {
		int entry = hoveredEntry;
		if (entry < 0) {
			// Not over a storage tab (inventory tab, "+", or nothing at all).
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();

		if (ClientTabState.tabCount() <= 1) {
			// The last tab always stays. Ask anyway rather than swallowing the key, so the
			// server's refusal explains in chat why nothing happened.
			ClientPlayNetworking.send(new DeleteTabPayload(entry));
			return true;
		}

		playClick(minecraft);
		minecraft.setScreen(new ConfirmDeleteTabScreen(entry, returnTo));
		return true;
	}

	/**
	 * The delete-tab prompt. It always hands the player back to the screen they came from -
	 * including on Esc, which otherwise drops to the world while the server still believes
	 * the container is open.
	 */
	private static final class ConfirmDeleteTabScreen extends ConfirmScreen {
		private final Screen returnTo;

		private ConfirmDeleteTabScreen(int tabIndex, Screen returnTo) {
			super(
					confirmed -> {
						if (confirmed) {
							ClientPlayNetworking.send(new DeleteTabPayload(tabIndex));
						}
						// Restoring the same screen is safe: nothing sent a container-close
						// packet, so the menu is still open on both sides.
						Minecraft.getInstance().setScreen(returnTo);
					},
					Component.translatable("gui.nbt-editor.delete_tab_title", tabIndex + 1),
					Component.translatable("gui.nbt-editor.delete_tab_message"),
					Component.translatable("gui.nbt-editor.delete_tab_confirm"),
					CommonComponents.GUI_CANCEL);
			this.returnTo = returnTo;
		}

		@Override
		public void onClose() {
			this.minecraft.setScreen(this.returnTo);
		}
	}

	private static void renderTab(GuiGraphicsExtractor graphics, Font font, int leftPos, int topPos,
			int imageHeight, int[] entries, int position, boolean selected) {
		int entry = entries[position];
		boolean top = isTopRow(position);
		int x = tabX(leftPos, position);
		int y = tabY(topPos, imageHeight, position);

		Identifier[] sprites;
		if (top) {
			sprites = selected ? TOP_SELECTED : TOP_UNSELECTED;
		} else {
			sprites = selected ? BOTTOM_SELECTED : BOTTOM_UNSELECTED;
		}
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprites[spriteIndex(position, entries.length)], x, y, TAB_WIDTH, TAB_HEIGHT);

		int iconX = x + TAB_WIDTH / 2 - 8;
		int iconY = y + TAB_HEIGHT / 2 - 8 + (top ? 1 : -1);

		if (entry == ENTRY_NEW_TAB) {
			graphics.centeredText(font, "+", x + TAB_WIDTH / 2, iconY + 4,
					ClientTabState.canCreateTab() ? ENABLED_TEXT : DISABLED_TEXT);
		} else {
			graphics.item(entry == ENTRY_INVENTORY ? INVENTORY_ICON : TAB_ICON, iconX, iconY);
		}
	}

	private static Component tooltipFor(int entry) {
		if (entry == ENTRY_INVENTORY) {
			return Component.translatable("gui.nbt-editor.tab_inventory");
		}
		if (entry == ENTRY_NEW_TAB) {
			return ClientTabState.canCreateTab()
					? Component.translatable("gui.nbt-editor.tab_new", ClientTabState.tabCount(), PlayerTabs.MAX_TABS)
					: Component.translatable("gui.nbt-editor.tab_new_full", PlayerTabs.MAX_TABS);
		}
		return Component.translatable("gui.nbt-editor.tab_storage", entry + 1);
	}

	private static boolean isTopRow(int position) {
		return position < PER_ROW;
	}

	private static int tabX(int leftPos, int position) {
		return leftPos + (position % PER_ROW) * SPACING;
	}

	private static int tabY(int topPos, int imageHeight, int position) {
		return isTopRow(position)
				? topPos - TAB_HEIGHT + OVERLAP
				: topPos + imageHeight - OVERLAP;
	}

	/**
	 * The seven tab sprites differ at their edges. Cap the first and last entry of each
	 * row so a part-filled row still ends in a rounded edge instead of a cut-off one.
	 */
	private static int spriteIndex(int position, int entryCount) {
		int column = position % PER_ROW;
		if (column == 0) {
			return 0;
		}
		boolean lastInRow = position == entryCount - 1 || column == PER_ROW - 1;
		return lastInRow ? 6 : column;
	}

	private static void playClick(Minecraft minecraft) {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private static Identifier[] topSprites(String state) {
		return sprites("tab_top_" + state + "_");
	}

	private static Identifier[] bottomSprites(String state) {
		return sprites("tab_bottom_" + state + "_");
	}

	private static Identifier[] sprites(String prefix) {
		Identifier[] sprites = new Identifier[7];
		for (int i = 0; i < sprites.length; i++) {
			sprites[i] = Identifier.withDefaultNamespace("container/creative_inventory/" + prefix + (i + 1));
		}
		return sprites;
	}
}
