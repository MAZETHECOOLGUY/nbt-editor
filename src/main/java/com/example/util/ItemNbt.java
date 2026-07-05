package com.example.util;

import com.mojang.serialization.DataResult;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

/**
 * Converts an {@link ItemStack} to/from its full NBT representation
 * ({@code {id, count, components:{...}}}) using the vanilla item codec.
 * This is the same shape the {@code /give} command accepts, so editing this
 * tree edits the item id, count and every data component.
 */
public final class ItemNbt {
	private ItemNbt() {}

	private static RegistryOps<Tag> ops(RegistryAccess registries) {
		return RegistryOps.create(NbtOps.INSTANCE, registries);
	}

	/** Encode a (non-empty) stack to a CompoundTag. Returns null if the stack can't be encoded. */
	public static CompoundTag encode(ItemStack stack, RegistryAccess registries) {
		if (stack.isEmpty()) {
			return null;
		}
		DataResult<Tag> result = ItemStack.CODEC.encodeStart(ops(registries), stack);
		Tag tag = result.result().orElse(null);
		return (tag instanceof CompoundTag compound) ? compound : null;
	}

	/**
	 * Decode a CompoundTag back into an ItemStack.
	 * @return a DataResult so callers can report why an edit was rejected.
	 */
	public static DataResult<ItemStack> decode(CompoundTag tag, RegistryAccess registries) {
		return ItemStack.CODEC.parse(ops(registries), tag);
	}
}
