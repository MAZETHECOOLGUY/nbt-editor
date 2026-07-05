package com.example.client.ui;

import com.example.NbtEditor;
import com.example.network.EditItemPayload;
import com.example.util.ItemNbt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Structured NBT tree editor for a whole item ({@code {id, count, components}}).
 * Single-click selects a node; double-click expands/collapses a container.
 * Edits are applied live as you type — there is no Apply button; the right-hand
 * vertical toolbar drives presets and tree tools, and Save sends the tree to the
 * server, which decodes it authoritatively.
 */
public class NbtEditorScreen extends Screen {
	// NBT type ids.
	private static final int T_BYTE = 1, T_SHORT = 2, T_INT = 3, T_LONG = 4,
			T_FLOAT = 5, T_DOUBLE = 6, T_STRING = 8, T_LIST = 9, T_COMPOUND = 10;
	private static final int[] TYPE_CYCLE = {T_BYTE, T_SHORT, T_INT, T_LONG, T_FLOAT, T_DOUBLE, T_STRING, T_LIST, T_COMPOUND};

	private static final int ROW_HEIGHT = 12;
	private static final int INDENT = 12;
	private static final int LIST_TOP = 38;
	private static final int MARGIN = 20;
	private static final int SIDEBAR_W = 100;
	private static final int BTN_H = 16;

	// Rename toolbar geometry (format toggles + colour swatches).
	private static final int FMT_SIZE = 18, FMT_GAP = 4;
	private static final int SWATCH = 16, SWATCH_GAP = 4;
	private static final String[] FMT_LETTERS = {"B", "I", "U", "S", "K"};
	private static final ChatFormatting[] NAME_COLORS = {
			ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
			ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
			ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE};

	/** Quick-edit preset that repurposes the bottom panel (and, for Enchant/Attributes, the main area). */
	private enum Preset { NONE, RENAME, COUNT, ENCHANT, ATTRIBUTES }

	private final boolean playerInventory;
	private final int syncId;
	private final int slotIndex;
	private final CompoundTag root;
	private final RegistryAccess registries;

	private final Set<Tag> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
	private final List<Row> rows = new ArrayList<>();
	private int scroll;

	// Selection is identified by (parent, key | index) so it survives tag replacement.
	private Tag selParent;
	private String selKey;   // non-null when parent is a CompoundTag
	private int selIndex = -1; // used when parent is a ListTag

	private Preset preset = Preset.NONE;
	private String presetStatus = "";

	// Rename toolbar state.
	private boolean fmtBold, fmtItalic, fmtUnderline, fmtStrike, fmtObfuscated;
	private ChatFormatting nameColor; // null = default (no explicit colour)

	// Enchant picker state.
	private List<Identifier> allEnchants = List.of();
	private String enchSelectedId; // full id string of the enchant being edited, or null

	// Attribute picker state.
	private List<Identifier> allAttributes = List.of();
	private String attrSelectedId; // full id string of the attribute being edited, or null
	private int attrOpIndex;   // index into Presets.OPERATIONS
	private int attrSlotIndex; // index into Presets.SLOT_GROUPS

	private EditBox keyBox;
	private EditBox valueBox;
	private boolean suppressResponder;

	private Button renameBtn;
	private Button countBtn;
	private Button unbreakBtn;
	private Button enchantBtn;
	private Button attributesBtn;
	private Button addBtn;
	private Button deleteBtn;
	private Button typeBtn;

	private NbtEditorScreen(boolean playerInventory, int syncId, int slotIndex, CompoundTag root,
			RegistryAccess registries) {
		super(Component.literal("NBT Editor"));
		this.playerInventory = playerInventory;
		this.syncId = syncId;
		this.slotIndex = slotIndex;
		this.root = root;
		this.registries = registries;
		this.expanded.add(root);
	}

	public static NbtEditorScreen open(boolean playerInventory, int syncId, int slotIndex, ItemStack stack,
			RegistryAccess registries) {
		CompoundTag tag = ItemNbt.encode(stack, registries);
		if (tag == null) {
			NbtEditor.LOGGER.warn("Failed to encode item to NBT; opening empty editor");
			tag = new CompoundTag();
		}
		return new NbtEditorScreen(playerInventory, syncId, slotIndex, tag, registries);
	}

	// ---- layout helpers ----
	private int sidebarX() { return this.width - MARGIN - SIDEBAR_W; }
	private int centralRight() { return sidebarX() - 8; }
	private int treeBottom() { return this.height - 46; }
	private int breadcrumbY() { return this.height - 40; }
	private int boxesY() { return this.height - 26; }

