package com.example.tabs;

import com.example.NbtEditor;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A double-chest sized view onto one of the player's storage tabs, plus the usual
 * 36 player-inventory slots.
 *
 * <p>Switching tabs does not rebuild the slots: they all point at {@link TabView},
 * which forwards to whichever {@link SimpleContainer} is currently active. Changing
 * the active index therefore only needs a re-sync, and slot ids stay stable.
 */
public class TabsMenu extends AbstractContainerMenu {
	public static final int ROWS = 6;
	public static final int COLUMNS = 9;
	/** Slot ids [0, TAB_SLOTS) are the active tab; the rest are the player inventory. */
	public static final int TAB_SLOTS = PlayerTabs.TAB_SIZE;

	public static final ExtendedMenuType<TabsMenu, Data> TYPE = Registry.register(
			BuiltInRegistries.MENU,
			NbtEditor.id("storage_tabs"),
			new ExtendedMenuType<>(
					(containerId, inventory, data) ->
							new TabsMenu(containerId, inventory, PlayerTabs.createEmpty(1), data.activeTab(), data.tabCount()),
					Data.STREAM_CODEC));

	private final PlayerTabs tabs;
	private final TabView view;
	/**
	 * True on the client, where {@link #tabs} is a single scratch container that whatever
	 * the server last sent is displayed in. Keeping one container there means the active
	 * index only picks which tab is highlighted, never where incoming contents land - so
	 * a tab switch cannot race its own content packet.
	 */
	private final boolean clientMirror;
	/** Client only: how many tabs exist, which the single mirror container cannot say. */
	private int mirroredTabCount;
	private int activeTab;

	/** Server side view onto the player's real tabs. */
	public TabsMenu(int containerId, Inventory inventory, PlayerTabs tabs, int activeTab) {
		this(containerId, inventory, tabs, activeTab, false, tabs.tabCount());
	}

	/** Client side mirror; {@code tabs} is a single scratch container. */
	public TabsMenu(int containerId, Inventory inventory, PlayerTabs tabs, int activeTab, int tabCount) {
		this(containerId, inventory, tabs, activeTab, true, tabCount);
	}

	private TabsMenu(int containerId, Inventory inventory, PlayerTabs tabs, int activeTab, boolean clientMirror, int tabCount) {
		super(TYPE, containerId);
		this.tabs = tabs;
		this.clientMirror = clientMirror;
		this.mirroredTabCount = tabCount;
		this.activeTab = Math.clamp(activeTab, 0, Math.max(tabCount - 1, 0));
		this.view = new TabView();

		for (int row = 0; row < ROWS; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				this.addSlot(new Slot(this.view, column + row * COLUMNS, 8 + column * 18, 18 + row * 18));
			}
		}

		this.addStandardInventorySlots(inventory, 8, 18 + ROWS * 18 + 13);
	}

	/** Opens at {@code index}, or at the last tab if the index is out of range. */
	public static MenuProvider provider(PlayerTabs tabs, int index) {
		int target = Math.clamp(index, 0, tabs.tabCount() - 1);
		return new ExtendedMenuProvider<Data>() {
			@Override
			public Component getDisplayName() {
				return Component.translatable("container.nbt-editor.tabs");
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
				return new TabsMenu(containerId, inventory, tabs, target);
			}

			@Override
			public Data getScreenOpeningData(ServerPlayer player) {
				return new Data(tabs.tabCount(), target);
			}
		};
	}

	public int getActiveTab() {
		return this.activeTab;
	}

	public int getTabCount() {
		return this.clientMirror ? this.mirroredTabCount : this.tabs.tabCount();
	}

	/** Server side: point the slots at another tab and push that tab's contents out. */
	public void setActiveTab(int index) {
		if (this.clientMirror || !this.tabs.isValidIndex(index) || index == this.activeTab) {
			return;
		}
		this.activeTab = index;
		this.sendAllDataToRemote();
	}

	/**
	 * Server side: the tab list changed underneath the menu, so re-point it and push
	 * everything again. Unlike {@link #setActiveTab} this always re-syncs, because after
	 * a deletion the same index can mean a different tab.
	 */
	public void refreshAfterTabRemoval(int index) {
		if (this.clientMirror) {
			return;
		}
		this.activeTab = Math.clamp(index, 0, this.tabs.tabCount() - 1);
		this.sendAllDataToRemote();
	}

	/** Client side: adopt the tab count and highlight the server says we are looking at. */
	public void applyClientState(int tabCount, int activeTab) {
		if (!this.clientMirror) {
			return;
		}
		this.mirroredTabCount = tabCount;
		if (activeTab >= 0) {
			this.activeTab = Math.clamp(activeTab, 0, Math.max(tabCount - 1, 0));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack original = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);

		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			original = stack.copy();

			if (slotIndex < TAB_SLOTS) {
				if (!this.moveItemStackTo(stack, TAB_SLOTS, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, TAB_SLOTS, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		// The tabs travel with the player, so there is nothing to be out of range of.
		return true;
	}

	/** Data the server hands the client when the screen opens. */
	public record Data(int tabCount, int activeTab) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Data::tabCount,
				ByteBufCodecs.VAR_INT, Data::activeTab,
				Data::new);
	}

	/** Forwards every slot operation to the currently active tab. */
	private final class TabView implements Container {
		private SimpleContainer active() {
			return TabsMenu.this.tabs.get(TabsMenu.this.clientMirror ? 0 : TabsMenu.this.activeTab);
		}

		@Override
		public int getContainerSize() {
			return PlayerTabs.TAB_SIZE;
		}

		@Override
		public boolean isEmpty() {
			return this.active().isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return this.active().getItem(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			return this.active().removeItem(slot, count);
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return this.active().removeItemNoUpdate(slot);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.active().setItem(slot, stack);
		}

		@Override
		public void setChanged() {
			this.active().setChanged();
		}

		@Override
		public void clearContent() {
			this.active().clearContent();
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
