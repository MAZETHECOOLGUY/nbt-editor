package com.example;

import com.example.network.DeleteItemPayload;
import com.example.network.EditItemPayload;
import com.example.tabs.TabsFeature;
import com.example.util.ItemNbt;
import com.mojang.serialization.DataResult;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NbtEditor implements ModInitializer {
	public static final String MOD_ID = "nbt-editor";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Op permission level required to apply edits. The host / server owner has this. */
	public static final int REQUIRED_PERMISSION_LEVEL = 2;

	@Override
	public void onInitialize() {
		// Register the payload types on both sides so client and server agree on the wire format.
		PayloadTypeRegistry.serverboundPlay().register(EditItemPayload.TYPE, EditItemPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DeleteItemPayload.TYPE, DeleteItemPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(EditItemPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			// Networking callbacks run off the main thread; touch game state on the server thread.
			context.server().execute(() -> applyEdit(player, payload));
		});

		ServerPlayNetworking.registerGlobalReceiver(DeleteItemPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> deleteItem(player, payload));
		});

		TabsFeature.init();

		LOGGER.info("nbt-editor initialized");
	}

	private static void applyEdit(ServerPlayer player, EditItemPayload payload) {
		if (!canEdit(player)) {
			return;
		}

		DataResult<ItemStack> decoded = ItemNbt.decode(payload.itemTag(), player.level().registryAccess());
		ItemStack stack = decoded.result().orElse(null);
		if (stack == null) {
			reject(player, "Invalid item NBT: "
					+ decoded.error().map(DataResult.Error::message).orElse("unknown error"));
			return;
		}

		if (payload.playerInventory()) {
			// Player-inventory slot: stable across creative and survival.
			Inventory inventory = player.getInventory();
			int slot = payload.slotIndex();
			if (slot < 0 || slot >= inventory.getContainerSize()) {
				reject(player, "Invalid inventory slot " + slot + ".");
				return;
			}
			inventory.setItem(slot, stack);
			player.inventoryMenu.broadcastChanges();
		} else {
			// A real server-synced container (chest, etc.): address via the open menu.
			AbstractContainerMenu menu = player.containerMenu;
			if (menu == null || menu.containerId != payload.syncId()) {
				reject(player, "That container is no longer open.");
				return;
			}
			if (!menu.isValidSlotIndex(payload.slotIndex())) {
				reject(player, "Invalid slot.");
				return;
			}
			Slot slot = menu.getSlot(payload.slotIndex());
			slot.set(stack);
			menu.broadcastChanges();
		}

		player.sendSystemMessage(Component.literal("[nbt-editor] Item updated.").withStyle(ChatFormatting.GREEN));
	}

	/** Clear the targeted slot. Same targeting and permission gate as an edit. */
	private static void deleteItem(ServerPlayer player, DeleteItemPayload payload) {
		if (!canEdit(player)) {
			return;
		}

		ItemStack deleted;

		if (payload.playerInventory()) {
			Inventory inventory = player.getInventory();
			int slot = payload.slotIndex();
			if (slot < 0 || slot >= inventory.getContainerSize()) {
				reject(player, "Invalid inventory slot " + slot + ".");
				return;
			}
			deleted = inventory.getItem(slot).copy();
			inventory.setItem(slot, ItemStack.EMPTY);
			player.inventoryMenu.broadcastChanges();
		} else {
			AbstractContainerMenu menu = player.containerMenu;
			if (menu == null || menu.containerId != payload.syncId()) {
				reject(player, "That container is no longer open.");
				return;
			}
			if (!menu.isValidSlotIndex(payload.slotIndex())) {
				reject(player, "Invalid slot.");
				return;
			}
			Slot slot = menu.getSlot(payload.slotIndex());
			deleted = slot.getItem().copy();
			slot.set(ItemStack.EMPTY);
			menu.broadcastChanges();
		}

		if (deleted.isEmpty()) {
			return;
		}

		player.sendSystemMessage(Component.literal("[nbt-editor] Deleted ")
				.append(Component.literal(deleted.getCount() + "x ").append(deleted.getHoverName()))
				.append(Component.literal("."))
				.withStyle(ChatFormatting.YELLOW));
	}

	/** True if the player may edit or delete items; tells them why not otherwise. */
	private static boolean canEdit(ServerPlayer player) {
		Permission required = new Permission.HasCommandLevel(PermissionLevel.byId(REQUIRED_PERMISSION_LEVEL));
		if (player.permissions().hasPermission(required)) {
			return true;
		}
		reject(player, "You don't have permission to edit items (needs op).");
		return false;
	}

	private static void reject(ServerPlayer player, String message) {
		player.sendSystemMessage(Component.literal("[nbt-editor] " + message).withStyle(ChatFormatting.RED));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
