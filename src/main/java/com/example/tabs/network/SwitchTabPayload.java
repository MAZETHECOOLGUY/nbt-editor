package com.example.tabs.network;

import com.example.NbtEditor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: show a different tab in the already-open storage screen. */
public record SwitchTabPayload(int tabIndex) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SwitchTabPayload> TYPE =
			new CustomPacketPayload.Type<>(NbtEditor.id("switch_tab"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchTabPayload> STREAM_CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, SwitchTabPayload::tabIndex, SwitchTabPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