	@Override
	protected void init() {
		allEnchants = Presets.allEnchantmentIds(registries);
		allAttributes = Presets.allAttributeIds(registries);

		// Right-hand vertical toolbar: presets, then tree tools, then Save/Cancel.
		int bx = sidebarX();
		int by = LIST_TOP;
		renameBtn = addRenderableWidget(tab("Rename", () -> beginPreset(Preset.RENAME), bx, by));
		by += BTN_H + 3;
		countBtn = addRenderableWidget(tab("Count", () -> beginPreset(Preset.COUNT), bx, by));
		by += BTN_H + 3;
		unbreakBtn = addRenderableWidget(tab("Unbreakable", this::toggleUnbreakable, bx, by));
		by += BTN_H + 3;
		enchantBtn = addRenderableWidget(tab("Enchant", () -> beginPreset(Preset.ENCHANT), bx, by));
		by += BTN_H + 3;
		attributesBtn = addRenderableWidget(tab("Attributes", () -> beginPreset(Preset.ATTRIBUTES), bx, by));
		by += BTN_H + 10;
		addBtn = addRenderableWidget(tab("Add", this::addChild, bx, by));
		by += BTN_H + 3;
		deleteBtn = addRenderableWidget(tab("Delete", this::deleteSelected, bx, by));
		by += BTN_H + 3;
		typeBtn = addRenderableWidget(tab("Type >", this::cycleType, bx, by));
		by += BTN_H + 10;
		addRenderableWidget(tab("Save", this::save, bx, by));
		by += BTN_H + 3;
		addRenderableWidget(tab("Cancel", this::onClose, bx, by));

		keyBox = new CommitEditBox(MARGIN, boxesY(), 150, 16);
		keyBox.setMaxLength(Short.MAX_VALUE);
		keyBox.setHint(Component.literal("key"));
		addRenderableWidget(keyBox);

		valueBox = new EditBox(this.font, MARGIN + 158, boxesY(), centralRight() - (MARGIN + 158), 16,
				Component.literal("value"));
		valueBox.setMaxLength(Short.MAX_VALUE);
		valueBox.setHint(Component.literal("value"));
		valueBox.setResponder(this::onValueChanged);
		addRenderableWidget(valueBox);

		syncPanel();
	}

