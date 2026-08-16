package com.example.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the panel geometry of any container screen.
 *
 * <p>Needed to draw the tab strip onto the vanilla inventory screen from a Fabric screen
 * event: {@code leftPos} in particular is not a constant, because the recipe book shifts
 * the panel sideways when it opens.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int nbteditor$leftPos();

	@Accessor("topPos")
	int nbteditor$topPos();

	@Accessor("imageWidth")
	int nbteditor$imageWidth();

	@Accessor("imageHeight")
	int nbteditor$imageHeight();
}
