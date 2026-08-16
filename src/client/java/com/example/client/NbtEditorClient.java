package com.example.client;

import com.example.client.tabs.TabsClient;
import net.fabricmc.api.ClientModInitializer;

public class NbtEditorClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TabsClient.init();
	}
}
