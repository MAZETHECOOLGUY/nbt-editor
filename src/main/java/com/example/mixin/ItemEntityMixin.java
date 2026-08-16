package com.example.mixin;

import com.example.tabs.TabStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes picked-up items overflow into the player's storage tabs.
 *
 * <p>Redirecting the vanilla {@code Inventory.add} call inside {@code playerTouch} keeps
 * everything downstream of it - the pickup sound and animation, the statistic, and
 * discarding the entity once the stack is empty - working untouched: vanilla only needs
 * to be told that the stack was accepted.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Redirect(
			method = "playerTouch",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean nbteditor$cascadeIntoStorageTabs(Inventory inventory, ItemStack stack) {
		boolean addedToInventory = inventory.add(stack);

		if (!stack.isEmpty() && inventory.player instanceof ServerPlayer player) {
			return TabStorage.insert(player, stack) || addedToInventory;
		}

		return addedToInventory;
	}
}
