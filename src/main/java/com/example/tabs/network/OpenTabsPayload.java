package com.example.tabs.network;

import com.example.NbtEditor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: open the storage-tab screen, showing {@code tabIndex}. */
public record OpenTabsPayload(int tabIndex) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenTabsPayload> TYPE =
			new CustomPacketPayload.Type<>(NbtEditor.id("open_tabs"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenTabsPayload> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenTabsPayload::tabIndex, OpenTabsPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
