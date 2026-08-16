package com.example.tabs.network;

import com.example.NbtEditor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: how many tabs the player has unlocked, and which one is showing.
 *
 * <p>The tab strip is drawn on the vanilla inventory screen too, so the client needs
 * the count even when the storage screen is closed. {@code activeTab} is {@code -1}
 * when no storage screen is open.
 */
public record TabSyncPayload(int tabCount, int activeTab) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TabSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(NbtEditor.id("tab_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TabSyncPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, TabSyncPayload::tabCount,
					ByteBufCodecs.INT, TabSyncPayload::activeTab,
					TabSyncPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
