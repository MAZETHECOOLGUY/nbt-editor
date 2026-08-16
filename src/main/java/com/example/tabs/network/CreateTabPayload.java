package com.example.tabs.network;

import com.example.NbtEditor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the "+" button was pressed. The server decides whether the cap allows it. */
public record CreateTabPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CreateTabPayload> TYPE =
			new CustomPacketPayload.Type<>(NbtEditor.id("create_tab"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CreateTabPayload> STREAM_CODEC =
			StreamCodec.unit(new CreateTabPayload());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
