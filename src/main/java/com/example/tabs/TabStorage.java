package com.example.tabs;

import com.example.NbtEditor;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Owns the {@link PlayerTabs} attachment and the two places the server touches it. */
public final class TabStorage {
	/**
	 * Persistent, and copied to the respawned player so unlocked tabs survive death.
	 * The contents are emptied at death by {@link #dropAllAt}, so nothing is duplicated.
	 */
	public static final AttachmentType<PlayerTabs> TABS = AttachmentRegistry.<PlayerTabs>create(
			NbtEditor.id("tabs"),
			builder -> builder
					.persistent(PlayerTabs.CODEC)
					.copyOnDeath()
					.initializer(PlayerTabs::createDefault));

	private TabStorage() {}

	/** The player's tabs, creating the default single tab on first access. */
	public static PlayerTabs get(Player player) {
		return player.getAttachedOrCreate(TABS);
	}

	/**
	 * Cascade a stack that did not fit in the vanilla inventory across tabs 0..N.
	 * Shrinks {@code stack} in place.
	 *
	 * @return true if anything was absorbed (so the pickup counts as successful).
	 */
	public static boolean insert(ServerPlayer player, ItemStack stack) {
		return get(player).insert(stack);
	}

	/**
	 * Harvest every tab and drop the lot as a tight cluster of item entities where the
	 * player died, then leave the tabs empty (but still unlocked) for the respawn copy.
	 */
	public static void dropAllAt(ServerPlayer player) {
		PlayerTabs tabs = player.getAttached(TABS);
		if (tabs == null) {
			return;
		}

		List<ItemStack> contents = tabs.removeAllItems();
		if (contents.isEmpty()) {
			return;
		}

		ServerLevel level = player.level();
		double x = player.getX();
		double y = player.getY() + 0.5;
		double z = player.getZ();

		for (ItemStack stack : contents) {
			if (stack.isEmpty()) {
				continue;
			}
			// No throw velocity: everything lands in one pile instead of scattering.
			ItemEntity drop = new ItemEntity(level, x, y, z, stack, 0.0, 0.0, 0.0);
			drop.setDefaultPickUpDelay();
			level.addFreshEntity(drop);
		}

		NbtEditor.LOGGER.debug("Dropped {} stacks from {}'s storage tabs on death", contents.size(), player.getName().getString());
	}
}
