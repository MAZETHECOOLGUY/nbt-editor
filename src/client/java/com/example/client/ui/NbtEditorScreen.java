package com.example.client.ui;

import com.example.NbtEditor;
import com.example.network.EditItemPayload;
import com.example.util.ItemNbt;
import com.mojang.serialization.DataResult;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SpacerComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Polished, tabbed NBT & Data Components editor built with oωo-lib UI.
 * Features a Black and Orange theme, live 3D item preview, searchable enchantments
 * and attributes pickers, and a structured raw NBT hierarchy tree.
 */
public class NbtEditorScreen extends BaseUIModelScreen<FlowLayout> {

	private static final int T_BYTE = 1, T_SHORT = 2, T_INT = 3, T_LONG = 4,
			T_FLOAT = 5, T_DOUBLE = 6, T_STRING = 8, T_LIST = 9, T_COMPOUND = 10;
	private static final int[] TYPE_CYCLE = {T_BYTE, T_SHORT, T_INT, T_LONG, T_FLOAT, T_DOUBLE, T_STRING, T_LIST, T_COMPOUND};

	private static final ChatFormatting[] NAME_COLORS = {
			ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
			ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
			ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE
	};

	// ---- Black & Orange Theme Palette ----
	private static final int COLOR_PANEL_BG = 0xFF121216;
	private static final int COLOR_CARD_BG = 0xFF18181E;
	private static final int COLOR_INSET_BG = 0xFF0E0E12;
	private static final int COLOR_BORDER_SUBTLE = 0xFF2A2A34;
	private static final int COLOR_BORDER_ACTIVE = 0xFFFF7A00;
	private static final int COLOR_ORANGE_PRIMARY = 0xFFFF7A00;
	private static final int COLOR_ORANGE_HOVER = 0xFFFFA040;
	private static final int COLOR_ORANGE_DARK = 0xFFE65100;
	private static final int COLOR_TEXT_MUTED = 0xFFAAAAAA;

	private static final Surface CARD_SURFACE = Surface.flat(COLOR_CARD_BG).and(Surface.outline(COLOR_BORDER_SUBTLE));
	private static final Surface ACTIVE_CARD_SURFACE = Surface.flat(0xFF221A14).and(Surface.outline(COLOR_BORDER_ACTIVE));
	private static final Surface PANEL_SURFACE = Surface.flat(COLOR_PANEL_BG).and(Surface.outline(COLOR_BORDER_SUBTLE));
	private static final Surface INSET_SURFACE = Surface.flat(COLOR_INSET_BG).and(Surface.outline(COLOR_BORDER_SUBTLE));

	private static final ButtonComponent.Renderer BTN_STANDARD = ButtonComponent.Renderer.flat(0xFF202028, 0xFF30303C, 0xFF141418);
	private static final ButtonComponent.Renderer BTN_ORANGE = ButtonComponent.Renderer.flat(COLOR_ORANGE_DARK, COLOR_ORANGE_PRIMARY, 0xFF3D2010);
	private static final ButtonComponent.Renderer BTN_ACTIVE_TAB = ButtonComponent.Renderer.flat(COLOR_ORANGE_PRIMARY, COLOR_ORANGE_HOVER, COLOR_ORANGE_DARK);
	private static final ButtonComponent.Renderer BTN_INACTIVE_TAB = ButtonComponent.Renderer.flat(0xFF181820, 0xFF282834, 0xFF121216);

	public enum Tab { OVERVIEW, ENCHANTS, ATTRIBUTES, TREE }

	private final boolean playerInventory;
	private final int syncId;
	private final int slotIndex;
	private final CompoundTag root;
	private final RegistryAccess registries;
	private ItemStack currentStack;

	// Top & bottom widgets bound from nbt_editor.xml
	private ItemComponent headerItemIcon;
	private LabelComponent headerItemId;
	private ButtonComponent tabBtnOverview;
	private ButtonComponent tabBtnEnchants;
	private ButtonComponent tabBtnAttributes;
	private ButtonComponent tabBtnTree;
	private FlowLayout mainContent;
	private LabelComponent statusLabel;
	private ButtonComponent saveButton;
	private ButtonComponent cancelButton;

	// Live preview in Overview tab
	private ItemComponent overviewItemPreview;
	private LabelComponent overviewNamePreview;
	private LabelComponent overviewCountBadge;
	private LabelComponent overviewDurabilityBadge;
	private LabelComponent overviewEnchantsBadge;
	private LabelComponent overviewAttributesBadge;

	// Custom name formatting state
	private boolean fmtBold, fmtItalic, fmtUnderline, fmtStrike, fmtObfuscated;
	private ChatFormatting nameColor = null;

	// Enchantments state
	private List<Identifier> allEnchants = List.of();
	private String enchantSearchQuery = "";
	private boolean enchantOnlyActive = false;

	// Attributes state
	private List<Identifier> allAttributes = List.of();
	private String attributeSearchQuery = "";
	private boolean attributeOnlyActive = false;

	// Raw NBT Tree state
	private final Set<Tag> expandedTags = Collections.newSetFromMap(new IdentityHashMap<>());
	private Tag selectedTagParent = null;
	private String selectedTagKey = null;
	private int selectedTagIndex = -1;
	private FlowLayout treeInspectorContainer;

	private NbtEditorScreen(boolean playerInventory, int syncId, int slotIndex, CompoundTag root,
			RegistryAccess registries, ItemStack stack) {
		super(FlowLayout.class, DataSource.asset(Identifier.fromNamespaceAndPath("nbt-editor", "nbt_editor")));
		this.playerInventory = playerInventory;
		this.syncId = syncId;
		this.slotIndex = slotIndex;
		this.root = root;
		this.registries = registries;
		this.currentStack = stack.copy();
		this.expandedTags.add(root);
	}

	public static NbtEditorScreen open(boolean playerInventory, int syncId, int slotIndex, ItemStack stack,
			RegistryAccess registries) {
		CompoundTag tag = ItemNbt.encode(stack, registries);
		if (tag == null) {
			NbtEditor.LOGGER.warn("Failed to encode item to NBT; opening empty editor");
			tag = new CompoundTag();
		}
		return new NbtEditorScreen(playerInventory, syncId, slotIndex, tag, registries, stack);
	}

