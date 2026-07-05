package com.example.client.mixin;

import com.example.client.ui.NbtEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opens the NBT editor when the player presses Shift+Space while hovering a slot
 * in any container screen.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void nbteditor$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (event.key() != GLFW.GLFW_KEY_SPACE) {
			return;
		}
		if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) {
			return;
		}
		if (hoveredSlot == null) {
			return;
		}

		ItemStack stack = hoveredSlot.getItem();
		if (stack.isEmpty()) {
			return;
		}

		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		AbstractContainerMenu menu = player.containerMenu;
		if (menu == null) {
			return;
		}

		// Player-inventory slots are addressed by their stable inventory index (0-40),
		// which works even in the creative inventory (a client-only menu). Other
		// containers (chests, etc.) are addressed via the open menu's synced slot id.
		boolean playerInventory = hoveredSlot.container == player.getInventory();
		int syncId = playerInventory ? -1 : menu.containerId;
		int slotIndex = playerInventory ? hoveredSlot.getContainerSlot() : hoveredSlot.index;

		Minecraft.getInstance().setScreen(NbtEditorScreen.open(
				playerInventory, syncId, slotIndex, stack, player.level().registryAccess()));
		// Swallow the key so Space doesn't do anything else.
		cir.setReturnValue(true);
	}
}