	private Button tab(String label, Runnable action, int x, int y) {
		return Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, SIDEBAR_W, BTN_H).build();
	}

	// ---- selection ----
	private boolean hasSelection() {
		return selParent != null && selectedTag() != null;
	}

	private Tag selectedTag() {
		if (selParent instanceof CompoundTag c && selKey != null) {
			return c.get(selKey);
		}
		if (selParent instanceof ListTag l && selIndex >= 0 && selIndex < l.size()) {
			return l.get(selIndex);
		}
		return null;
	}

	private void select(Tag parent, String key, int index) {
		commitKeyRename();
		preset = Preset.NONE;
		presetStatus = "";
		selParent = parent;
		selKey = key;
		selIndex = index;
		syncPanel();
	}

	private void clearSelection() {
		selParent = null;
		selKey = null;
		selIndex = -1;
		syncPanel();
	}

	private void replaceSelected(Tag newTag) {
		if (selParent instanceof CompoundTag c && selKey != null) {
			c.put(selKey, newTag);
		} else if (selParent instanceof ListTag l && selIndex >= 0) {
			if (!l.setTag(selIndex, newTag)) {
				// List requires homogeneous element types; refuse mismatched replacement.
				NbtEditor.LOGGER.warn("List rejected element of a different type");
			}
		}
	}

	/** Refresh the bottom-panel widgets to reflect the current selection (or active preset). */
	private void syncPanel() {
		updateToolLabels();
		if (preset == Preset.ATTRIBUTES) {
			keyBox.visible = keyBox.active = true;   // filter
			valueBox.visible = valueBox.active = true; // amount
			// In attribute mode the tree-tool buttons drive slot / operation / remove.
			addBtn.active = true;
			typeBtn.active = true;
			deleteBtn.active = attrSelectedId != null;
			return;
		}
		if (preset == Preset.ENCHANT) {
			keyBox.visible = keyBox.active = true;   // filter
			valueBox.visible = valueBox.active = true; // level
			addBtn.active = deleteBtn.active = typeBtn.active = false;
			return;
		}
		if (preset == Preset.RENAME || preset == Preset.COUNT) {
			keyBox.visible = keyBox.active = false;
			valueBox.visible = valueBox.active = true;
			addBtn.active = deleteBtn.active = typeBtn.active = false;
			return;
		}

		Tag t = selectedTag();
		boolean sel = t != null;

		boolean canRenameKey = sel && selKey != null;
		keyBox.visible = canRenameKey;
		keyBox.active = canRenameKey;
		setBox(keyBox, canRenameKey ? selKey : "");
		if (!canRenameKey) {
			keyBox.setFocused(false);
		}

		boolean isScalar = t instanceof NumericTag || t instanceof StringTag;
		valueBox.visible = isScalar;
		valueBox.active = isScalar;
		setBox(valueBox, isScalar ? leafText(t) : "");
		if (!isScalar) {
			valueBox.setFocused(false);
		}

		deleteBtn.active = sel;
		typeBtn.active = sel;
		addBtn.active = true;
	}

	/**
	 * In attribute mode the tree-tool buttons are repurposed to drive the modifier's slot,
	 * operation and removal; every other mode shows their normal tree labels.
	 */
	private void updateToolLabels() {
		if (preset == Preset.ATTRIBUTES) {
			addBtn.setMessage(Component.literal("Slot: " + Presets.slotLabel(Presets.SLOT_GROUPS.get(attrSlotIndex))));
			typeBtn.setMessage(Component.literal("Op: " + Presets.operationLabel(Presets.OPERATIONS.get(attrOpIndex))));
			deleteBtn.setMessage(Component.literal("Remove"));
		} else {
			addBtn.setMessage(Component.literal("Add"));
			deleteBtn.setMessage(Component.literal("Delete"));
			typeBtn.setMessage(Component.literal("Type >"));
		}
	}

	/** Set an edit box value without triggering its live responder. */
	private void setBox(EditBox box, String value) {
		suppressResponder = true;
		box.setValue(value);
		suppressResponder = false;
	}

	// ---- live editing ----
	private void onValueChanged(String text) {
		if (suppressResponder) {
			return;
		}
		switch (preset) {
			case RENAME -> applyRename();
			case COUNT -> {
				Presets.setCount(root, (int) parseLong(text));
				presetStatus = "Count set to " + Presets.currentCount(root) + ".";
			}
			case ENCHANT -> applyEnchantLevel();
			case ATTRIBUTES -> applyAttributeAmount();
			case NONE -> {
				Tag t = selectedTag();
				if (t instanceof NumericTag || t instanceof StringTag) {
					replaceSelected(createTag(t.getId(), text));
				}
			}
		}
	}

	/** Commit a pending key rename (called when the key box loses focus or before Save). */
	private void commitKeyRename() {
		if (preset != Preset.NONE || keyBox == null || !keyBox.visible) {
			return;
		}
		Tag t = selectedTag();
		if (t == null || selKey == null) {
			return;
		}
		String newKey = keyBox.getValue().trim();
		if (newKey.isEmpty() || newKey.equals(selKey)) {
			return;
		}
		if (selParent instanceof CompoundTag c && !c.contains(newKey)) {
			c.remove(selKey);
			c.put(newKey, t);
			selKey = newKey;
		}
	}

	// ---- presets ----
	private void beginPreset(Preset p) {
		commitKeyRename();
		selParent = null;
		selKey = null;
		selIndex = -1;
		preset = p;
		presetStatus = "";
		enchSelectedId = null;
		attrSelectedId = null;
		scroll = 0;
		setBox(keyBox, "");
		setBox(valueBox, switch (p) {
			case RENAME -> Presets.currentName(root, registries);
			case COUNT -> Integer.toString(Presets.currentCount(root));
			default -> "";
		});
		keyBox.setHint(Component.literal(switch (p) {
			case ENCHANT -> "filter enchantments";
			case ATTRIBUTES -> "filter attributes";
			default -> "key";
		}));
		valueBox.setHint(Component.literal(switch (p) {
			case RENAME -> "name (& colour codes)";
			case COUNT -> "count";
			case ENCHANT -> "level";
			case ATTRIBUTES -> "amount";
			default -> "value";
		}));
		if (p == Preset.ENCHANT || p == Preset.ATTRIBUTES) {
			setBox(valueBox, "1");
			// Route focus through the Screen (not just the widget) so keystrokes reach the filter.
			setFocused(keyBox);
		}
		if (p == Preset.RENAME) {
			Style st = Presets.currentNameStyle(root, registries);
			fmtBold = st.isBold();
			fmtItalic = st.isItalic();
			fmtUnderline = st.isUnderlined();
			fmtStrike = st.isStrikethrough();
			fmtObfuscated = st.isObfuscated();
			nameColor = matchColor(st.getColor());
			setFocused(valueBox);
		}
		syncPanel();
	}

	private void toggleUnbreakable() {
		Presets.toggleUnbreakable(root, registries);
		presetStatus = Presets.hasComponent(root, DataComponents.UNBREAKABLE) ? "Unbreakable on." : "Unbreakable off.";
		expandComponents();
	}

	// ---- rename toolbar ----
	/** Rebuild the custom name from the plain text plus the toolbar's format toggles and colour. */
	private void applyRename() {
		String text = valueBox.getValue();
		Presets.renameStyled(root, registries, text,
				fmtBold, fmtItalic, fmtUnderline, fmtStrike, fmtObfuscated, nameColor);
		expandComponents();
		presetStatus = text.isEmpty() ? "Custom name cleared." : "Renamed.";
	}

	/** Toggle format button i (B/I/U/S/K) and re-apply live. */
	private void toggleFormat(int i) {
		switch (i) {
			case 0 -> fmtBold = !fmtBold;
			case 1 -> fmtItalic = !fmtItalic;
			case 2 -> fmtUnderline = !fmtUnderline;
			case 3 -> fmtStrike = !fmtStrike;
			case 4 -> fmtObfuscated = !fmtObfuscated;
			default -> { return; }
		}
		applyRename();
		setFocused(valueBox);
	}

	/** Map a parsed name colour back to a swatch (null if none / not a vanilla colour). */
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

	private int fmtRowY() { return LIST_TOP + 12; }
	private int paletteTop() { return fmtRowY() + FMT_SIZE + 16; }
	private int paletteCols() { return Math.max(1, (centralRight() - MARGIN + SWATCH_GAP) / (SWATCH + SWATCH_GAP)); }

	/** Hit-test the rename toolbar; returns true if a toggle or swatch was clicked. */
	private boolean handleRenamePaletteClick(int mx, int my) {
		int fy = fmtRowY();
		if (my >= fy && my < fy + FMT_SIZE) {
			for (int i = 0; i < FMT_LETTERS.length; i++) {
				int x = MARGIN + i * (FMT_SIZE + FMT_GAP);
				if (mx >= x && mx < x + FMT_SIZE) {
					toggleFormat(i);
					return true;
				}
			}
		}
		int gridTop = paletteTop();
		int cols = paletteCols();
		if (mx >= MARGIN && my >= gridTop) {
			int col = (mx - MARGIN) / (SWATCH + SWATCH_GAP);
			int rowNo = (my - gridTop) / (SWATCH + SWATCH_GAP);
			int cellX = MARGIN + col * (SWATCH + SWATCH_GAP);
			int cellY = gridTop + rowNo * (SWATCH + SWATCH_GAP);
			int idx = rowNo * cols + col;
			if (col < cols && mx < cellX + SWATCH && my < cellY + SWATCH && idx >= 0 && idx <= NAME_COLORS.length) {
				nameColor = idx == 0 ? null : NAME_COLORS[idx - 1];
				applyRename();
				setFocused(valueBox);
				return true;
			}
		}
		return false;
	}

	/** Apply the level box to the currently selected enchantment (0 removes it). */
	private void applyEnchantLevel() {
		if (enchSelectedId == null) {
			return;
		}
		int level = (int) parseLong(valueBox.getValue());
		if (Presets.setEnchantment(root, registries, enchSelectedId, level)) {
			presetStatus = level <= 0 ? "Removed " + enchSelectedId + "." : "Set " + enchSelectedId + " " + level + ".";
		}
		expandComponents();
	}

	// ---- attribute preset ----
	/** Left-click an attribute row: load an existing modifier for editing, or add a new one. */
	private void selectAttribute(String idStr) {
		attrSelectedId = idStr;
		Presets.currentAttribute(root, registries, idStr).ifPresentOrElse(state -> {
			// Already on the item — load its amount / operation / slot for editing.
			setBox(valueBox, Presets.formatAmount(state.amount()).replace("+", ""));
			attrOpIndex = Math.max(0, Presets.OPERATIONS.indexOf(state.operation()));
			attrSlotIndex = Math.max(0, Presets.SLOT_GROUPS.indexOf(state.slot()));
			presetStatus = "Editing " + idStr + ".";
		}, () -> {
			double amount = parseDouble(valueBox.getValue());
			if (Presets.setAttribute(root, registries, idStr, amount,
					Presets.OPERATIONS.get(attrOpIndex), Presets.SLOT_GROUPS.get(attrSlotIndex))) {
				presetStatus = "Set " + idStr + " " + Presets.formatAmount(amount) + ".";
			}
		});
		expandComponents();
		syncPanel();
		setFocused(valueBox);
	}

	/** Apply the amount box to the selected attribute, re-using the current operation / slot. */
	private void applyAttributeAmount() {
		if (attrSelectedId == null) {
			return;
		}
		double amount = parseDouble(valueBox.getValue());
		if (Presets.setAttribute(root, registries, attrSelectedId, amount,
				Presets.OPERATIONS.get(attrOpIndex), Presets.SLOT_GROUPS.get(attrSlotIndex))) {
			presetStatus = "Set " + attrSelectedId + " " + Presets.formatAmount(amount) + ".";
		}
		expandComponents();
	}

	/** Right-click / Remove button: drop every modifier for the given attribute. */
	private void removeAttribute(String idStr) {
		if (Presets.removeAttribute(root, registries, idStr)) {
			presetStatus = "Removed " + idStr + ".";
			if (idStr.equals(attrSelectedId)) {
				attrSelectedId = null;
			}
			expandComponents();
			syncPanel();
		}
	}

	private void cycleAttrOperation() {
		attrOpIndex = (attrOpIndex + 1) % Presets.OPERATIONS.size();
		updateToolLabels();
		if (attrSelectedId != null) {
			applyAttributeAmount();
		}
	}

	private void cycleAttrSlot() {
		attrSlotIndex = (attrSlotIndex + 1) % Presets.SLOT_GROUPS.size();
		updateToolLabels();
		if (attrSelectedId != null) {
			applyAttributeAmount();
		}
	}

	private List<Identifier> filteredAttributes() {
		return filterIds(allAttributes);
	}

	private List<Identifier> filteredEnchants() {
		return filterIds(allEnchants);
	}

	/** Substring-filter an id list by the current key box (used by both picker lists). */
	private List<Identifier> filterIds(List<Identifier> all) {
		String filter = keyBox.getValue().trim().toLowerCase(Locale.ROOT);
		if (filter.isEmpty()) {
			return all;
		}
		List<Identifier> out = new ArrayList<>();
		for (Identifier id : all) {
			if (id.toString().toLowerCase(Locale.ROOT).contains(filter)) {
				out.add(id);
			}
		}
		return out;
	}

	/** Expand root + its components compound so a preset's effect is visible in the tree. */
	private void expandComponents() {
		expanded.add(root);
		if (root.get("components") instanceof CompoundTag components) {
			expanded.add(components);
		}
	}

	// ---- tree actions ----
	private void addChild() {
		if (preset == Preset.ATTRIBUTES) {
			cycleAttrSlot();
			return;
		}
		Tag target;
		if (hasSelection() && selectedTag() instanceof CompoundTag) {
			target = selectedTag();
		} else if (hasSelection() && selectedTag() instanceof ListTag) {
			target = selectedTag();
		} else if (hasSelection()) {
			target = selParent;
		} else {
			target = root;
		}

		Tag newTag = StringTag.valueOf("");
		if (target instanceof CompoundTag c) {
			String key = uniqueKey(c);
			c.put(key, newTag);
			expanded.add(c);
			select(c, key, -1);
		} else if (target instanceof ListTag l) {
			l.add(l.size(), newTag);
			expanded.add(l);
			select(l, null, l.size() - 1);
		}
	}

	private void deleteSelected() {
		if (preset == Preset.ATTRIBUTES) {
			if (attrSelectedId != null) {
				removeAttribute(attrSelectedId);
			}
			return;
		}
		if (!hasSelection()) {
			return;
		}
		if (selParent instanceof CompoundTag c && selKey != null) {
			c.remove(selKey);
		} else if (selParent instanceof ListTag l && selIndex >= 0) {
			l.remove(selIndex);
		}
		clearSelection();
	}

	private void cycleType() {
		if (preset == Preset.ATTRIBUTES) {
			cycleAttrOperation();
			return;
		}
		Tag t = selectedTag();
		if (t == null) {
			return;
		}
		int cur = t.getId();
		int i = 0;
		for (int j = 0; j < TYPE_CYCLE.length; j++) {
			if (TYPE_CYCLE[j] == cur) {
				i = j;
				break;
			}
		}
		int next = TYPE_CYCLE[(i + 1) % TYPE_CYCLE.length];
		String text = (t instanceof NumericTag || t instanceof StringTag) ? leafText(t) : "";
		replaceSelected(createTag(next, text));
		syncPanel();
	}

	private void save() {
		commitKeyRename();
		ClientPlayNetworking.send(new EditItemPayload(playerInventory, syncId, slotIndex, root));
		onClose();
	}

	// ---- tag helpers ----
	private static String uniqueKey(CompoundTag c) {
		if (!c.contains("new")) {
			return "new";
		}
		for (int i = 1; ; i++) {
			String k = "new" + i;
			if (!c.contains(k)) {
				return k;
			}
		}
	}

	private static long parseLong(String s) {
		try {
			return Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			try {
				return (long) Double.parseDouble(s.trim());
			} catch (NumberFormatException e2) {
				return 0L;
			}
		}
	}

	private static double parseDouble(String s) {
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return 0.0;
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

	private static String typeName(int id) {
		return switch (id) {
			case T_BYTE -> "byte";
			case T_SHORT -> "short";
			case T_INT -> "int";
			case T_LONG -> "long";
			case T_FLOAT -> "float";
			case T_DOUBLE -> "double";
			case 7 -> "byte[]";
			case T_STRING -> "string";
			case T_LIST -> "list";
			case T_COMPOUND -> "compound";
			case 11 -> "int[]";
			case 12 -> "long[]";
			default -> "?";
		};
	}

	// ---- tree building ----
	private void rebuildRows() {
		rows.clear();
		appendChildren(root, 0);
	}

	private void appendChildren(Tag tag, int depth) {
		if (tag instanceof CompoundTag compound) {
			List<String> keys = new ArrayList<>(compound.keySet());
			Collections.sort(keys);
			for (String key : keys) {
				addRow(depth, compound.get(key), compound, key, -1);
			}
		} else if (tag instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++) {
				addRow(depth, list.get(i), list, null, i);
			}
		}
	}

	private void addRow(int depth, Tag tag, Tag parent, String key, int index) {
		boolean container = tag instanceof CompoundTag || tag instanceof ListTag;
		rows.add(new Row(depth, tag, container, parent, key, index));
		if (container && expanded.contains(tag)) {
			appendChildren(tag, depth + 1);
		}
	}

	private boolean isSelectedRow(Row row) {
		if (row.parent() != selParent) {
			return false;
		}
		return selKey != null ? selKey.equals(row.key()) : row.index() == selIndex;
	}

	private String describe(Row row) {
		String label = row.key() != null ? row.key() : "[" + row.index() + "]";
		Tag t = row.tag();
		if (row.container()) {
			int size = (t instanceof CompoundTag c) ? c.size() : ((ListTag) t).size();
			String bracket = (t instanceof CompoundTag) ? "{" + size + "}" : "[" + size + "]";
			String arrow = expanded.contains(t) ? "▾ " : "▸ ";
			return arrow + label + ": " + bracket;
		}
		String value = leafText(t);
		if (!(t instanceof StringTag) && !(t instanceof NumericTag)) {
			value = t.toString();
		}
		if (value.length() > 70) {
			value = value.substring(0, 67) + "...";
		}
		return "  " + label + " (" + typeName(t.getId()) + "): " + value;
	}

	// ---- rendering ----
	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		g.centeredText(this.font, "edits apply live — press Save   ·   slot " + slotIndex,
				this.width / 2, 24, 0xFFAAAAAA);

		drawActiveTabIndicator(g);

		if (preset == Preset.ENCHANT) {
			renderEnchantList(g);
		} else if (preset == Preset.ATTRIBUTES) {
			renderAttributeList(g);
		} else if (preset == Preset.RENAME) {
			renderRenamePalette(g);
		} else {
			renderTree(g);
		}

		String crumb = breadcrumb();
		g.text(this.font, crumb, MARGIN, breadcrumbY(), 0xFFFFFFAA);
	}

	private void drawActiveTabIndicator(GuiGraphicsExtractor g) {
		Button active = switch (preset) {
			case RENAME -> renameBtn;
			case COUNT -> countBtn;
			case ENCHANT -> enchantBtn;
			case ATTRIBUTES -> attributesBtn;
			default -> null;
		};
		if (active != null) {
			g.fill(active.getX() - 4, active.getY(), active.getX() - 1, active.getY() + active.getHeight(), 0xFF40FF40);
		}
		if (Presets.hasComponent(root, DataComponents.UNBREAKABLE)) {
			g.fill(unbreakBtn.getX() - 4, unbreakBtn.getY(), unbreakBtn.getX() - 1,
					unbreakBtn.getY() + unbreakBtn.getHeight(), 0xFFFFC040);
		}
	}

	private void renderTree(GuiGraphicsExtractor g) {
		rebuildRows();
		int left = MARGIN;
		int top = LIST_TOP;
		int bottom = treeBottom();
		int right = centralRight();

		g.fill(left - 4, top - 4, right + 4, bottom + 4, 0x66000000);
		g.enableScissor(left - 4, top, right + 4, bottom);
		int y = top - scroll;
		for (Row row : rows) {
			if (y + ROW_HEIGHT >= top && y <= bottom) {
				if (isSelectedRow(row)) {
					g.fill(left - 4, y - 1, right + 4, y + ROW_HEIGHT - 1, 0x804080FF);
				}
				int x = left + row.depth() * INDENT;
				int color = row.container() ? 0xFF7FD6FF : 0xFFE0E0E0;
				g.text(this.font, describe(row), x, y, color);
			}
			y += ROW_HEIGHT;
		}
		g.disableScissor();
	}

	private void renderEnchantList(GuiGraphicsExtractor g) {
		int left = MARGIN;
		int top = LIST_TOP;
		int bottom = treeBottom();
		int right = centralRight();
		List<Identifier> filtered = filteredEnchants();
		Map<String, Integer> levels = Presets.currentEnchantments(root, registries);

		g.fill(left - 4, top - 4, right + 4, bottom + 4, 0x66000000);
		g.enableScissor(left - 4, top, right + 4, bottom);
		int y = top - scroll;
		for (Identifier id : filtered) {
			if (y + ROW_HEIGHT >= top && y <= bottom) {
				String idStr = id.toString();
				boolean sel = idStr.equals(enchSelectedId);
				if (sel) {
					g.fill(left - 4, y - 1, right + 4, y + ROW_HEIGHT - 1, 0x804080FF);
				}
				Integer lvl = levels.get(idStr);
				String label = idStr + (lvl != null ? "   (" + lvl + ")" : "");
				int color = lvl != null ? 0xFF7FFF9F : 0xFFE0E0E0;
				g.text(this.font, label, left, y, color);
			}
			y += ROW_HEIGHT;
		}
		g.disableScissor();
	}

	private void renderAttributeList(GuiGraphicsExtractor g) {
		int left = MARGIN;
		int top = LIST_TOP;
		int bottom = treeBottom();
		int right = centralRight();
		List<Identifier> filtered = filteredAttributes();
		Map<String, String> current = Presets.currentAttributes(root, registries);

		g.fill(left - 4, top - 4, right + 4, bottom + 4, 0x66000000);
		g.enableScissor(left - 4, top, right + 4, bottom);
		int y = top - scroll;
		for (Identifier id : filtered) {
			if (y + ROW_HEIGHT >= top && y <= bottom) {
				String idStr = id.toString();
				boolean sel = idStr.equals(attrSelectedId);
				if (sel) {
					g.fill(left - 4, y - 1, right + 4, y + ROW_HEIGHT - 1, 0x804080FF);
				}
				String summary = current.get(idStr);
				String label = idStr + (summary != null ? "   " + summary : "");
				int color = summary != null ? 0xFF7FFF9F : 0xFFE0E0E0;
				g.text(this.font, label, left, y, color);
			}
			y += ROW_HEIGHT;
		}
		g.disableScissor();
	}

	private void renderRenamePalette(GuiGraphicsExtractor g) {
		int left = MARGIN;
		int top = LIST_TOP;
		int right = centralRight();
		int bottom = treeBottom();
		g.fill(left - 4, top - 4, right + 4, bottom + 4, 0x66000000);

		// Format toggles: B / I / U / S / K, highlighted green when active.
		g.text(this.font, "Format", left, top, 0xFFAAAAAA);
		boolean[] states = {fmtBold, fmtItalic, fmtUnderline, fmtStrike, fmtObfuscated};
		int fy = fmtRowY();
		for (int i = 0; i < FMT_LETTERS.length; i++) {
			int x = left + i * (FMT_SIZE + FMT_GAP);
			g.fill(x, fy, x + FMT_SIZE, fy + FMT_SIZE, states[i] ? 0xFF2E7D32 : 0xFF3A3A3A);
			g.centeredText(this.font, styledLetter(i), x + FMT_SIZE / 2, fy + (FMT_SIZE - 8) / 2, 0xFFFFFFFF);
		}

		// Colour swatches: a "default" (×) cell first, then the vanilla palette.
		int gridTop = paletteTop();
		g.text(this.font, "Colour", left, gridTop - 10, 0xFFAAAAAA);
		int cols = paletteCols();
		int count = 1 + NAME_COLORS.length;
		for (int idx = 0; idx < count; idx++) {
			int cx = left + (idx % cols) * (SWATCH + SWATCH_GAP);
			int cy = gridTop + (idx / cols) * (SWATCH + SWATCH_GAP);
			boolean selected;
			if (idx == 0) {
				g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0xFF202020);
				g.centeredText(this.font, "×", cx + SWATCH / 2, cy + (SWATCH - 8) / 2, 0xFFCCCCCC);
				selected = nameColor == null;
			} else {
				ChatFormatting cf = NAME_COLORS[idx - 1];
				Integer rgb = cf.getColor();
				g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0xFF000000 | (rgb == null ? 0 : rgb));
				selected = cf == nameColor;
			}
			if (selected) {
				drawSwatchBorder(g, cx, cy);
			}
		}
	}

	/** A 1px white border around a selected swatch. */
	private void drawSwatchBorder(GuiGraphicsExtractor g, int x, int y) {
		int c = 0xFFFFFFFF;
		g.fill(x - 1, y - 1, x + SWATCH + 1, y, c);
		g.fill(x - 1, y + SWATCH, x + SWATCH + 1, y + SWATCH + 1, c);
		g.fill(x - 1, y, x, y + SWATCH, c);
		g.fill(x + SWATCH, y, x + SWATCH + 1, y + SWATCH, c);
	}

	/** The toggle letter, drawn in its own format so it previews the effect. */
	private Component styledLetter(int i) {
		MutableComponent c = Component.literal(FMT_LETTERS[i]);
		return switch (i) {
			case 0 -> c.withStyle(ChatFormatting.BOLD);
			case 1 -> c.withStyle(ChatFormatting.ITALIC);
			case 2 -> c.withStyle(ChatFormatting.UNDERLINE);
			case 3 -> c.withStyle(ChatFormatting.STRIKETHROUGH);
			default -> c;
		};
	}

	private String breadcrumb() {
		if (preset != Preset.NONE) {
			String prompt = switch (preset) {
				case RENAME -> "Rename: type the name below; click B/I/U/S/K to toggle formats and a swatch to set colour (× = default; empty clears).";
				case COUNT -> "Set count: number ≥ 1 in the value box (e.g. 1 / 16 / 64).";
				case ENCHANT -> "Enchant: left-click adds/edits, right-click removes; filter in key box, level in value box.";
				case ATTRIBUTES -> "Attribute: left-click adds/edits, right-click removes; amount in value box; Slot/Op buttons set slot & operation.";
				default -> "";
			};
			return presetStatus.isEmpty() ? prompt : prompt + "   —   " + presetStatus;
		}
		Tag selTag = selectedTag();
		return selTag == null
				? "No selection — click a node. Add creates into the selected container (or root)."
				: "Selected: " + (selKey != null ? selKey : "[" + selIndex + "]") + "  (" + typeName(selTag.getId()) + ")";
	}

	// ---- input ----
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		boolean inCentral = event.x() >= MARGIN - 4 && event.x() <= centralRight() + 4
				&& event.y() >= LIST_TOP && event.y() <= treeBottom();
		if (!inCentral) {
			return false;
		}
		int idx = (int) ((event.y() - (LIST_TOP - scroll)) / ROW_HEIGHT);

		if (preset == Preset.ENCHANT) {
			List<Identifier> filtered = filteredEnchants();
			if (idx >= 0 && idx < filtered.size()) {
				String idStr = filtered.get(idx).toString();
				if (event.button() == 1) {
					removeEnchant(idStr);      // right-click disables an active enchantment
				} else if (event.button() == 0) {
					selectEnchant(idStr);       // left-click adds / edits
				}
				return true;
			}
			return false;
		}

		if (preset == Preset.ATTRIBUTES) {
			List<Identifier> filtered = filteredAttributes();
			if (idx >= 0 && idx < filtered.size()) {
				String idStr = filtered.get(idx).toString();
				if (event.button() == 1) {
					removeAttribute(idStr);    // right-click removes the attribute's modifiers
				} else if (event.button() == 0) {
					selectAttribute(idStr);     // left-click adds / edits
				}
				return true;
			}
			return false;
		}

		if (preset == Preset.RENAME) {
			return event.button() == 0 && handleRenamePaletteClick((int) event.x(), (int) event.y());
		}

		if (event.button() == 0 && idx >= 0 && idx < rows.size()) {
			Row row = rows.get(idx);
			if (doubleClick && row.container()) {
				if (expanded.contains(row.tag())) {
					expanded.remove(row.tag());
				} else {
					expanded.add(row.tag());
				}
			} else {
				select(row.parent(), row.key(), row.index());
			}
			return true;
		}
		return false;
	}

	private void selectEnchant(String idStr) {
		enchSelectedId = idStr;
		int existing = Presets.currentEnchantments(root, registries).getOrDefault(idStr, 0);
		if (existing > 0) {
			// Already on the item — load its level for editing instead of overwriting it.
			setBox(valueBox, Integer.toString(existing));
			presetStatus = "Editing " + idStr + " (" + existing + ").";
		} else {
			int level = (int) parseLong(valueBox.getValue());
			if (level <= 0) {
				level = 1;
				setBox(valueBox, "1");
			}
			if (Presets.setEnchantment(root, registries, idStr, level)) {
				presetStatus = "Set " + idStr + " " + level + ".";
			}
		}
		expandComponents();
		setFocused(valueBox);
	}

	/** Right-click handler: disable an enchantment that's currently on the item (no-op otherwise). */
	private void removeEnchant(String idStr) {
		if (!Presets.currentEnchantments(root, registries).containsKey(idStr)) {
			return;
		}
		Presets.setEnchantment(root, registries, idStr, 0);
		presetStatus = "Removed " + idStr + ".";
		if (idStr.equals(enchSelectedId)) {
			enchSelectedId = null;
		}
		expandComponents();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int contentRows = switch (preset) {
			case ENCHANT -> filteredEnchants().size();
			case ATTRIBUTES -> filteredAttributes().size();
			default -> rows.size();
		};
		int contentHeight = contentRows * ROW_HEIGHT;
		int viewHeight = treeBottom() - LIST_TOP;
		int maxScroll = Math.max(0, contentHeight - viewHeight);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * ROW_HEIGHT * 2)));
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record Row(int depth, Tag tag, boolean container, Tag parent, String key, int index) {}

	/** Key box that commits a pending rename when it loses focus (e.g. clicking a toolbar button). */
	private class CommitEditBox extends EditBox {
		CommitEditBox(int x, int y, int w, int h) {
			super(NbtEditorScreen.this.font, x, y, w, h, Component.literal("key"));
		}

		@Override
		public void setFocused(boolean focused) {
			boolean wasFocused = isFocused();
			super.setFocused(focused);
			if (wasFocused && !focused) {
				commitKeyRename();
			}
		}
	}
}
