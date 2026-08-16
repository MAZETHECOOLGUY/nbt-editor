package com.example.tabs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * The extra storage a player owns: an ordered list of double-chest sized tabs.
 *
 * <p>Every player starts with exactly one tab and can unlock up to {@link #MAX_TABS}
 * from the GUI. The list is mutable and lives for as long as the player entity does;
 * it is persisted through a Fabric data attachment (see {@link TabStorage}).
 *
 * <p>Serialized shape:
 * <pre>{@code
 * { "TabCount": 3, "Tabs": [ { "Index": 0, "Items": [ { "Slot": 0, "id": ... } ] }, ... ] }
 * }</pre>
 * {@code Items} is sparse - empty slots are simply absent.
 */
public final class PlayerTabs {
	/** Slots per tab: 9x6, i.e. a double chest. */
	public static final int TAB_SIZE = 54;
	/** Hard cap on unlocked tabs. */
	public static final int MAX_TABS = 10;

	public static final Codec<PlayerTabs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			// Deliberately not a range: an out-of-range count is clamped on read rather than
			// failing the whole attachment and taking every stored item with it.
			Codec.INT.fieldOf("TabCount").forGetter(PlayerTabs::tabCount),
			SerializedTab.CODEC.listOf().fieldOf("Tabs").forGetter(PlayerTabs::serializeTabs)
	).apply(instance, PlayerTabs::deserialize));

	private final List<SimpleContainer> tabs;

	private PlayerTabs(List<SimpleContainer> tabs) {
		this.tabs = tabs;
	}

	/** A brand new player: one empty tab. */
	public static PlayerTabs createDefault() {
		return createEmpty(1);
	}

	/** {@code count} empty tabs. Used for the client-side mirror of the menu, too. */
	public static PlayerTabs createEmpty(int count) {
		int clamped = Math.clamp(count, 1, MAX_TABS);
		List<SimpleContainer> tabs = new ArrayList<>(clamped);
		for (int i = 0; i < clamped; i++) {
			tabs.add(new SimpleContainer(TAB_SIZE));
		}
		return new PlayerTabs(tabs);
	}

	public int tabCount() {
		return this.tabs.size();
	}

	public SimpleContainer get(int index) {
		return this.tabs.get(Math.clamp(index, 0, this.tabs.size() - 1));
	}

	public List<SimpleContainer> tabs() {
		return Collections.unmodifiableList(this.tabs);
	}

	public boolean isValidIndex(int index) {
		return index >= 0 && index < this.tabs.size();
	}

	public boolean canAddTab() {
		return this.tabs.size() < MAX_TABS;
	}

	/**
	 * Unlock one more tab.
	 * @return the new tab's index, or {@code -1} if the cap has been reached.
	 */
	public int addTab() {
		if (!this.canAddTab()) {
			return -1;
		}
		this.tabs.add(new SimpleContainer(TAB_SIZE));
		return this.tabs.size() - 1;
	}

	/**
	 * Insert a stack, filling tab 0 first and cascading forward.
	 * The passed stack is shrunk in place by however much was absorbed.
	 *
	 * @return true if at least one item was absorbed.
	 */
	public boolean insert(ItemStack stack) {
		boolean absorbedAny = false;
		for (SimpleContainer tab : this.tabs) {
			if (stack.isEmpty()) {
				break;
			}
			// addItem() works on a copy and hands back what did not fit.
			ItemStack leftover = tab.addItem(stack);
			if (leftover.getCount() < stack.getCount()) {
				absorbedAny = true;
				stack.setCount(leftover.getCount());
			}
		}
		return absorbedAny;
	}

	/** Empty every tab and hand back everything that was in them. Tab count is untouched. */
	public List<ItemStack> removeAllItems() {
		List<ItemStack> contents = new ArrayList<>();
		for (SimpleContainer tab : this.tabs) {
			contents.addAll(tab.removeAllItems());
		}
		return contents;
	}

	private List<SerializedTab> serializeTabs() {
		List<SerializedTab> serialized = new ArrayList<>(this.tabs.size());
		for (int index = 0; index < this.tabs.size(); index++) {
			SimpleContainer tab = this.tabs.get(index);
			List<ItemStackWithSlot> items = new ArrayList<>();
			for (int slot = 0; slot < TAB_SIZE; slot++) {
				ItemStack stack = tab.getItem(slot);
				if (!stack.isEmpty()) {
					items.add(new ItemStackWithSlot(slot, stack));
				}
			}
			serialized.add(new SerializedTab(index, items));
		}
		return serialized;
	}

	private static PlayerTabs deserialize(int tabCount, List<SerializedTab> serialized) {
		// Trust whichever is larger so a mismatched TabCount can never silently eat a tab.
		int highestIndex = 0;
		for (SerializedTab tab : serialized) {
			highestIndex = Math.max(highestIndex, tab.index());
		}
		PlayerTabs tabs = createEmpty(Math.max(tabCount, highestIndex + 1));

		for (SerializedTab tab : serialized) {
			if (!tabs.isValidIndex(tab.index())) {
				continue;
			}
			SimpleContainer container = tabs.tabs.get(tab.index());
			for (ItemStackWithSlot item : tab.items()) {
				if (item.isValidInContainer(TAB_SIZE)) {
					container.setItem(item.slot(), item.stack());
				}
			}
		}
		return tabs;
	}

	private record SerializedTab(int index, List<ItemStackWithSlot> items) {
		static final Codec<SerializedTab> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("Index").forGetter(SerializedTab::index),
				ItemStackWithSlot.CODEC.listOf().fieldOf("Items").forGetter(SerializedTab::items)
		).apply(instance, SerializedTab::new));
	}
}