	@Override
	protected void build(FlowLayout rootComponent) {
		headerItemIcon = rootComponent.childById(ItemComponent.class, "header-item-icon");
		headerItemId = rootComponent.childById(LabelComponent.class, "header-item-id");
		tabBtnOverview = rootComponent.childById(ButtonComponent.class, "tab-btn-overview");
		tabBtnEnchants = rootComponent.childById(ButtonComponent.class, "tab-btn-enchants");
		tabBtnAttributes = rootComponent.childById(ButtonComponent.class, "tab-btn-attributes");
		tabBtnTree = rootComponent.childById(ButtonComponent.class, "tab-btn-tree");
		mainContent = rootComponent.childById(FlowLayout.class, "main-content");
		statusLabel = rootComponent.childById(LabelComponent.class, "status-label");
		saveButton = rootComponent.childById(ButtonComponent.class, "save-button");
		cancelButton = rootComponent.childById(ButtonComponent.class, "cancel-button");

		allEnchants = Presets.allEnchantmentIds(registries);
		allAttributes = Presets.allAttributeIds(registries);

		Style style = Presets.currentNameStyle(root, registries);
		fmtBold = style.isBold();
		fmtItalic = style.isItalic();
		fmtUnderline = style.isUnderlined();
		fmtStrike = style.isStrikethrough();
		fmtObfuscated = style.isObfuscated();
		nameColor = matchColor(style.getColor());

		tabBtnOverview.onPress(b -> switchTab(Tab.OVERVIEW));
		tabBtnEnchants.onPress(b -> switchTab(Tab.ENCHANTS));
		tabBtnAttributes.onPress(b -> switchTab(Tab.ATTRIBUTES));
		tabBtnTree.onPress(b -> switchTab(Tab.TREE));

		cancelButton.onPress(b -> onClose());
		saveButton.onPress(b -> save());

		updatePreview();
		switchTab(Tab.OVERVIEW);
		setStatus("Edits apply live — press Save Changes to keep.", false);
	}

	private void switchTab(Tab tab) {
		tabBtnOverview.renderer(tab == Tab.OVERVIEW ? BTN_ACTIVE_TAB : BTN_INACTIVE_TAB);
		tabBtnEnchants.renderer(tab == Tab.ENCHANTS ? BTN_ACTIVE_TAB : BTN_INACTIVE_TAB);
		tabBtnAttributes.renderer(tab == Tab.ATTRIBUTES ? BTN_ACTIVE_TAB : BTN_INACTIVE_TAB);
		tabBtnTree.renderer(tab == Tab.TREE ? BTN_ACTIVE_TAB : BTN_INACTIVE_TAB);

		mainContent.clearChildren();
		switch (tab) {
			case OVERVIEW -> mainContent.child(buildOverviewTab());
			case ENCHANTS -> mainContent.child(buildEnchantsTab());
			case ATTRIBUTES -> mainContent.child(buildAttributesTab());
			case TREE -> mainContent.child(buildTreeTab());
		}
	}

