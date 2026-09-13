package com.example.mixin;

import com.example.tabs.TabStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes anything that does not fit in the vanilla inventory overflow into the player's
 * storage tabs.
 *
 * <p>This deliberately hooks {@code Inventory.add} rather than the {@code Inventory.add}
 * call site inside {@code ItemEntity.playerTouch}: that call is a popular {@code @Redirect}
 * target (Collective redirects it, for one), and only one redirect may own an instruction,
 * so whichever mod loses on priority gets its injection silently dropped - fatally so with
 * {@code defaultRequire: 1}. An {@code @Inject} on the method itself takes over nothing and
 * therefore stacks with any number of foreign injectors.
 *
 * <p>Injecting at {@code RETURN} means vanilla has already taken everything it could and
 * {@code stack} has been shrunk to the leftover, so the tabs only ever see the overflow.
 * Reporting the insert as successful keeps everything downstream working untouched - for a
 * pickup that is the sound and animation, the statistic, and discarding the item entity.
 *
 * <p>{@code add(ItemStack)} is the "put this anywhere" overload, so besides ground pickups
 * this also catches {@code /give}, {@code Player.addItem} and mod-driven inserts. The
 * slot-targeted {@code add(int, ItemStack)} is left alone: a caller that asked for one
 * specific slot does not want the item teleported into a storage tab instead.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Inject(
			method = "add(Lnet/minecraft/world/item/ItemStack;)Z",
			at = @At("RETURN"),
			cancellable = true)
	private void nbteditor$cascadeIntoStorageTabs(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		// The mixin class is unrelated to Inventory at compile time; this is the standard
		// way to reach the target's own members without shadowing a blank final field.
		Inventory inventory = (Inventory) (Object) this;

		if (stack.isEmpty() || !(inventory.player instanceof ServerPlayer player)) {
			return;
		}

		if (TabStorage.insert(player, stack)) {
			cir.setReturnValue(true);
		}
	}
}
