package com.example.network;

import com.example.NbtEditor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: clear the targeted slot.
 *
 * <p>Targeting works exactly like {@link EditItemPayload}: either a player-inventory
 * slot index, or a slot id in the currently open container menu.
 */
public record DeleteItemPayload(boolean playerInventory, int syncId, int slotIndex)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DeleteItemPayload> TYPE =
			new CustomPacketPayload.Type<>(NbtEditor.id("delete_item"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeleteItemPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, DeleteItemPayload::playerInventory,
					ByteBufCodecs.VAR_INT, DeleteItemPayload::syncId,
					ByteBufCodecs.VAR_INT, DeleteItemPayload::slotIndex,
					DeleteItemPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