	// ==========================================
	// TAB 1: OVERVIEW & QUICK EDIT
	// ==========================================
	private FlowLayout buildOverviewTab() {
		FlowLayout container = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
		container.gap(8);

		// Left Card: 3D Item Showcase
		FlowLayout previewCard = UIContainers.verticalFlow(Sizing.fixed(160), Sizing.fill(100));
		previewCard.surface(CARD_SURFACE);
		previewCard.padding(Insets.of(12));
		previewCard.gap(6);
		previewCard.horizontalAlignment(HorizontalAlignment.CENTER);
		previewCard.verticalAlignment(VerticalAlignment.CENTER);

		LabelComponent previewHeader = UIComponents.label(Component.literal("Live Preview"));
		previewHeader.color(Color.ofRgb(COLOR_ORANGE_PRIMARY)).shadow(true);
		previewCard.child(previewHeader);

		overviewItemPreview = UIComponents.item(currentStack);
		overviewItemPreview.sizing(Sizing.fixed(48), Sizing.fixed(48));
		overviewItemPreview.showOverlay(true);
		overviewItemPreview.setTooltipFromStack(true);
		previewCard.child(overviewItemPreview);

		overviewNamePreview = UIComponents.label(currentStack.getHoverName());
		overviewNamePreview.shadow(true);
		previewCard.child(overviewNamePreview);

		overviewCountBadge = UIComponents.label(Component.literal("Count: " + Presets.currentCount(root)));
		overviewCountBadge.color(Color.ofRgb(COLOR_TEXT_MUTED));
		previewCard.child(overviewCountBadge);

		overviewDurabilityBadge = UIComponents.label(Component.literal(
				Presets.hasComponent(root, DataComponents.UNBREAKABLE) ? "✦ Unbreakable" : "Durability: Standard"));
		overviewDurabilityBadge.color(Color.ofRgb(Presets.hasComponent(root, DataComponents.UNBREAKABLE) ? COLOR_ORANGE_HOVER : COLOR_TEXT_MUTED));
		previewCard.child(overviewDurabilityBadge);

		int enchCount = Presets.currentEnchantments(root, registries).size();
		overviewEnchantsBadge = UIComponents.label(Component.literal(enchCount + " Enchantments"));
		overviewEnchantsBadge.color(Color.ofRgb(enchCount > 0 ? COLOR_ORANGE_HOVER : COLOR_TEXT_MUTED));
		previewCard.child(overviewEnchantsBadge);

		int attrCount = Presets.currentAttributes(root, registries).size();
		overviewAttributesBadge = UIComponents.label(Component.literal(attrCount + " Modifiers"));
		overviewAttributesBadge.color(Color.ofRgb(attrCount > 0 ? COLOR_ORANGE_HOVER : COLOR_TEXT_MUTED));
		previewCard.child(overviewAttributesBadge);

		container.child(previewCard);

		// Right Card: Controls
		FlowLayout controlsLayout = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		controlsLayout.padding(Insets.of(10));
		controlsLayout.gap(8);

		// --- Section: Custom Name ---
		FlowLayout nameSection = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		nameSection.surface(INSET_SURFACE);
		nameSection.padding(Insets.of(8));
		nameSection.gap(6);

		LabelComponent nameLabel = UIComponents.label(Component.literal("Custom Name"));
		nameLabel.color(Color.ofRgb(COLOR_ORANGE_PRIMARY)).shadow(true);
		nameSection.child(nameLabel);

		TextBoxComponent nameBox = UIComponents.textBox(Sizing.fill(100));
		nameBox.setMaxLength(256);
		nameBox.text(Presets.currentName(root, registries));
		nameSection.child(nameBox);

		// Format toggles
		FlowLayout formatBar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
		formatBar.gap(4);
		formatBar.verticalAlignment(VerticalAlignment.CENTER);

		ButtonComponent btnB = createFormatToggle("B", fmtBold, () -> { fmtBold = !fmtBold; applyRename(nameBox.getValue()); });
		ButtonComponent btnI = createFormatToggle("I", fmtItalic, () -> { fmtItalic = !fmtItalic; applyRename(nameBox.getValue()); });
		ButtonComponent btnU = createFormatToggle("U", fmtUnderline, () -> { fmtUnderline = !fmtUnderline; applyRename(nameBox.getValue()); });
		ButtonComponent btnS = createFormatToggle("S", fmtStrike, () -> { fmtStrike = !fmtStrike; applyRename(nameBox.getValue()); });
		ButtonComponent btnK = createFormatToggle("K", fmtObfuscated, () -> { fmtObfuscated = !fmtObfuscated; applyRename(nameBox.getValue()); });

		formatBar.child(btnB).child(btnI).child(btnU).child(btnS).child(btnK);
		nameSection.child(formatBar);

		// Swatches
		FlowLayout swatchesRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		swatchesRow.gap(3);
		swatchesRow.verticalAlignment(VerticalAlignment.CENTER);

		// Clear color swatch
		ButtonComponent clearSwatch = UIComponents.button(Component.literal("×"), b -> {
			nameColor = null;
			applyRename(nameBox.getValue());
		});
		clearSwatch.sizing(Sizing.fixed(16), Sizing.fixed(16));
		clearSwatch.renderer(BTN_STANDARD);
		clearSwatch.tooltip(Component.literal("Default Color"));
		swatchesRow.child(clearSwatch);

		for (ChatFormatting cf : NAME_COLORS) {
			Integer rgb = cf.getColor();
			int col = rgb != null ? rgb : 0xFFFFFF;
			BoxComponent swatch = UIComponents.box(Sizing.fixed(16), Sizing.fixed(16));
			swatch.fill(true);
			swatch.color(Color.ofRgb(col));
			swatch.tooltip(Component.literal(cf.getName()).withStyle(cf));
			swatch.cursorStyle(CursorStyle.HAND);
			swatch.mouseDown().subscribe((event, doubleClick) -> {
				nameColor = cf;
				applyRename(nameBox.getValue());
				return true;
			});
			swatchesRow.child(swatch);
		}
		nameSection.child(swatchesRow);

		nameBox.onChanged().subscribe(val -> applyRename(val));
		controlsLayout.child(nameSection);

		// --- Section: Item Count ---
		FlowLayout countSection = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		countSection.surface(INSET_SURFACE);
		countSection.padding(Insets.of(8));
		countSection.gap(6);

		LabelComponent countLabel = UIComponents.label(Component.literal("Item Count"));
		countLabel.color(Color.ofRgb(COLOR_ORANGE_PRIMARY)).shadow(true);
		countSection.child(countLabel);

		FlowLayout countControls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
		countControls.gap(4);
		countControls.verticalAlignment(VerticalAlignment.CENTER);

		TextBoxComponent countBox = UIComponents.textBox(Sizing.fixed(50));
		countBox.text(Integer.toString(Presets.currentCount(root)));

		ButtonComponent btnDec = UIComponents.button(Component.literal("-"), b -> {
			int c = Math.max(1, Presets.currentCount(root) - 1);
			Presets.setCount(root, c);
			countBox.text(Integer.toString(c));
			updatePreview();
			setStatus("Count set to " + c, false);
		});
		btnDec.sizing(Sizing.fixed(20), Sizing.fixed(20));
		btnDec.renderer(BTN_STANDARD);

		ButtonComponent btnInc = UIComponents.button(Component.literal("+"), b -> {
			int c = Presets.currentCount(root) + 1;
			Presets.setCount(root, c);
			countBox.text(Integer.toString(c));
			updatePreview();
			setStatus("Count set to " + c, false);
		});
		btnInc.sizing(Sizing.fixed(20), Sizing.fixed(20));
		btnInc.renderer(BTN_STANDARD);

		countControls.child(btnDec).child(countBox).child(btnInc);

		int[] presets = {1, 16, 64, 99};
		for (int p : presets) {
			ButtonComponent pBtn = UIComponents.button(Component.literal(Integer.toString(p)), b -> {
				Presets.setCount(root, p);
				countBox.text(Integer.toString(p));
				updatePreview();
				setStatus("Count set to " + p, false);
			});
			pBtn.sizing(Sizing.fixed(32), Sizing.fixed(20));
			pBtn.renderer(BTN_STANDARD);
			countControls.child(pBtn);
		}

		countBox.onChanged().subscribe(val -> {
			try {
				int c = Math.max(1, Integer.parseInt(val.trim()));
				Presets.setCount(root, c);
				updatePreview();
			} catch (NumberFormatException ignored) {}
		});
		countSection.child(countControls);
		controlsLayout.child(countSection);

		// --- Section: Unbreakable ---
		FlowLayout propSection = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		propSection.surface(INSET_SURFACE);
		propSection.padding(Insets.of(8));
		propSection.gap(6);

		CheckboxComponent unbreakBox = UIComponents.checkbox(Component.literal("Unbreakable (Ignore durability damage)"));
		unbreakBox.checked(Presets.hasComponent(root, DataComponents.UNBREAKABLE));
		unbreakBox.onChanged(checked -> {
			Presets.toggleUnbreakable(root, registries);
			updatePreview();
			setStatus(Presets.hasComponent(root, DataComponents.UNBREAKABLE) ? "Unbreakable enabled." : "Unbreakable disabled.", false);
		});
		propSection.child(unbreakBox);
		controlsLayout.child(propSection);

		ScrollContainer<FlowLayout> controlsScroll = UIContainers.verticalScroll(Sizing.expand(100), Sizing.fill(100), controlsLayout);
		controlsScroll.surface(CARD_SURFACE);
		container.child(controlsScroll);

		return container;
	}

