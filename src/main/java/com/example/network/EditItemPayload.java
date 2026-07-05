package com.example.network;

import com.example.NbtEditor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S packet: set an item to the one described by {@code itemTag}.
 *
 * <p>Two targeting modes, because the creative inventory uses a client-only menu
 * whose slot ids do not match the server's:
 * <ul>
 *   <li>{@code playerInventory == true}: {@code slotIndex} is a player-inventory
 *       container slot (0-40). Stable in both creative and survival.</li>
 *   <li>{@code playerInventory == false}: {@code syncId}+{@code slotIndex} address a
 *       slot in the currently open (server-synced) container menu, e.g. a chest.</li>
 * </ul>
 */
public record EditItemPayload(boolean playerInventory, int syncId, int slotIndex, CompoundTag itemTag)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<EditItemPayload> TYPE =
			new CustomPacketPayload.Type<>(NbtEditor.id("edit_item"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EditItemPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, EditItemPayload::playerInventory,
					ByteBufCodecs.VAR_INT, EditItemPayload::syncId,
					ByteBufCodecs.VAR_INT, EditItemPayload::slotIndex,
					ByteBufCodecs.COMPOUND_TAG, EditItemPayload::itemTag,
					EditItemPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
