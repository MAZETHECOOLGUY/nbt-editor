package com.example.client.tabs;

import com.example.tabs.TabsMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The storage screen: a double chest worth of slots, with the tab strip around it. */
public class TabsScreen extends AbstractContainerScreen<TabsMenu> {
	private static final Identifier CONTAINER_BACKGROUND =
			Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	/** Height of the chest half of generic_54.png for six rows. */
	private static final int CHEST_TEXTURE_HEIGHT = TabsMenu.ROWS * 18 + 17;

	public TabsScreen(TabsMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 114 + TabsMenu.ROWS * 18);
		this.inventoryLabelY = this.imageHeight - 94;
		// The menu was opened with the authoritative count; the strip reads it from here.
		ClientTabState.setTabCount(menu.getTabCount());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, this.leftPos, this.topPos,
				0.0F, 0.0F, this.imageWidth, CHEST_TEXTURE_HEIGHT, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, this.leftPos, this.topPos + CHEST_TEXTURE_HEIGHT,
				0.0F, 126.0F, this.imageWidth, 96, 256, 256);

		TabStrip.render(graphics, this.font, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
				this.menu.getActiveTab(), mouseX, mouseY);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// The provider's title is fixed at open time; the tab number is not, so build it here.
		graphics.text(this.font, Component.translatable("gui.nbt-editor.tab_storage", this.menu.getActiveTab() + 1),
				this.titleLabelX, this.titleLabelY, -12566464, false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, -12566464, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int entry = TabStrip.entryAt(this.leftPos, this.topPos, this.imageWidth, this.imageHeight, event.x(), event.y());
		if (entry != TabStrip.ENTRY_NONE) {
			return TabStrip.click(entry, this.menu.getActiveTab());
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		// Clicking a tab must not count as "outside", or the carried stack gets thrown away.
		return super.hasClickedOutside(mouseX, mouseY, left, top)
				&& TabStrip.entryAt(this.leftPos, this.topPos, this.imageWidth, this.imageHeight, mouseX, mouseY) == TabStrip.ENTRY_NONE;
	}
}
