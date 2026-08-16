package com.example.tabs.network;

import com.example.NbtEditor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: delete a storage tab. The client only sends this once the player has confirmed;
 * the server still re-checks that the tab exists and is not the last one.
 */
public record DeleteTabPayload(int tabIndex) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DeleteTabPayload> TYPE =
			new CustomPacketPayload.Type<>(NbtEditor.id("delete_tab"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeleteTabPayload> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, DeleteTabPayload::tabIndex, DeleteTabPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