	private ButtonComponent createFormatToggle(String label, boolean initial, Runnable action) {
		ButtonComponent btn = UIComponents.button(Component.literal(label), null);
		btn.sizing(Sizing.fixed(20), Sizing.fixed(20));
		btn.renderer(initial ? BTN_ORANGE : BTN_STANDARD);
		btn.onPress(b -> {
			action.run();
			boolean current = switch (label) {
				case "B" -> fmtBold;
				case "I" -> fmtItalic;
				case "U" -> fmtUnderline;
				case "S" -> fmtStrike;
				case "K" -> fmtObfuscated;
				default -> false;
			};
			b.renderer(current ? BTN_ORANGE : BTN_STANDARD);
		});
		return btn;
	}

	private void applyRename(String text) {
		Presets.renameStyled(root, registries, text, fmtBold, fmtItalic, fmtUnderline, fmtStrike, fmtObfuscated, nameColor);
		updatePreview();
		if (overviewNamePreview != null) {
			overviewNamePreview.text(currentStack.getHoverName());
		}
	}

	// ==========================================
	// TAB 2: ENCHANTMENTS
	// ==========================================
	private FlowLayout buildEnchantsTab() {
		FlowLayout container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
		container.gap(6);

		// Search toolbar
		FlowLayout toolbar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
		toolbar.surface(PANEL_SURFACE);
		toolbar.padding(Insets.both(6, 3));
		toolbar.gap(8);
		toolbar.verticalAlignment(VerticalAlignment.CENTER);

		TextBoxComponent searchBox = UIComponents.textBox(Sizing.fixed(180));
		searchBox.setMaxLength(64);
		searchBox.setHint(Component.literal("Search enchantments..."));
		searchBox.text(enchantSearchQuery);
		toolbar.child(searchBox);

		CheckboxComponent activeOnly = UIComponents.checkbox(Component.literal("Active Only"));
		activeOnly.checked(enchantOnlyActive);
		toolbar.child(activeOnly);

		SpacerComponent sp = UIComponents.spacer();
		toolbar.child(sp);

		ButtonComponent clearAll = UIComponents.button(Component.literal("Remove All"), b -> {
			Map<String, Integer> current = Presets.currentEnchantments(root, registries);
			for (String id : current.keySet()) {
				Presets.setEnchantment(root, registries, id, 0);
			}
			updatePreview();
			switchTab(Tab.ENCHANTS);
			setStatus("Removed all enchantments.", false);
		});
		clearAll.sizing(Sizing.fixed(80), Sizing.fixed(20));
		clearAll.renderer(BTN_STANDARD);
		toolbar.child(clearAll);

		container.child(toolbar);

		// List container
		FlowLayout listLayout = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		listLayout.gap(4);
		listLayout.padding(Insets.of(4));

		populateEnchantsList(listLayout);

		searchBox.onChanged().subscribe(val -> {
			enchantSearchQuery = val.trim().toLowerCase(Locale.ROOT);
			populateEnchantsList(listLayout);
		});

		activeOnly.onChanged(checked -> {
			enchantOnlyActive = checked;
			populateEnchantsList(listLayout);
		});

		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), listLayout);
		scroll.surface(CARD_SURFACE);
		container.child(scroll);

		return container;
	}

	private void populateEnchantsList(FlowLayout listLayout) {
		listLayout.clearChildren();
		Map<String, Integer> currentEnchants = Presets.currentEnchantments(root, registries);

		for (Identifier id : allEnchants) {
			String idStr = id.toString();
			boolean active = currentEnchants.containsKey(idStr);
			if (enchantOnlyActive && !active) {
				continue;
			}
			if (!enchantSearchQuery.isEmpty() && !idStr.toLowerCase(Locale.ROOT).contains(enchantSearchQuery)) {
				continue;
			}

			int currentLevel = currentEnchants.getOrDefault(idStr, 0);

			FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(28));
			row.surface(active ? ACTIVE_CARD_SURFACE : CARD_SURFACE);
			row.padding(Insets.both(8, 4));
			row.gap(6);
			row.verticalAlignment(VerticalAlignment.CENTER);

			LabelComponent nameLabel = UIComponents.label(Component.literal(idStr));
			nameLabel.color(Color.ofRgb(active ? COLOR_ORANGE_HOVER : 0xEEEEEE));
			row.child(nameLabel);

			if (active) {
				LabelComponent lvlBadge = UIComponents.label(Component.literal(" [Lvl " + currentLevel + "]"));
				lvlBadge.color(Color.ofRgb(COLOR_ORANGE_PRIMARY)).shadow(true);
				row.child(lvlBadge);
			}

			row.child(UIComponents.spacer());

			TextBoxComponent lvlBox = UIComponents.textBox(Sizing.fixed(32));
			lvlBox.text(Integer.toString(Math.max(1, currentLevel)));

			ButtonComponent decBtn = UIComponents.button(Component.literal("-"), b -> {
				int next = currentLevel - 1;
				Presets.setEnchantment(root, registries, idStr, Math.max(0, next));
				updatePreview();
				populateEnchantsList(listLayout);
				setStatus(next <= 0 ? "Removed " + idStr : "Set " + idStr + " to " + next, false);
			});
			decBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));
			decBtn.renderer(BTN_STANDARD);

			ButtonComponent incBtn = UIComponents.button(Component.literal("+"), b -> {
				int next = currentLevel <= 0 ? 1 : currentLevel + 1;
				Presets.setEnchantment(root, registries, idStr, next);
				updatePreview();
				populateEnchantsList(listLayout);
				setStatus("Set " + idStr + " to " + next, false);
			});
			incBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));
			incBtn.renderer(BTN_STANDARD);

			ButtonComponent setBtn = UIComponents.button(Component.literal(active ? "Update" : "Add"), b -> {
				int lvl = 1;
				try {
					lvl = Math.max(1, Integer.parseInt(lvlBox.getValue().trim()));
				} catch (NumberFormatException ignored) {}
				Presets.setEnchantment(root, registries, idStr, lvl);
				updatePreview();
				populateEnchantsList(listLayout);
				setStatus("Set " + idStr + " to " + lvl, false);
			});
			setBtn.sizing(Sizing.fixed(45), Sizing.fixed(20));
			setBtn.renderer(active ? BTN_ORANGE : BTN_STANDARD);

			ButtonComponent removeBtn = UIComponents.button(Component.literal("×"), b -> {
				Presets.setEnchantment(root, registries, idStr, 0);
				updatePreview();
				populateEnchantsList(listLayout);
				setStatus("Removed " + idStr, false);
			});
			removeBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));
			removeBtn.renderer(BTN_STANDARD);
			removeBtn.active(active);

			row.child(decBtn).child(lvlBox).child(incBtn).child(setBtn).child(removeBtn);
			listLayout.child(row);
		}
	}

	// ==========================================
	// TAB 3: ATTRIBUTES
	// ==========================================
	private FlowLayout buildAttributesTab() {
		FlowLayout container = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
		container.gap(6);

		// Toolbar
		FlowLayout toolbar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
		toolbar.surface(PANEL_SURFACE);
		toolbar.padding(Insets.both(6, 3));
		toolbar.gap(8);
		toolbar.verticalAlignment(VerticalAlignment.CENTER);

		TextBoxComponent searchBox = UIComponents.textBox(Sizing.fixed(180));
		searchBox.setMaxLength(64);
		searchBox.setHint(Component.literal("Search attributes..."));
		searchBox.text(attributeSearchQuery);
		toolbar.child(searchBox);

		CheckboxComponent activeOnly = UIComponents.checkbox(Component.literal("Active Only"));
		activeOnly.checked(attributeOnlyActive);
		toolbar.child(activeOnly);

		toolbar.child(UIComponents.spacer());

		ButtonComponent clearAll = UIComponents.button(Component.literal("Remove All"), b -> {
			Map<String, String> current = Presets.currentAttributes(root, registries);
			for (String id : current.keySet()) {
				Presets.removeAttribute(root, registries, id);
			}
			updatePreview();
			switchTab(Tab.ATTRIBUTES);
			setStatus("Removed all attribute modifiers.", false);
		});
		clearAll.sizing(Sizing.fixed(80), Sizing.fixed(20));
		clearAll.renderer(BTN_STANDARD);
		toolbar.child(clearAll);

		container.child(toolbar);

		// List container
		FlowLayout listLayout = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		listLayout.gap(4);
		listLayout.padding(Insets.of(4));

		populateAttributesList(listLayout);

		searchBox.onChanged().subscribe(val -> {
			attributeSearchQuery = val.trim().toLowerCase(Locale.ROOT);
			populateAttributesList(listLayout);
		});

		activeOnly.onChanged(checked -> {
			attributeOnlyActive = checked;
			populateAttributesList(listLayout);
		});

		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), listLayout);
		scroll.surface(CARD_SURFACE);
		container.child(scroll);

		return container;
	}

	private void populateAttributesList(FlowLayout listLayout) {
		listLayout.clearChildren();
		Map<String, String> currentAttrs = Presets.currentAttributes(root, registries);

		for (Identifier id : allAttributes) {
			String idStr = id.toString();
			boolean active = currentAttrs.containsKey(idStr);
			if (attributeOnlyActive && !active) {
				continue;
			}
			if (!attributeSearchQuery.isEmpty() && !idStr.toLowerCase(Locale.ROOT).contains(attributeSearchQuery)) {
				continue;
			}

			String summary = currentAttrs.get(idStr);

			FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(28));
			row.surface(active ? ACTIVE_CARD_SURFACE : CARD_SURFACE);
			row.padding(Insets.both(8, 4));
			row.gap(6);
			row.verticalAlignment(VerticalAlignment.CENTER);

			LabelComponent nameLabel = UIComponents.label(Component.literal(idStr));
			nameLabel.color(Color.ofRgb(active ? COLOR_ORANGE_HOVER : 0xEEEEEE));
			row.child(nameLabel);

			if (summary != null) {
				LabelComponent summaryBadge = UIComponents.label(Component.literal(" [" + summary + "]"));
				summaryBadge.color(Color.ofRgb(COLOR_ORANGE_PRIMARY)).shadow(true);
				row.child(summaryBadge);
			}

			row.child(UIComponents.spacer());

			TextBoxComponent amountBox = UIComponents.textBox(Sizing.fixed(40));
			Optional<Presets.AttributeState> state = Presets.currentAttribute(root, registries, idStr);
			amountBox.text(state.map(s -> Presets.formatAmount(s.amount()).replace("+", "")).orElse("1.0"));

			final int[] localSlot = {state.map(s -> Presets.SLOT_GROUPS.indexOf(s.slot())).orElse(0)};
			final int[] localOp = {state.map(s -> Presets.OPERATIONS.indexOf(s.operation())).orElse(0)};
			if (localSlot[0] < 0) localSlot[0] = 0;
			if (localOp[0] < 0) localOp[0] = 0;

			ButtonComponent slotBtn = UIComponents.button(Component.literal(Presets.slotLabel(Presets.SLOT_GROUPS.get(localSlot[0]))), null);
			slotBtn.sizing(Sizing.fixed(60), Sizing.fixed(20));
			slotBtn.renderer(BTN_STANDARD);
			slotBtn.onPress(b -> {
				localSlot[0] = (localSlot[0] + 1) % Presets.SLOT_GROUPS.size();
				b.setMessage(Component.literal(Presets.slotLabel(Presets.SLOT_GROUPS.get(localSlot[0]))));
			});

			ButtonComponent opBtn = UIComponents.button(Component.literal(Presets.operationLabel(Presets.OPERATIONS.get(localOp[0]))), null);
			opBtn.sizing(Sizing.fixed(50), Sizing.fixed(20));
			opBtn.renderer(BTN_STANDARD);
			opBtn.onPress(b -> {
				localOp[0] = (localOp[0] + 1) % Presets.OPERATIONS.size();
				b.setMessage(Component.literal(Presets.operationLabel(Presets.OPERATIONS.get(localOp[0]))));
			});

			ButtonComponent applyBtn = UIComponents.button(Component.literal(active ? "Update" : "Add"), b -> {
				double amount = 1.0;
				try {
					amount = Double.parseDouble(amountBox.getValue().trim());
				} catch (NumberFormatException ignored) {}
				Presets.setAttribute(root, registries, idStr, amount, Presets.OPERATIONS.get(localOp[0]), Presets.SLOT_GROUPS.get(localSlot[0]));
				updatePreview();
				populateAttributesList(listLayout);
				setStatus("Set modifier for " + idStr, false);
			});
			applyBtn.sizing(Sizing.fixed(45), Sizing.fixed(20));
			applyBtn.renderer(active ? BTN_ORANGE : BTN_STANDARD);

			ButtonComponent removeBtn = UIComponents.button(Component.literal("×"), b -> {
				Presets.removeAttribute(root, registries, idStr);
				updatePreview();
				populateAttributesList(listLayout);
				setStatus("Removed modifier for " + idStr, false);
			});
			removeBtn.sizing(Sizing.fixed(20), Sizing.fixed(20));
			removeBtn.renderer(BTN_STANDARD);
			removeBtn.active(active);

			row.child(amountBox).child(opBtn).child(slotBtn).child(applyBtn).child(removeBtn);
			listLayout.child(row);
		}
	}

	// ==========================================
	// TAB 4: RAW NBT TREE EXPLORER
	// ==========================================
	private FlowLayout buildTreeTab() {
		FlowLayout container = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
		container.gap(8);

		// Left Pane: Tree View
		FlowLayout treeCard = UIContainers.verticalFlow(Sizing.expand(62), Sizing.fill(100));
		treeCard.surface(CARD_SURFACE);
		treeCard.padding(Insets.of(6));
		treeCard.gap(4);

		FlowLayout treeHeader = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
		treeHeader.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent treeTitle = UIComponents.label(Component.literal("Tag Hierarchy"));
		treeTitle.color(Color.ofRgb(COLOR_ORANGE_PRIMARY)).shadow(true);
		treeHeader.child(treeTitle);
		treeHeader.child(UIComponents.spacer());

		ButtonComponent btnAddRoot = UIComponents.button(Component.literal("+ Add Key"), b -> {
			String key = uniqueKey(root);
			root.putString(key, "");
			selectedTagParent = root;
			selectedTagKey = key;
			selectedTagIndex = -1;
			updatePreview();
			switchTab(Tab.TREE);
		});
		btnAddRoot.sizing(Sizing.fixed(65), Sizing.fixed(18));
		btnAddRoot.renderer(BTN_STANDARD);
		treeHeader.child(btnAddRoot);

		treeCard.child(treeHeader);

		FlowLayout treeList = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		treeList.gap(2);
		populateTreeRows(treeList, root, null, null, -1, 0);

		ScrollContainer<FlowLayout> treeScroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), treeList);
		treeCard.child(treeScroll);
		container.child(treeCard);

		// Right Pane: Node Inspector
		treeInspectorContainer = UIContainers.verticalFlow(Sizing.expand(38), Sizing.fill(100));
		treeInspectorContainer.surface(PANEL_SURFACE);
		treeInspectorContainer.padding(Insets.of(10));
		treeInspectorContainer.gap(8);
		refreshInspector();
		container.child(treeInspectorContainer);

		return container;
	}

	private void populateTreeRows(FlowLayout treeList, Tag tag, Tag parent, String key, int index, int depth) {
		boolean isContainer = tag instanceof CompoundTag || tag instanceof ListTag;
		boolean isSelected = isSelectedTag(parent, key, index);

		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
		row.surface(isSelected ? ACTIVE_CARD_SURFACE : Surface.BLANK);
		row.padding(Insets.both(depth * 10 + 4, 2));
		row.gap(4);
		row.verticalAlignment(VerticalAlignment.CENTER);

		// Expand/collapse arrow
		if (isContainer) {
			boolean isExpanded = expandedTags.contains(tag);
			LabelComponent arrow = UIComponents.label(Component.literal(isExpanded ? "▾" : "▸"));
			arrow.color(Color.ofRgb(COLOR_ORANGE_PRIMARY));
			arrow.cursorStyle(CursorStyle.HAND);
			arrow.mouseDown().subscribe((e, dbl) -> {
				if (expandedTags.contains(tag)) {
					expandedTags.remove(tag);
				} else {
					expandedTags.add(tag);
				}
				switchTab(Tab.TREE);
				return true;
			});
			row.child(arrow);
		} else {
			row.child(UIComponents.label(Component.literal("  ")));
		}

		// Type badge
		LabelComponent typeBadge = UIComponents.label(Component.literal("[" + typeBadgeName(tag.getId()) + "]"));
		typeBadge.color(Color.ofRgb(typeBadgeColor(tag.getId())));
		row.child(typeBadge);

		// Key or index label
		String name = key != null ? key : "[" + index + "]";
		LabelComponent nameLabel = UIComponents.label(Component.literal(name));
		nameLabel.color(Color.ofRgb(isSelected ? COLOR_ORANGE_HOVER : 0xFFFFFF));
		row.child(nameLabel);

		// Value preview
		if (!isContainer) {
			String valStr = leafText(tag);
			if (valStr.length() > 24) {
				valStr = valStr.substring(0, 21) + "...";
			}
			LabelComponent valLabel = UIComponents.label(Component.literal(": " + valStr));
			valLabel.color(Color.ofRgb(COLOR_TEXT_MUTED));
			row.child(valLabel);
		} else {
			int sz = (tag instanceof CompoundTag c) ? c.size() : ((ListTag) tag).size();
			LabelComponent szLabel = UIComponents.label(Component.literal(" (" + sz + ")"));
			szLabel.color(Color.ofRgb(COLOR_TEXT_MUTED));
			row.child(szLabel);
		}

		row.cursorStyle(CursorStyle.HAND);
		row.mouseDown().subscribe((e, dbl) -> {
			selectedTagParent = parent;
			selectedTagKey = key;
			selectedTagIndex = index;
			if (dbl && isContainer) {
				if (expandedTags.contains(tag)) {
					expandedTags.remove(tag);
				} else {
					expandedTags.add(tag);
				}
			}
			switchTab(Tab.TREE);
			return true;
		});

		treeList.child(row);

		// Recursively add children if expanded
		if (isContainer && expandedTags.contains(tag)) {
			if (tag instanceof CompoundTag comp) {
				List<String> keys = new ArrayList<>(comp.keySet());
				Collections.sort(keys);
				for (String k : keys) {
					populateTreeRows(treeList, comp.get(k), comp, k, -1, depth + 1);
				}
			} else if (tag instanceof ListTag list) {
				for (int i = 0; i < list.size(); i++) {
					populateTreeRows(treeList, list.get(i), list, null, i, depth + 1);
				}
			}
		}
	}

	private void refreshInspector() {
		if (treeInspectorContainer == null) {
			return;
		}
		treeInspectorContainer.clearChildren();

		LabelComponent title = UIComponents.label(Component.literal("Tag Inspector"));
		title.color(Color.ofRgb(COLOR_ORANGE_PRIMARY)).shadow(true);
		treeInspectorContainer.child(title);

		Tag selected = getSelectedTag();
		if (selected == null) {
			LabelComponent hint = UIComponents.label(Component.literal("Click any node in the tree to inspect and edit its properties."));
			hint.color(Color.ofRgb(COLOR_TEXT_MUTED));
			treeInspectorContainer.child(hint);
			return;
		}

		// Selected Path info
		String desc = selectedTagKey != null ? "Key: " + selectedTagKey : "Index: [" + selectedTagIndex + "]";
		LabelComponent pathLabel = UIComponents.label(Component.literal(desc + " (" + typeName(selected.getId()) + ")"));
		pathLabel.color(Color.ofRgb(COLOR_ORANGE_HOVER));
		treeInspectorContainer.child(pathLabel);

		// Key rename (if in compound)
		if (selectedTagParent instanceof CompoundTag parentComp && selectedTagKey != null) {
			LabelComponent keyHeader = UIComponents.label(Component.literal("Key Name"));
			keyHeader.color(Color.ofRgb(COLOR_TEXT_MUTED));
			treeInspectorContainer.child(keyHeader);

			TextBoxComponent keyBox = UIComponents.textBox(Sizing.fill(100));
			keyBox.text(selectedTagKey);
			keyBox.onChanged().subscribe(newKey -> {
				newKey = newKey.trim();
				if (!newKey.isEmpty() && !newKey.equals(selectedTagKey) && !parentComp.contains(newKey)) {
					parentComp.remove(selectedTagKey);
					parentComp.put(newKey, selected);
					selectedTagKey = newKey;
					updatePreview();
				}
			});
			treeInspectorContainer.child(keyBox);
		}

		// Scalar value editor
		if (selected instanceof NumericTag || selected instanceof StringTag) {
			LabelComponent valHeader = UIComponents.label(Component.literal("Tag Value"));
			valHeader.color(Color.ofRgb(COLOR_TEXT_MUTED));
			treeInspectorContainer.child(valHeader);

			TextBoxComponent valBox = UIComponents.textBox(Sizing.fill(100));
			valBox.text(leafText(selected));
			valBox.onChanged().subscribe(newVal -> {
				replaceSelectedTag(createTag(selected.getId(), newVal));
				updatePreview();
			});
			treeInspectorContainer.child(valBox);
		}

		// Tag type changer
		LabelComponent typeHeader = UIComponents.label(Component.literal("Tag Type: " + typeName(selected.getId())));
		typeHeader.color(Color.ofRgb(COLOR_TEXT_MUTED));
		treeInspectorContainer.child(typeHeader);

		ButtonComponent cycleTypeBtn = UIComponents.button(Component.literal("Change Type >"), b -> {
			int cur = selected.getId();
			int idx = 0;
			for (int j = 0; j < TYPE_CYCLE.length; j++) {
				if (TYPE_CYCLE[j] == cur) {
					idx = j;
					break;
				}
			}
			int next = TYPE_CYCLE[(idx + 1) % TYPE_CYCLE.length];
			replaceSelectedTag(createTag(next, leafText(selected)));
			updatePreview();
			switchTab(Tab.TREE);
		});
		cycleTypeBtn.sizing(Sizing.fill(100), Sizing.fixed(20));
		cycleTypeBtn.renderer(BTN_STANDARD);
		treeInspectorContainer.child(cycleTypeBtn);

		// Actions: Add Child & Delete
		FlowLayout actionsRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
		actionsRow.gap(4);

		if (selected instanceof CompoundTag || selected instanceof ListTag) {
			ButtonComponent addChildBtn = UIComponents.button(Component.literal("+ Add Child"), b -> {
				if (selected instanceof CompoundTag c) {
					String newK = uniqueKey(c);
					c.putString(newK, "");
					expandedTags.add(c);
					selectedTagParent = c;
					selectedTagKey = newK;
					selectedTagIndex = -1;
				} else if (selected instanceof ListTag l) {
					l.add(l.size(), StringTag.valueOf(""));
					expandedTags.add(l);
					selectedTagParent = l;
					selectedTagKey = null;
					selectedTagIndex = l.size() - 1;
				}
				updatePreview();
				switchTab(Tab.TREE);
			});
			addChildBtn.sizing(Sizing.expand(50), Sizing.fixed(20));
			addChildBtn.renderer(BTN_STANDARD);
			actionsRow.child(addChildBtn);
		}

		ButtonComponent deleteBtn = UIComponents.button(Component.literal("Delete"), b -> {
			if (selectedTagParent instanceof CompoundTag c && selectedTagKey != null) {
				c.remove(selectedTagKey);
			} else if (selectedTagParent instanceof ListTag l && selectedTagIndex >= 0 && selectedTagIndex < l.size()) {
				l.remove(selectedTagIndex);
			}
			selectedTagParent = null;
			selectedTagKey = null;
			selectedTagIndex = -1;
			updatePreview();
			switchTab(Tab.TREE);
		});
		deleteBtn.sizing(Sizing.expand(50), Sizing.fixed(20));
		deleteBtn.renderer(ButtonComponent.Renderer.flat(0xFF6B1A1A, 0xFFA02020, 0xFF301010));
		actionsRow.child(deleteBtn);

		treeInspectorContainer.child(actionsRow);
	}

	private boolean isSelectedTag(Tag parent, String key, int index) {
		if (parent != selectedTagParent) {
			return false;
		}
		return selectedTagKey != null ? selectedTagKey.equals(key) : selectedTagIndex == index;
	}

	private Tag getSelectedTag() {
		if (selectedTagParent instanceof CompoundTag c && selectedTagKey != null) {
			return c.get(selectedTagKey);
		}
		if (selectedTagParent instanceof ListTag l && selectedTagIndex >= 0 && selectedTagIndex < l.size()) {
			return l.get(selectedTagIndex);
		}
		return null;
	}

	private void replaceSelectedTag(Tag newTag) {
		if (selectedTagParent instanceof CompoundTag c && selectedTagKey != null) {
			c.put(selectedTagKey, newTag);
		} else if (selectedTagParent instanceof ListTag l && selectedTagIndex >= 0) {
			l.setTag(selectedTagIndex, newTag);
		}
	}

	// ==========================================
	// HELPERS & LIFECYCLE
	// ==========================================
	private void updatePreview() {
		DataResult<ItemStack> decoded = ItemNbt.decode(root, registries);
		currentStack = decoded.result().orElse(currentStack);
		if (headerItemIcon != null) {
			headerItemIcon.stack(currentStack);
		}
		if (headerItemId != null) {
			String itemId = root.getStringOr("id", "minecraft:air");
			int count = Presets.currentCount(root);
			headerItemId.text(Component.literal(itemId + " ×" + count).withStyle(s -> s.withColor(TextColor.fromRgb(COLOR_ORANGE_HOVER))));
		}
		if (overviewItemPreview != null) {
			overviewItemPreview.stack(currentStack);
		}
		if (overviewNamePreview != null) {
			overviewNamePreview.text(currentStack.getHoverName());
		}
		if (overviewCountBadge != null) {
			overviewCountBadge.text(Component.literal("Count: " + Presets.currentCount(root)));
		}
		if (overviewDurabilityBadge != null) {
			boolean unb = Presets.hasComponent(root, DataComponents.UNBREAKABLE);
			overviewDurabilityBadge.text(Component.literal(unb ? "✦ Unbreakable" : "Durability: Standard"));
			overviewDurabilityBadge.color(Color.ofRgb(unb ? COLOR_ORANGE_HOVER : COLOR_TEXT_MUTED));
		}
		if (overviewEnchantsBadge != null) {
			int sz = Presets.currentEnchantments(root, registries).size();
			overviewEnchantsBadge.text(Component.literal(sz + " Enchantments"));
			overviewEnchantsBadge.color(Color.ofRgb(sz > 0 ? COLOR_ORANGE_HOVER : COLOR_TEXT_MUTED));
		}
		if (overviewAttributesBadge != null) {
			int sz = Presets.currentAttributes(root, registries).size();
			overviewAttributesBadge.text(Component.literal(sz + " Modifiers"));
			overviewAttributesBadge.color(Color.ofRgb(sz > 0 ? COLOR_ORANGE_HOVER : COLOR_TEXT_MUTED));
		}
	}

	private void setStatus(String message, boolean error) {
		if (statusLabel != null) {
			statusLabel.text(Component.literal(message));
			statusLabel.color(Color.ofRgb(error ? 0xFFFF4444 : COLOR_ORANGE_HOVER));
		}
	}

	private void save() {
		ClientPlayNetworking.send(new EditItemPayload(playerInventory, syncId, slotIndex, root));
		onClose();
	}

	private static ChatFormatting matchColor(TextColor color) {
		if (color == null) {
			return null;
		}
		for (ChatFormatting cf : NAME_COLORS) {
			Integer rgb = cf.getColor();
			if (rgb != null && rgb == color.getValue()) {
				return cf;
			}
		}
		return null;
	}

	private static String uniqueKey(CompoundTag c) {
		if (!c.contains("new_tag")) {
			return "new_tag";
		}
		for (int i = 1; ; i++) {
			String k = "new_tag" + i;
			if (!c.contains(k)) {
				return k;
			}
		}
	}

	private static Tag createTag(int typeId, String text) {
		return switch (typeId) {
			case T_BYTE -> ByteTag.valueOf((byte) parseLong(text));
			case T_SHORT -> ShortTag.valueOf((short) parseLong(text));
			case T_INT -> IntTag.valueOf((int) parseLong(text));
			case T_LONG -> LongTag.valueOf(parseLong(text));
			case T_FLOAT -> FloatTag.valueOf((float) parseDouble(text));
			case T_DOUBLE -> DoubleTag.valueOf(parseDouble(text));
			case T_LIST -> new ListTag();
			case T_COMPOUND -> new CompoundTag();
			default -> StringTag.valueOf(text);
		};
	}

	private static String leafText(Tag t) {
		if (t instanceof StringTag) {
			return t.asString().orElse("");
		}
		if (t instanceof NumericTag n) {
			return switch (t.getId()) {
				case T_FLOAT -> String.valueOf(n.floatValue());
				case T_DOUBLE -> String.valueOf(n.doubleValue());
				default -> String.valueOf(n.longValue());
			};
		}
		return "";
	}

	private static long parseLong(String s) {
		try {
			return Long.parseLong(s.trim());
		} catch (Exception e) {
			return 0L;
		}
	}

	private static double parseDouble(String s) {
		try {
			return Double.parseDouble(s.trim());
		} catch (Exception e) {
			return 0.0;
		}
	}

	private static String typeName(int id) {
		return switch (id) {
			case T_BYTE -> "Byte";
			case T_SHORT -> "Short";
			case T_INT -> "Int";
			case T_LONG -> "Long";
			case T_FLOAT -> "Float";
			case T_DOUBLE -> "Double";
			case 7 -> "Byte Array";
			case T_STRING -> "String";
			case T_LIST -> "List";
			case T_COMPOUND -> "Compound";
			case 11 -> "Int Array";
			case 12 -> "Long Array";
			default -> "Unknown";
		};
	}

	private static String typeBadgeName(int id) {
		return switch (id) {
			case T_BYTE -> "BYT";
			case T_SHORT -> "SHT";
			case T_INT -> "INT";
			case T_LONG -> "LNG";
			case T_FLOAT -> "FLT";
			case T_DOUBLE -> "DBL";
			case 7 -> "B[]";
			case T_STRING -> "STR";
			case T_LIST -> "LST";
			case T_COMPOUND -> "COM";
			case 11 -> "I[]";
			case 12 -> "L[]";
			default -> "???";
		};
	}

	private static int typeBadgeColor(int id) {
		return switch (id) {
			case T_BYTE -> 0xFFBA68C8;     // Purple
			case T_SHORT -> 0xFFA1887F;    // Brown
			case T_INT -> 0xFF81C784;      // Green
			case T_LONG -> 0xFFE57373;     // Red
			case T_FLOAT -> 0xFFFFD54F;    // Yellow
			case T_DOUBLE -> 0xFFFFB74D;   // Amber
			case T_STRING -> 0xFF4FC3F7;   // Light Blue
			case T_LIST -> COLOR_ORANGE_DARK;
			case T_COMPOUND -> COLOR_ORANGE_PRIMARY;
			default -> 0xFFAAAAAA;
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
